package de.jpx3.intave.connect.cloud;

import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.player.trust.DefaultForwardingPermissionTrustFactorResolver;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.Shard;
import de.jpx3.intave.connect.cloud.protocol.Token;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.connect.cloud.protocol.packets.*;
import de.jpx3.intave.connect.cloud.request.CloudStorageGateaway;
import de.jpx3.intave.connect.cloud.request.CloudTrustfactorResolver;
import de.jpx3.intave.connect.cloud.request.Request;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
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

@HighOrderService
public final class Cloud {
  // later
  private static final Resource INITIAL_SHARED_KEY_RESOURCE = Resources.localServiceCacheResource("cloud-initial.dat", "cloud-initial", TimeUnit.DAYS.toMillis(30));
  private static final Resource SHARD_STORAGE_RESOURCE = Resources.fileCache("shardStorage");
  private static final ShardCache shardCache = SHARD_STORAGE_RESOURCE.collectLines(ShardCache.resourceCollector());

  private final Map<Shard, Session> sessions = new HashMap<>();
  private final Map<UUID, Request<TrustFactor>> trustfactorRequests = new HashMap<>();
  private final Map<UUID, Request<ByteBuffer>> storageRequests = new HashMap<>();
  private final Map<Integer, Request<String>> uploadLogRequests = new HashMap<>();
  private final Map<UUID, Request<Classifier>> sampleTransmissionRequests = new HashMap<>();
  private CloudConfig cloudConfig;
  private int taskId;

  private final Map<Shard, Integer> reconnectAttempts = new ConcurrentHashMap<>();

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
    Bukkit.getScheduler().cancelTask(taskId);
    TaskTracker.stopped(taskId);
    if (shardCache.wasModified() && !IntaveControl.CLOUD_LOCALHOST_MASTER_SHARD) {
      SHARD_STORAGE_RESOURCE.write(shardCache.compiledLines());
    }
  }

  public void openSession(Shard shard) {
    if (shard == null) {
      throw new IllegalArgumentException("Shard cannot be null");
    }
//    IntaveLogger.logger().info("Connecting to " + shard);
    Session session = new Session(shard, this);
    session.init(success -> {
      if (success) {
//        IntaveLogger.logger().info("Authenticating with " + shard + "..");
        session.subscribeToStarted(unused -> {
          reconnectAttempts.remove(shard);
//          IntaveLogger.logger().info("Connected to " + shard);
          setTrustAndStorage();
        });
      } else {
        // called on failure or connection closure
        int attempts = reconnectAttempts.getOrDefault(shard, 0);
        int retryingIn = (int) (Math.pow(2, attempts) * 2);

        try {
          Nayoro nayoro = Modules.nayoro();
          for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            nayoro.disableRecordingFor(UserRepository.userOf(onlinePlayer));
          }
        } catch (Exception exception) {
          // just return
          return;
        }

        IntaveLogger.logger().warning("Unable to connect to " + shard + ", retrying in " + retryingIn + " seconds, attempt " + attempts + "/10");
        if (attempts < 10) {
          reconnectAttempts.put(shard, attempts + 1);
          Synchronizer.synchronizeDelayed(() -> {
            BackgroundExecutors.executeWhenever(() -> openSession(shard));
          }, 20 * retryingIn);
        } else {
          IntaveLogger.logger().warning("Unable to connect to " + shard + " after 10 attempts");
        }
        sessions.remove(shard);
      }
    });
    sessions.put(shard, session);
  }

  private void setupKeepAliveTick() {
    taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(
      IntavePlugin.singletonInstance(), this::keepAliveTick, 20 * 10, 20 * 30
    );
    TaskTracker.begun(taskId);
  }

  private void setTrustAndStorage() {
    IntaveAccess unsafe = IntaveAccessor.unsafeAccess();
    if (cloudConfig.features().cloudTrustfactorEnabled())
      unsafe.setTrustFactorResolver(new DefaultForwardingPermissionTrustFactorResolver(new CloudTrustfactorResolver(this)));
    if (cloudConfig.features().cloudStorageEnabled())
      unsafe.setStorageGateway(new CloudStorageGateaway(this));
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
          break;
        }
      }
      if (!sent) {
        IntaveLogger.logger().error("Unable to send packet " + packet.name() + " to any shard");
      }
    });
  }

  private void keepAliveTick() {
    for (Session session : sessions.values()) {
      session.keepAliveTick();
    }
  }

  public void requestSampleTransmission(Player player,  Consumer<Classifier> callbackIfAccepted) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    UUID id = player.getUniqueId();
    Request<Classifier> request = sampleTransmissionRequests.get(id);
    if (request == null) {
      request = new Request<>();
      sampleTransmissionRequests.put(id, request);
    }
    request.subscribe(callbackIfAccepted);
    sendPacket(new ServerboundSampleTransmissionRequest(Identity.from(player)));
  }

  public void requestSampleTransmission(Player player, Classifier classifier, String cheatOrScenario, String version, Consumer<Classifier> callbackIfAccepted) {
    if (!cloudConfig.isEnabled() || !cloudConfig.features().sampleTransmission()) {
      return;
    }
    UUID id = player.getUniqueId();
    Request<Classifier> request = sampleTransmissionRequests.get(id);
    if (request == null) {
      request = new Request<>();
      sampleTransmissionRequests.put(id, request);
    }
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
    sendPacket(new ServerboundPassNayoro(Identity.from(player), buffer));
  }

  public void uploadPlayerLogs(Player player, int nonce, List<String> logs, Consumer<String> callback) {
    Request<String> request = uploadLogRequests.get(nonce);
    if (request == null) {
      request = new Request<>();
      uploadLogRequests.put(nonce, request);
    }
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
    Request<TrustFactor> request = trustfactorRequests.get(key);
    if (request == null) {
      request = new Request<>();
      trustfactorRequests.put(key, request);
    }
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
    Request<ByteBuffer> request = storageRequests.get(id);
    if (request == null) {
      request = new Request<>();
      storageRequests.put(id, request);
    }
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
    sendPacket(new ServerboundUploadStorage(Identity.from(id), buffer));
  }

  public boolean isEnabled() {
    return cloudConfig.isEnabled();
  }

  public boolean available() {
    return sessions.values().stream().anyMatch(Session::active);
  }
}
