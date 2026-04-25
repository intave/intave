package de.jpx3.intave.user;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.block.fluid.FluidFlow;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.connect.cloud.LogTransmittor;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.actionbar.DisplayType;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import de.jpx3.intave.module.feedback.FeedbackSender;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.mitigate.HurttimeModifier;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.user.permission.ExpiringPermissionCache;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.user.storage.PlayerStorage;
import de.jpx3.intave.user.storage.Storage;
import de.jpx3.intave.user.storage.Storages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;

final class PlayerUser implements User {
  private final Map<Class<? extends CheckCustomMetadata>, CheckCustomMetadata> metadataPool = new ConcurrentHashMap<>();
  private final Reference<Player> player;
  private final Reference<Object> playerHandle;
  private final Reference<Object> playerConnection;
  private final MetadataBundle metadata;
  private final PermissionCache permissionCache;
  private final List<MessageChannel> receivingUserChannels = new ArrayList<>();
  private final Map<MessageChannel, Predicate<Player>> channelConstraints = Maps.newEnumMap(MessageChannel.class);
  private final Map<Material, Material> typeTranslations = Maps.newEnumMap(Material.class);
  private final BlockCache blockStateAccess;
  private final long birth = System.currentTimeMillis();
  private final Map<UUID, DisplayType> actionSubscriptions = GarbageCollector.watch(Maps.newConcurrentMap());
  private final Map<DisplayType, String> actionDisplay = Maps.newConcurrentMap();
  private final UserContext playerPlaceholderContext = UserContext.createFor(this);
  private final PlayerContext playerContext;
  private final PlayerStorage storage;
  private final Lock storageSubscriptionLock = new ReentrantLock();
  private final Queue<Reference<Runnable>> storageSubscriptionQueue = new ArrayDeque<>();
  private Collider collider;
  private FluidFlow waterflow;
  private SimpleCollider simpleCollider;
  private Map<Pose, HitboxSize> poseSizes;
  private boolean ignoreNextInboundPacket;
  private boolean ignoreNextOutboundPacket;
  private CustomClientSupportConfig customClientConfig = CustomClientSupportConfig.createDefault();
  private String currentActionDisplay = "", overrideActionDisplay = "";
  private UUID actionTarget = null;
  private TrustFactor trustFactor = TrustFactor.DARK_RED;
  private boolean storageLoaded;
  private boolean disconnectQueued = false;

  PlayerUser(Player player) {
    this.player = new WeakReference<>(player);
    this.playerHandle = new WeakReference<>(ReflectiveHandleAccess.handleOf(player));
    this.playerConnection = new WeakReference<>(ReflectiveHandleAccess.playerConnectionOf(player));
    this.metadata = new MetadataBundle(player, this);
    this.permissionCache = ExpiringPermissionCache.withDefaultExpirationTime();
    this.blockStateAccess = BlockCaches.cacheForPlayer(player);
    this.collider = Colliders.suitableComplexColliderProcessorFor(this);
    this.waterflow = Fluids.suitableWaterflowFor(this);
    this.simpleCollider = Colliders.suitableSimpleColliderProcessorFor(this);
    Synchronizer.synchronize(this::setDefaultMessagingChannel);
    this.playerContext = PlayerContext.of(player);
    this.storage = Storages.emptyPlayerStorageFor(player.getUniqueId());
    this.poseSizes = Pose.poseSizesByVersion(metadata.protocol().protocolVersion());
    this.metadata.setup();
  }

  public void setDefaultMessagingChannel() {
    for (MessageChannel channel : MessageChannel.values()) {
      if (channel.enabledByDefault && BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
        toggleReceive(channel);
        removeChannelConstraint(channel);
      }
    }
  }

  @Override
  public void delayedSetup() {
    Player player = player();
    ProtocolMetadata clientData = meta().protocol();
    clientData.refresh(player);
    applyNewProtocolVersion();
    outputVersionJoinInfo();
  }

  @Override
  public void applyNewProtocolVersion() {
    this.waterflow = Fluids.suitableWaterflowFor(this);
    this.collider = Colliders.suitableComplexColliderProcessorFor(this);
    this.simpleCollider = Colliders.suitableSimpleColliderProcessorFor(this);
    this.poseSizes = Pose.poseSizesByVersion(metadata.protocol().protocolVersion());
    BlockTypeAccess.setupTranslationsFor(this);
    meta().movement().setupDefaults();
  }

