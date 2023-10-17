package de.jpx3.intave.connect.cloud;

import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveAccess;
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
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.util.*;
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
    }
  }

  public void openSession(Shard shard) {
    if (shard == null) {
      throw new IllegalArgumentException("Shard cannot be null");
    }
    IntaveLogger.logger().info("Connecting to " + shard);
    Session session = new Session(shard, this);
    session.init(success -> {
      if (success) {
        IntaveLogger.logger().info("Connected to " + shard);
        setTrustAndStorage();
      } else {
        // called on failure or connection closure
        int attempts = reconnectAttempts.getOrDefault(shard, 0);
        IntaveLogger.logger().warning("Unable to connect to " + shard + ", retrying in 5 seconds, attempt " + attempts + "/3");
        if (attempts < 3) {
          reconnectAttempts.put(shard, attempts + 1);
          Synchronizer.synchronizeDelayed(() -> openSession(shard), 20 * 5);
        } else {
          IntaveLogger.logger().warning("Unable to connect to " + shard + " after 3 attempts");
        }
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
      unsafe.setTrustFactorResolver(new CloudTrustfactorResolver(this));
    if (cloudConfig.features().cloudStorageEnabled())
      unsafe.setStorageGateway(new CloudStorageGateaway(this));
  }

  private void disable() {
    sessions.values().forEach(Session::close);
    Bukkit.getScheduler().cancelTask(taskId);
    TaskTracker.stopped(taskId);
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
      for (Session session : sessions.values()) {
        if (session.canSend(packet)) {
          session.send(packet);
          break;
        }
      }
    });
  }

  private void keepAliveTick() {
    for (Session session : sessions.values()) {
      session.keepAliveTick();
    }
  }

  public void uploadSample(Player player, ByteBuffer buffer) {
    sendPacket(new ServerboundPassNayoroPacket(Identity.from(player), buffer));
  }

  public void uploadPlayerLogs(Player player, int nonce, List<String> logs, Consumer<String> callback) {
    Request<String> request = uploadLogRequests.get(nonce);
    if (request == null) {
      request = new Request<>();
      uploadLogRequests.put(nonce, request);
    }
    request.subscribe(callback);
    sendPacket(new ServerboundUploadLogsPacket(Identity.from(player), nonce, logs));
  }

  public void serveUploadPlayerLogs(Identity identity, int nonce, String logId) {
    Request<String> request = uploadLogRequests.remove(nonce);
    if (request != null) {
      request.publish(logId);
    }
  }

  public void trustfactorRequest(Player player, Consumer<TrustFactor> callback) {
    UUID key = player.getUniqueId();
    Request<TrustFactor> request = trustfactorRequests.get(key);
    if (request == null) {
      request = new Request<>();
      trustfactorRequests.put(key, request);
    }
    request.subscribe(callback);
    sendPacket(new ServerboundRequestTrustfactorPacket(Identity.from(player)));
  }

  public void serveTrustfactorRequest(Identity identity, TrustFactor trustFactor) {
    Request<TrustFactor> request = trustfactorRequests.remove(identity.id());
    if (request != null) {
      request.publish(trustFactor);
    }
  }

  public void storageRequest(UUID id, Consumer<ByteBuffer> callback) {
    Request<ByteBuffer> request = storageRequests.get(id);
    if (request == null) {
      request = new Request<>();
      storageRequests.put(id, request);
    }
    request.subscribe(callback);
    sendPacket(new ServerboundRequestStoragePacket(Identity.from(id)));
  }

  public void serveStorageRequest(Identity identity, ByteBuffer buffer) {
    Request<ByteBuffer> request = storageRequests.remove(identity.id());
    if (request != null) {
      request.publish(buffer);
    }
  }

  public void saveStorage(UUID id, ByteBuffer buffer) {
    sendPacket(new ServerboundUploadStoragePacket(Identity.from(id), buffer));
  }

  public boolean isEnabled() {
    return cloudConfig.isEnabled();
  }

  public boolean available() {
    return sessions.values().stream().anyMatch(Session::active);
  }
}
