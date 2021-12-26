package de.jpx3.intave.user;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.block.state.MultiChunkKeyBlockStateAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.connect.shadow.ShadowPacketDataLink;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackSender;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.mitigate.HurttimeModifier;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.collider.Collider;
import de.jpx3.intave.player.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.player.collider.simple.SimpleColliderProcessor;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.user.permission.ExpiringPermissionCache;
import de.jpx3.intave.user.permission.PermissionCache;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static de.jpx3.intave.module.feedback.TransactionOptions.SELF_SYNCHRONIZATION;

@Relocate
final class PlayerUser implements User {
  private final Map<Class<? extends CheckCustomMetadata>, CheckCustomMetadata> customMetaPool = new ConcurrentHashMap<>();

  private final WeakReference<Player> player;
  private final WeakReference<Object> playerHandle;
  private final WeakReference<Object> playerConnection;
  private final MetadataBundle metadata;
  private final PermissionCache permissionCache;
  private final ComplexColliderProcessor complexColliderProcessor;
  private final SimpleColliderProcessor simpleColliderProcessor;
  private final List<MessageChannel> receivingUserChannels = new ArrayList<>();
  private final Map<MessageChannel, Predicate<Player>> channelConstraints = Maps.newEnumMap(MessageChannel.class);
  private final Map<Material, Material> typeTranslations = Maps.newHashMap();
  private final Map<Pose, HitboxSize> poseSizes;
  private final BlockStateAccess blockStateAccess;
  private boolean ignoreNextInboundPacket;
  private boolean ignoreNextOutboundPacket;
  private boolean hasShadow;
  private CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createDefault();
  private ShadowPacketDataLink shadowRepo = null;
  private final long birthTimestamp = System.currentTimeMillis();

  private final UserContext playerPlaceholderContext = new UserContext(this);
  private final PlayerContext playerContext;
  private TrustFactor trustFactor = TrustFactor.DARK_RED;

  PlayerUser(Player player) {
    this.player = new WeakReference<>(player);
    this.playerHandle = new WeakReference<>(ReflectiveHandleAccess.handleOf(player));
    this.playerConnection = new WeakReference<>(ReflectiveHandleAccess.playerConnectionOf(player));
    this.metadata = new MetadataBundle(player, this);
    this.permissionCache = new ExpiringPermissionCache(16, TimeUnit.SECONDS);
    this.blockStateAccess = MultiChunkKeyBlockStateAccess.forPlayer(player);
    this.complexColliderProcessor = Collider.suitableComplexColliderProcessorFor(this);
    this.simpleColliderProcessor = Collider.suitableSimpleColliderProcessorFor(this);
    Synchronizer.synchronize(this::setDefaultMessagingChannel);
    this.playerContext = PlayerContext.of(player);
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
    outputVersionJoinInfo();
    BlockTypeAccess.setupTranslationsFor(this);
  }