  private void outputVersionJoinInfo() {
    Player player = player();
    LogTransmittor logTransmittor = IntavePlugin.singletonInstance().logTransmittor();
    ProtocolMetadata clientData = meta().protocol();
    if (hasDisabledLogs()) {
      logTransmittor.addPlayerLog(player, "[SYSTEM] Disabled logs");
    }
    logTransmittor.addPlayerLog(player, "(JOIN) " + player.getName() + " joined game "+IntavePlugin.gameId()+" with version " + clientData.versionString() + "/" + clientData.protocolVersion() + " and locale " + clientData.locale());
    if (!ConsoleOutput.CLIENT_VERSION_DEBUG) {
      return;
    }
    String string = player.getName() + " joined with version " + clientData.versionString() + "/" + clientData.protocolVersion();
    if (clientData.outdatedClient()) {
      string += " (behind)";
    }
    string += " and locale " + clientData.locale();
    IntaveLogger.logger().info(string);
  }

  private Boolean disabledCache = null;

  private boolean hasDisabledLogs() {
    if (disabledCache != null) {
      return disabledCache;
    }
    ConfigurationSection featuresSection = IntavePlugin.singletonInstance().settings().getConfigurationSection("cloud.features");
    return disabledCache = featuresSection != null && !featuresSection.getBoolean("logs", featuresSection.getBoolean("cloud-logs", true));
  }

  @Override
  public UUID id() {
    return player().getUniqueId();
  }

  @Override
  public MetadataBundle meta() {
    return this.metadata;
  }

  @Override
  public Object playerHandle() {
    return playerHandle.get();
  }

  @Override
  public Object playerConnection() {
    return playerConnection.get();
  }

  @Override
  public Player player() {
    Player player = this.player.get();
    if (player == null) {
      throw new IntaveInternalException("Player be gone");
    }
    return player;
  }

  @Override
  public boolean justJoined() {
    return System.currentTimeMillis() - birth < 5000;
  }

  @Override
  public long joined() {
    return birth;
  }

  @Override
  public boolean hasPlayer() {
    return true;
  }

  private boolean isOnline(OfflinePlayer player) {
    return player != null && (player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != null);
  }

  @Override
  public CheckCustomMetadata checkMetadata(
    Class<? extends CheckCustomMetadata> metaClass,
    Function<? super User, ? extends CheckCustomMetadata> generator
  ) {
    return metadataPool.computeIfAbsent(metaClass, key -> generator.apply(this));
  }

  @Override
  public PermissionCache permissionCache() {
    return permissionCache;
  }

  @Override
  public CustomClientSupportConfig customClientSupport() {
    return customClientConfig;
  }

  @Override
  public void setCustomClientSupport(CustomClientSupportConfig config) {
    this.customClientConfig = config;
  }

  @Override
  public boolean shouldIgnoreNextInboundPacket() {
    return ignoreNextInboundPacket;
  }

  @Override
  public boolean shouldIgnoreNextOutboundPacket() {
    return ignoreNextOutboundPacket;
  }

  @Override
  public void ignoreNextInboundPacket() {
    this.ignoreNextInboundPacket = true;
  }

  @Override
  public void ignoreNextOutboundPacket() {
    this.ignoreNextOutboundPacket = true;
  }

  @Override
  public void receiveNextInboundPacketAgain() {
    this.ignoreNextInboundPacket = false;
  }

  @Override
  public void receiveNextOutboundPacketAgain() {
    this.ignoreNextOutboundPacket = false;
  }

  @Override
  public Storage mainStorage() {
    return storage;
  }

  @Override
  public void onStorageReady(Consumer<? super Storage> consumer) {
    try {
      storageSubscriptionLock.lock();
      if (storageLoaded) {
        consumer.accept(storage);
      } else {
        storageSubscriptionQueue.add(new SoftReference<>(() -> consumer.accept(storage)));
      }
    } finally {
      storageSubscriptionLock.unlock();
    }
  }

  @Override
  public void notifyStorageLoadSubscribers() {
    try {
      storageSubscriptionLock.lock();
      storageLoaded = true;
      Reference<Runnable> runnableRef;
      Runnable runnable;
      while ((runnableRef = storageSubscriptionQueue.poll()) != null && (runnable = runnableRef.get()) != null) {
        runnable.run();
      }
    } finally {
      storageSubscriptionLock.unlock();
    }
  }

  @Override
  public <T extends Storage> T storageOf(Class<T> storageClass) {
    return storage.storageOf(storageClass);
  }

  @Override
  public BlockCache blockCache() {
    return blockStateAccess;
  }

  @Override
  public Collider collider() {
    return collider;
  }

  @Override
  public FluidFlow waterflow() {
    return waterflow;
  }

  @Override
  public SimpleCollider simplifiedCollider() {
    return simpleCollider;
  }

  @Override
  public PlayerContext playerContext() {
    return playerContext;
  }

