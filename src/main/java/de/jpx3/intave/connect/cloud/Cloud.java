package de.jpx3.intave.connect.cloud;

import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.player.trust.DefaultForwardingPermissionTrustFactorResolver;
import de.jpx3.intave.access.player.trust.SelectiveTrustfactorResolver;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.Shard;
import de.jpx3.intave.connect.cloud.protocol.Token;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.connect.cloud.protocol.packets.*;
import de.jpx3.intave.connect.cloud.protocol.packets.ServerboundPlayerPlayStateChange.PlayState;
import de.jpx3.intave.connect.cloud.request.CloudStorageGateaway;
import de.jpx3.intave.connect.cloud.request.CloudTrustfactorResolver;
import de.jpx3.intave.connect.cloud.request.Request;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@HighOrderService
public final class Cloud {
  // later
  private static final Resource INITIAL_SHARED_KEY_RESOURCE = Resources.localServiceCacheResource("cloud-initial.dat", "cloud-initial", TimeUnit.DAYS.toMillis(30));
  private static final Resource SHARD_STORAGE_RESOURCE = Resources.fileCache("shardStorage");
  private static final ShardCache shardCache = SHARD_STORAGE_RESOURCE.collectLines(ShardCache.resourceCollector());

  private final Map<Shard, Session> sessions = new HashMap<>();
  private final Map<Shard, Integer> reconnectAttempts = new ConcurrentHashMap<>();
  private volatile long lastUnsendablePacketLog = 0L;
  private final Map<UUID, Request<TrustFactor>> trustfactorRequests = new HashMap<>();
  private final Map<UUID, Request<ByteBuffer>> storageRequests = new HashMap<>();
  private final Map<UUID, Request<Classifier>> sampleTransmissionRequests = new HashMap<>();
  private final Map<UUID, Request<Map<String, String>>> statusInquiryRequests = new HashMap<>();
  private final Map<Integer, Request<String>> uploadLogRequests = new HashMap<>();
  private CloudConfig cloudConfig;
  private Task task;
  private boolean wasConnected = false;
  private boolean lastAttemptFailed = false;

  public void init() {
    setupKeepAliveTick();
  }

  public void configInit(ConfigurationSection config) {
    cloudConfig = CloudConfig.from(config);
  }

  public void connectMasterShard() {
    if (cloudConfig.isEnabled()) {
      openSession(shardCache.masterShard());
      ShutdownTasks.add(this::disable);
    } else {
      IntaveLogger.logger().info("Cloud is disabled");
    }
  }

  private void disable() {
    sessions.values().forEach(Session::close);
    task.cancel();
    if (shardCache.wasModified() && !IntaveControl.CLOUD_LOCALHOST_MASTER_SHARD) {
      SHARD_STORAGE_RESOURCE.write(shardCache.compiledLines());
    }
  }