  private void outputVersionJoinInfo() {
    Player player = player();
    ProtocolMetadata clientData = meta().protocol();
    String string = player.getName() + " joined with version " + clientData.versionString() + "/" + clientData.protocolVersion();
    if (clientData.clientVersionOlderThanServerVersion()) {
      string += " (behind)";
    }
    IntaveLogger.logger().printLine(string);
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
      throw new IntaveInternalException("Unable to reference player through service repo: Fallback user lacks reference");
    }
    return player;
  }

  @Override
  public boolean justJoined() {
    return System.currentTimeMillis() - birthTimestamp < 5000;
  }

  @Override
  public boolean hasPlayer() {
    Player player = this.player.get();
    return isOnline(player);
  }

  private boolean isOnline(OfflinePlayer player) {
    return player != null && (player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != null);
  }

  @Override
  public CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> classTarget) {
    return customMetaPool.computeIfAbsent(classTarget, aClass -> {
      try {
        return aClass.newInstance();
      } catch (InstantiationException | IllegalAccessException exception) {
        throw new IllegalStateException(exception);
      }
    });
  }

  @Override
  public PermissionCache permissionCache() {
    return permissionCache;
  }

  @Override
  public CustomClientSupportConfig customClientSupport() {
    return customClientSupportConfig;
  }

  @Override
  public void setCustomClientSupport(CustomClientSupportConfig customClientSupportConfig) {
    this.customClientSupportConfig = customClientSupportConfig;
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
  @Deprecated
  public boolean hasShadow() {
    return hasShadow;
  }

  @Override
  @Deprecated
  public void setShadow(boolean hasShadow) {
    this.hasShadow = hasShadow;
  }

  @Override
  @Deprecated
  public ShadowPacketDataLink shadowLinkage() {
    return shadowRepo;
  }

  @Override
  @Deprecated
  public void setShadowLinkage(ShadowPacketDataLink shadowRepo) {
    this.shadowRepo = shadowRepo;
  }

  @Override
  public BlockStateAccess blockStates() {
    return blockStateAccess;
  }

  @Override
  public ComplexColliderProcessor complexColliderProcessor() {
    return complexColliderProcessor;
  }

  @Override
  public SimpleColliderProcessor simpleColliderProcessor() {
    return simpleColliderProcessor;
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
    if (!BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
      receivingUserChannels.remove(channel);
      return false;
    }
    return receivingUserChannels.contains(channel);
  }

  @Override
  public void toggleReceive(MessageChannel channel) {
    boolean remove = receives(channel);
    if (remove) {
      receivingUserChannels.remove(channel);
    } else {
      receivingUserChannels.add(channel);
    }
    MessageChannelSubscriptions.setChannelActivation(player(), channel, !remove);
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
  public void applyAttackNerfer(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    Modules.mitigate().combat().mitigate(this, strategy, checkId);
  }

  @Override
  public void removeChannelConstraint(MessageChannel channel) {
    channelConstraints.remove(channel);
  }

  @Override
  public int latency() {
    return meta().connection().latency;
  }

  @Override
  public int latencyJitter() {
    return meta().connection().latencyJitter;
  }

  @Override
  public UserContext userContext() {
    return playerPlaceholderContext;
  }

  @Override
  public HitboxSize sizeOf(Pose pose) {
    return poseSizes.get(pose);
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

  @Override
  public void noteHardTransactionResponse() {
    ConnectionMetadata connectionData = metadata.connection();
    if (connectionData.hardTransactionResponse++ > 100) {
      Player player = player();
      IntaveLogger.logger().error(player.getName() + " has been removed for repeated feedback faults");
      synchronizedDisconnect("Timed out");
    }
  }

  private boolean disconnectQueued = false;

  @Override
  public synchronized void synchronizedDisconnect(String reason) {
    if (disconnectQueued) {
      return;
    }
    disconnectQueued = true;
    IntaveLogger.logger().info("Queuing manual disconnect of player " + player().getName() + " for \"" + reason + "\"");
    IntaveLogger.logger().info("This measure is a security-constraint necessity, but feel free to contact us if this happens too often");
    Synchronizer.synchronize(() -> {
      if (player().isOnline()) {
        player().kickPlayer(reason);
      }
    });
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
  public void refreshSprintState() {
    Player player = player();
    FeedbackSender feedbackSender = Modules.feedback();
    feedbackSender.synchronize(player, UserRepository.userOf(player), (player1, user) -> {
      sendStatsUpdate(player, 0, 0);
      feedbackSender.synchronize(player, null, (player2, target1) -> {
        feedbackSender.synchronize(player, null, (player3, target2) -> {
          sendStatsUpdate(player, player.getFoodLevel(), player.getSaturation());
        }, SELF_SYNCHRONIZATION);
      }, SELF_SYNCHRONIZATION);
    }, SELF_SYNCHRONIZATION);
  }

  private void sendStatsUpdate(Player player, int foodLevel, float saturationLevel) {
    float healthScale = (float) (player.isHealthScaled() ? player.getHealth() * player.getHealthScale() / player.getMaxHealth() : player.getHealth());
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_HEALTH);
    packet.getFloat().write(0, healthScale);
    packet.getFloat().write(1, saturationLevel);
    packet.getIntegers().write(0, foodLevel);
    PacketSender.sendServerPacket(player, packet);
  }

  private IntavePlugin plugin() {
    return IntavePlugin.singletonInstance();
  }

  @Override
  public String toString() {
    return "PlayerUser{" +
      "player=" + player +
      ", birthTimestamp=" + birthTimestamp +
      '}';
  }
}