  @Override
  public TrustFactor trustFactor() {
    return trustFactor;
  }

  @Override
  public void setTrustFactor(TrustFactor trustFactor) {
    this.trustFactor = trustFactor;
  }

  @Override
  public int trustFactorSetting(String key) {
    return plugin().trustFactorService().trustFactorSetting(key, player());
  }

  @Override
  public boolean receives(MessageChannel channel) {
    boolean receives = receivesCurrently(channel);
    if (receives && !BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
      toggleReceive(channel);
      return false;
    }
    return receives;
  }

  @Override
  public void toggleReceive(MessageChannel channel) {
    boolean remove = receivesCurrently(channel);
    if (remove) {
      receivingUserChannels.remove(channel);
    } else {
      receivingUserChannels.add(channel);
    }
    MessageChannelSubscriptions.setChannelActivation(player(), channel, !remove);
  }

  private boolean receivesCurrently(MessageChannel channel) {
    return receivingUserChannels.contains(channel);
  }

  @Override
  public void setChannelConstraint(MessageChannel channel, Predicate<Player> constraint) {
    channelConstraints.put(channel, constraint);
  }

  @Override
  public boolean hasChannelConstraint(MessageChannel channel) {
    return channelConstraints.containsKey(channel);
  }

  @Override
  public Predicate<Player> channelPlayerConstraint(MessageChannel channel) {
    return channelConstraints.get(channel);
  }

  @Override
  public void nerf(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    Modules.mitigate().combat().mitigate(this, strategy, checkId);
  }

  @Override
  public void nerfOnce(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    Modules.mitigate().combat().mitigateOnce(this, strategy, checkId);
  }

  @Override
  public void nerfPermanently(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    Modules.mitigate().combat().mitigatePermanently(this, strategy, checkId);
  }

  @Override
  public void removeChannelConstraint(MessageChannel channel) {
    channelConstraints.remove(channel);
  }

  @Override
  public int latency() {
    return (int) (meta().connection().transactionPingAverage() + 0.5);
  }

  @Override
  public int latencyJitter() {
    return meta().connection().latencyJitter;
  }

  @Override
  public int protocolVersion() {
    return meta().protocol().protocolVersion();
  }

  @Override
  public UserContext userContext() {
    return playerPlaceholderContext;
  }

  @Override
  public HitboxSize sizeOf(Pose pose) {
    HitboxSize size = poseSizes.get(pose);
    double scale = meta().abilities().attributeValue("generic.scale");
    if (Double.isNaN(scale)) {
      return size;
    }
    size = size.scaled(scale);
    return size;
  }

  @Override
  public void clearTypeTranslations() {
    typeTranslations.clear();
    blockStateAccess.invalidateAll();
  }

  @Override
  public void applyTypeTranslation(Material from, Material to) {
    typeTranslations.put(from, to);
  }

  @Override
  public Material typeTranslationOf(Material source) {
    return typeTranslations.get(source);
  }

  public String actionDisplayOf(DisplayType type) {
    return actionDisplay.get(type);
  }

  public String currentActionDisplay() {
    return currentActionDisplay;
  }

  public void setCurrentActionDisplay(String currentActionDisplay) {
    this.currentActionDisplay = currentActionDisplay;
  }

  public String overrideActionDisplay() {
    return overrideActionDisplay;
  }

  public void setOverrideActionDisplay(String overrideActionDisplay) {
    this.overrideActionDisplay = overrideActionDisplay;
  }

  public void pushActionDisplayToSubscribers(DisplayType type, String message) {
    if (message == null) {
      return;
    }
    this.actionDisplay.put(type, message);
  }

  public UUID actionTarget() {
    return actionTarget;
  }

  public void setActionTarget(UUID target) {
    this.actionTarget = target;
  }

  public boolean anyActionSubscriptions() {
    return !actionSubscriptions.isEmpty();
  }

  public void addActionReceiver(UUID subscriber, DisplayType type) {
    actionSubscriptions.put(subscriber, type);
  }

  public void removeActionSubscription(UUID id) {
    actionSubscriptions.remove(id);
  }

  @Override
  public void noteFeedbackFault() {
    ConnectionMetadata connectionData = metadata.connection();
    if (!justJoined() && connectionData.lastReceivedTransactionNum > 100 && connectionData.feedbackFaults++ > 10 && FaultKicks.FEEDBACK_FAULTS) {
      if (ConsoleOutput.FAULT_KICKS) {
        IntaveLogger.logger().info(player().getName() + " will be removed for repeated feedback faults");
      }
      kick("Repeated feedback faults");
    }
  }