  public void openSession(Shard shard) {
    if (shard == null) {
      throw new IllegalArgumentException("Shard cannot be null");
    }
    Session session = new Session(shard, this);
    session.tryToConnect(success -> {
      if (success) {
        session.subscribeToStarted(unused -> {
          reconnectAttempts.remove(shard);
          if (lastAttemptFailed) {
            IntaveLogger.logger().info("Successfully reconnected to " + shard);
            lastAttemptFailed = false;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
              IntavePlugin.singletonInstance().logTransmittor()
                .addPlayerLog(onlinePlayer, "--- CLOUD RECONNECTED ---");
            }
          }
          setTrustAndStorage();
          askForGlobalSampleTransmission();
          wasConnected = true;
        });
      } else {
        lastAttemptFailed = true;
        // called on failure or connection closure
        int attempts = reconnectAttempts.getOrDefault(shard, 0);
        int retryingIn = (int) (Math.pow(2, attempts + 1.75) * 2) + 10;

        // add random 25% jitter
        retryingIn += (int) (retryingIn * (Math.random() * 0.25));

        if (wasConnected) {
          try {
            Nayoro nayoro = Modules.nayoro();
            int delay = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
              IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(onlinePlayer, "--- CLOUD LOST ---");
              nayoro.disableRecordingFor(UserRepository.userOf(onlinePlayer));
            }
          } catch (Exception exception) {
            // just return
            return;
          }
          wasConnected = false;
        }

        IntaveLogger.logger().info(
          String.format("Cloud reconnect unsuccessful, retrying in %d seconds, attempt %d/20", retryingIn, attempts + 1)
        );
        if (attempts < 20) {
          reconnectAttempts.put(shard, attempts + 1);
          Synchronizer.synchronizeDelayed(() -> {
            BackgroundExecutors.executeWhenever(() -> openSession(shard));
          }, 20 * retryingIn);
        } else {
          IntaveLogger.logger().warning("Unable to connect to " + shard + " after 20 attempts");
          IntaveLogger.logger().warning("We will try to reconnect every 12 hours now");
          Synchronizer.synchronizeDelayed(() -> {
            BackgroundExecutors.executeWhenever(() -> openSession(shard));
          }, 20 * 60 * 60 * 12);
        }
        sessions.remove(shard);
      }
    });
    sessions.put(shard, session);
  }

  private void setupKeepAliveTick() {
    task = Tasks.periodicNamed("Cloud.keepAliveTick", () -> {
      keepAliveTick();
      removeUnansweredRequests();
    }, 20 * 10, 20 * 30).startAsync();
  }

  private void setTrustAndStorage() {
    IntaveAccess access = IntaveAccessor.unsafeAccess();
    CloudConfig.CloudFeatures features = cloudConfig.features();
    if (features.cloudTrustfactorEnabled()) {
      TrustFactorService trustFactorService = IntavePlugin.singletonInstance().trustFactorService();
      TrustFactorResolver custom = trustFactorService.customTrustFactorResolver();
      TrustFactorResolver newResolver = new DefaultForwardingPermissionTrustFactorResolver(new CloudTrustfactorResolver(this));
      if (custom != null) {
        newResolver = new SelectiveTrustfactorResolver(custom, newResolver);
      }
      trustFactorService.setDirectTrustFactorResolver(newResolver);
    }
    if (features.cloudStorageEnabled()) {
      access.setStorageGateway(new CloudStorageGateaway(this));
    }
  }

  private void askForGlobalSampleTransmission() {
    try {
      int delay = 0;
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        Nayoro nayoro = Modules.nayoro();
        if (!nayoro.recordingActiveFor(UserRepository.userOf(onlinePlayer))) {
          Synchronizer.synchronizeDelayed(() -> {
            nayoro.askForSampleTransmission(onlinePlayer);
          }, delay);
          delay++;
        }
      }
    } catch (Exception ignored) {}
  }

  public void setMasterShard(
    String host, int port, byte[] tokenBytes, long tokenValidUntil
  ) {
    shardCache.addShard(new Shard("master", host, port, new Token(tokenBytes, tokenValidUntil)));
  }

  public boolean knowsMasterShard() {
    return shardCache.hasMasterShard() &&
      shardCache.masterCloudToken().isStillValidIn(5, TimeUnit.MINUTES);
  }

  public long sentBytes() {
    return sessions.values().stream().mapToLong(Session::sentBytes).sum();
  }

  public long receivedBytes() {
    return sessions.values().stream().mapToLong(Session::receivedBytes).sum();
  }

  public Map<Shard, Long> sentBytesPerShard() {
    Map<Shard, Long> sent = new HashMap<>();
    for (Shard shard : sessions.keySet()) {
      sent.put(shard, sessions.get(shard).sentBytes());
    }
    return sent;
  }

  public Map<Shard, Long> receivedBytesPerShard() {
    Map<Shard, Long> received = new HashMap<>();
    for (Shard shard : sessions.keySet()) {
      received.put(shard, sessions.get(shard).receivedBytes());
    }
    return received;
  }

  public Map<Shard, Boolean> shardConnections() {
    Map<Shard, Boolean> connections = new HashMap<>();
    for (Shard shard : sessions.keySet()) {
      connections.put(shard, sessions.get(shard).active());
    }
    return connections;
  }

  private void sendPacket(Packet<Serverbound> packet) {
    BackgroundExecutors.execute(() -> {
      boolean sent = false;
      for (Session session : sessions.values()) {
        if (session.canSend(packet)) {
          session.send(packet);
          sent = true;
          if (IntaveControl.AUTHENTICATION_DEBUG_MODE) {
            IntaveLogger.logger().info("Sent packet " + packet.name() + " to " + session.shard());
          }
          break;
        }
      }
      boolean cloudWasDeactivated = !cloudConfig.isEnabled();
      if (!sent && !cloudWasDeactivated) {
        // While the cloud is enabled but not connected, every upload (logs, join
        // state, …) lands here; the reconnect loop already reports the connection
        // state, so rate-limit this to avoid flooding the console.
        long now = System.currentTimeMillis();
        if (now - lastUnsendablePacketLog > 30_000L) {
          lastUnsendablePacketLog = now;
          IntaveLogger.logger().error("Unable to send packet " + packet.name() + " to any shard (no shard connected; suppressing repeats for 30s)");
        }
      }
    });
  }

  private void keepAliveTick() {
    for (Session session : sessions.values()) {
      session.keepAliveTick();
    }
  }

  private void removeUnansweredRequests() {
    long now = System.currentTimeMillis();
    long timeout = 1000 * 60 * 5;
    sampleTransmissionRequests.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > timeout);
    statusInquiryRequests.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > timeout && entry.getValue().publish(new HashMap<>()));
    trustfactorRequests.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > timeout);
    uploadLogRequests.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > timeout);
    storageRequests.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > timeout);
  }

  public void requestSampleTransmission(Player player, Consumer<Classifier> callbackIfAccepted) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    Nayoro nayoro = Modules.nayoro();
    if (nayoro.recordingActiveFor(UserRepository.userOf(player))) {
      return;
    }
    UUID id = player.getUniqueId();
    Request<Classifier> request = sampleTransmissionRequests.computeIfAbsent(id, k -> new Request<>());
    request.subscribe(callbackIfAccepted);
    sendPacket(new ServerboundSampleTransmissionRequest(Identity.from(player)));
  }

  public void requestSampleTransmission(Player player, Classifier classifier, String cheatOrScenario, String version, Consumer<Classifier> callbackIfAccepted) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    UUID id = player.getUniqueId();
    Request<Classifier> request = sampleTransmissionRequests.computeIfAbsent(id, k -> new Request<>());
    request.subscribe(callbackIfAccepted);
    sendPacket(new ServerboundSampleTransmissionRequest(Identity.from(player), classifier, cheatOrScenario, version));
  }

  public void noteEndOfSampleTransmission(Player player) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    sendPacket(new ServerboundSampleCompleted(Identity.from(player)));
  }

  public void serveSampleTransmissionRequest(Identity identity, boolean allowed, Classifier classifier) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    Request<Classifier> request = sampleTransmissionRequests.remove(identity.id());
    if (request != null && allowed) {
      request.publish(classifier);
    }
  }

  public void uploadSample(Player player, ByteBuffer buffer) {
    if (!available()) {
      return;
    }
    sendPacket(new ServerboundPassNayoro(Identity.from(player), buffer));
  }

  public void uploadPlayerLogs(Player player, int nonce, List<String> logs, Consumer<String> callback) {
    if (!cloudConfig.features().isCloudLogs() || !available()) {
      return;
    }
    Request<String> request = uploadLogRequests.computeIfAbsent(nonce, k -> new Request<>());
    request.subscribe(callback);
    sendPacket(new ServerboundUploadLogs(Identity.from(player), nonce, logs));
  }

  public void serveUploadPlayerLogs(Identity identity, int nonce, String logId) {
    Request<String> request = uploadLogRequests.remove(nonce);
    if (request != null) {
      request.publish(logId);
    }
  }

  public void trustfactorRequest(Player player, Consumer<TrustFactor> callback) {
    if (!available()) {
      return;
    }
    UUID key = player.getUniqueId();
    Request<TrustFactor> request = trustfactorRequests.computeIfAbsent(key, k -> new Request<>());
    request.subscribe(callback);
    sendPacket(new ServerboundRequestTrustfactor(Identity.from(player)));
  }

  public void serveTrustfactorRequest(Identity identity, TrustFactor trustFactor) {
    Request<TrustFactor> request = trustfactorRequests.remove(identity.id());
    if (request != null) {
      request.publish(trustFactor);
    }
  }

  public void storageRequest(UUID id, Consumer<ByteBuffer> callback) {
    if (!available()) {
      return;
    }
    Request<ByteBuffer> request = storageRequests.computeIfAbsent(id, k -> new Request<>());
    request.subscribe(callback);
    sendPacket(new ServerboundRequestStorage(Identity.from(id)));
  }

  public void serveStorageRequest(Identity identity, ByteBuffer buffer) {
    Request<ByteBuffer> request = storageRequests.remove(identity.id());
    if (request != null) {
      request.publish(buffer);
    }
  }

  public void saveStorage(UUID id, ByteBuffer buffer) {
    if (!available()) {
      return;
    }
    sendPacket(new ServerboundUploadStorage(Identity.from(id), buffer));
  }

  public void generalStatusInquiry(
    Consumer<Map<String, String>> callback
  ) {
    if (!available()) {
      callback.accept(new HashMap<>());
      return;
    }
    if (statusInquiryRequests.size() > 100) {
      callback.accept(new HashMap<>());
      return;
    }
    UUID id = UUID.randomUUID();
    Request<Map<String, String>> request = statusInquiryRequests.computeIfAbsent(id, k -> new Request<>());
    request.subscribe(callback);
    sendPacket(new ServerboundStatusInquiry(id, ServerboundStatusInquiry.Type.GENERAL, null));
  }

  public void playerStatusInquiry(
    Player player, Consumer<Map<String, String>> callback
  ) {
    if (!available()) {
      callback.accept(new HashMap<>());
      return;
    }
    if (statusInquiryRequests.size() > 100) {
      callback.accept(new HashMap<>());
      return;
    }
    UUID id = player.getUniqueId();// hehe
    Request<Map<String, String>> request = statusInquiryRequests.computeIfAbsent(id, k -> new Request<>());
    request.subscribe(callback);
    sendPacket(new ServerboundStatusInquiry(id, ServerboundStatusInquiry.Type.PLAYER, Identity.from(player)));
  }

  public void serveInquiryResponse(UUID requestId, Map<String, String> status) {
    Request<Map<String, String>> request = statusInquiryRequests.remove(requestId);
    if (request != null) {
      request.publish(status);
    }
  }

  public void playerStateChange(
    Player player, UUID gameId, boolean online, List<UUID> interacted
  ) {
    if (!available()) {
      return;
    }
    sendPacket(ServerboundPlayerPlayStateChange.from(
      Identity.from(player), online ? PlayState.JOIN : PlayState.LEAVE, gameId,
      interacted.stream().map(Identity::from).collect(Collectors.toList())
    ));
  }

  public boolean isEnabled() {
    return cloudConfig.isEnabled();
  }

  public boolean available() {
    return sessions.values().stream().anyMatch(Session::active);
  }
}