  @Override
  public synchronized void kick(String reason) {
    if (disconnectQueued) {
      return;
    }
    disconnectQueued = true;
    if (ConsoleOutput.FAULT_KICKS) {
      IntaveLogger.logger().info("Queuing manual disconnect of player " + player().getName() + " for " + reason.toLowerCase());
      IntaveLogger.logger().info("This measure is a security-constraint necessity, but feel free to contact us if this happens too often");
    }
    Synchronizer.synchronize(() -> {
      Player player = player();
      if (player.isOnline()) {
        player.kickPlayer(reason);
      }
    });
  }

  @Override
  public void message(String key, Object... args) {

  }

  @Override
  public void unregister() {
    FakePlayer fakePlayer = meta().attack().fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.remove();
    }
    HurttimeModifier.removeNoDamageTickChangeOf(this);
    for (MessageChannel value : MessageChannel.values()) {
      MessageChannelSubscriptions.setChannelActivation(player(), value, false);
    }
  }

  @Override
  public void refreshSprintState(Consumer<Void> callback) {
    Player player = player();
    FeedbackSender feedbackSender = Modules.feedback();
    feedbackSender.synchronize(player, this, (player1, user) -> {
      sendStatsUpdate(player, 0, 0);
      feedbackSender.synchronize(player, null, (player2, target1) -> {
        feedbackSender.synchronize(player, null, (player3, target2) -> {
          sendStatsUpdate(player, player.getFoodLevel(), player.getSaturation());
          feedbackSender.synchronize(player, null, (player4, target3) -> {
            if (callback != null) {
              callback.accept(null);
            }
          }, SELF_SYNCHRONIZATION);
        }, SELF_SYNCHRONIZATION);
      }, SELF_SYNCHRONIZATION);
    }, SELF_SYNCHRONIZATION);
  }

  @Override
  public void tickFeedback(EmptyFeedbackCallback callback) {
    Modules.feedback().synchronize(player(), (player1, target) -> callback.success(player1, null));
  }

  @Override
  public void tickFeedback(EmptyFeedbackCallback callback, int options) {
    Modules.feedback().synchronize(player(), (player1, target) -> callback.success(player1, null), options);
  }

  @Override
  public void packetTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback) {
    Modules.feedback().synchronize(player(), (player1, target) -> callback.success(player1, null), event);
  }

  @Override
  public void packetTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, int options) {
    Modules.feedback().synchronize(player(), (player1, target) -> callback.success(player1, null), options, event);
  }

  @Override
  public void tracedTickFeedback(EmptyFeedbackCallback callback, FeedbackObserver tracker) {
    Modules.feedback().tracedSingleSynchronize(player(), null, callback, tracker);
  }

  @Override
  public void tracedTickFeedback(EmptyFeedbackCallback callback, FeedbackObserver tracker, int options) {
    Modules.feedback().tracedSingleSynchronize(player(), null, callback, tracker, options);
  }

  @Override
  public void tracedPacketTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, FeedbackObserver tracker) {
    Modules.feedback().tracedSingleSynchronize(player(), null, callback, tracker, 0, event);
  }

  @Override
  public void tracedPacketTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, FeedbackObserver tracker, int options) {
    Modules.feedback().tracedSingleSynchronize(player(), null, callback, tracker, options, event);
  }

  @Override
  public void doubleTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback before, EmptyFeedbackCallback after) {
    Modules.feedback().doubleSynchronize(player(), event, null, before, after);
  }

  @Override
  public void doubleTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, int options) {
    Modules.feedback().doubleSynchronize(player(), event, null, callback, callback2, options);
  }

  @Override
  public void doubleTracedTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, FeedbackObserver tracker) {
    Modules.feedback().tracedDoubleSynchronize(player(), event, null, callback, callback2, tracker, tracker);
  }

  @Override
  public void doubleTracedTickFeedback(ProtocolPacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, FeedbackObserver tracker, int options) {
    Modules.feedback().tracedDoubleSynchronize(player(), event, null, callback, callback2, tracker, tracker, options);
  }

  private void sendStatsUpdate(Player player, int foodLevel, float saturationLevel) {
    float healthScale = (float) (player.isHealthScaled() ? player.getHealth() * player.getHealthScale() / player.getMaxHealth() : player.getHealth());
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_HEALTH);
    packet.getFloat().write(0, healthScale);
    packet.getFloat().write(1, saturationLevel);
    packet.getIntegers().write(0, foodLevel);
    PacketSender.sendServerPacket(player, packet);
  }

  @Override
  public int hashCode() {
    return id().hashCode();
  }

  private IntavePlugin plugin() {
    return IntavePlugin.singletonInstance();
  }
}