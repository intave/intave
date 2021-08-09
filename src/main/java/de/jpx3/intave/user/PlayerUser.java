package de.jpx3.intave.user;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.connect.customclient.CustomClientSupport;
import de.jpx3.intave.connect.shadow.ShadowPacketDataLink;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.event.feedback.FeedbackService;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.event.violation.EntityNoDamageTickChanger;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.permission.BukkitPermissionCache;
import de.jpx3.intave.permission.BukkitPermissionCheck;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.placeholder.PlayerContext;
import de.jpx3.intave.tools.placeholder.PlayerIdentificationContext;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockshape.MultiChunkKeyOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolverFactory;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static de.jpx3.intave.event.feedback.FeedbackService.TransactionOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.user.UserMetaClientData.VER_1_13;
import static de.jpx3.intave.user.UserMetaClientData.VER_1_9;

@Relocate
public final class PlayerUser implements User {
  private final Map<Class<? extends UserCustomCheckMeta>, UserCustomCheckMeta> customMetaPool = new ConcurrentHashMap<>();

  private final WeakReference<Player> player;
  private final WeakReference<Object> playerHandle;
  private final WeakReference<Object> playerConnection;
  private final UserMeta userMeta;
  private final BukkitPermissionCache permissionCache;
  private final ComplexColliderProcessor complexColliderProcessor;
  private final SimpleColliderProcessor simpleColliderProcessor;
  private final List<UserMessageChannel> receivingUserChannels = new ArrayList<>();
  private final Map<UserMessageChannel, Predicate<Player>> receiveWhitelist = Maps.newEnumMap(UserMessageChannel.class);
  private final Map<Material, Material> typeTranslations = Maps.newHashMap();
  private final Map<Pose, HitBoxBoundaries> poseSizes;
  private OCBlockShapeAccess blockShapeAccess;
  private boolean ignoreNextPacket;
  private boolean ignoreNextOutboundPacket;
  private boolean hasShadow;
  private CustomClientSupport customClientSupport = CustomClientSupport.createDefault();
  private ShadowPacketDataLink shadowRepo = null;
  private final long birthTimestamp = AccessHelper.now();

  private final PlayerContext playerPlaceholderContext = new PlayerContext(this);
  private final PlayerIdentificationContext identificationContext;
  private TrustFactor trustFactor = TrustFactor.DARK_RED;

  PlayerUser(Player player) {
    this.player = new WeakReference<>(player);
    this.playerHandle = new WeakReference<>(ReflectiveHandleAccess.handleOf(player));
    this.playerConnection = new WeakReference<>(ReflectiveHandleAccess.playerConnectionOf(player));
    this.userMeta = new UserMeta(player, this);
    this.permissionCache = new BukkitPermissionCache();
    setBlockShapeAccess(new MultiChunkKeyOCBlockShapeAccess(player(), BoundingBoxResolverFactory.resolver()));
    this.complexColliderProcessor = Collider.suitableComplexColliderProcessorFor(this);
    this.simpleColliderProcessor = Collider.suitableSimpleColliderProcessorFor(this);
    Synchronizer.synchronize(this::setDefaultMessagingChannel);
    this.identificationContext = new PlayerIdentificationContext(player.getName(), player.getUniqueId(), player.getAddress().getAddress());
    int version = userMeta.clientData().protocolVersion();
    if (version >= VER_1_13) {
      this.poseSizes = Pose.AT_LEAST_1_13_POSE;
    } else if (version >= VER_1_9) {
      this.poseSizes = Pose.AT_LEAST_1_9_POSE;
    } else {
      this.poseSizes = Pose.AT_LEAST_1_8_POSE;
    }
    this.userMeta.setup();
  }

  @Override
  public void delayedSetup() {
    Player player = player();
    UserMetaClientData clientData = meta().clientData();
    clientData.refresh(player);
    outputVersionJoinInfo();
    BlockTypeAccess.setupTranslationsFor(this);
  }

  private void outputVersionJoinInfo() {
    Player player = player();
    UserMetaClientData clientData = meta().clientData();
    String string = player.getName() + " joined with version " + clientData.versionString() + " ";
    string += "(" + clientData.protocolVersion() + ")";
    if (clientData.clientVersionOlderThanServerVersion()) {
      string += " (behind server)";
    }
    IntaveLogger.logger().pushPrintln(string);
  }

  @Override
  public UserMeta meta() {
    return this.userMeta;
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
    return AccessHelper.now() - birthTimestamp < 5000;
  }

  @Override
  public boolean hasPlayer() {
    Player player = this.player.get();
    return AccessHelper.isOnline(player);
  }

  @Override
  public UserCustomCheckMeta customMeta(Class<? extends UserCustomCheckMeta> classTarget) {
    UserCustomCheckMeta userCustomCheckMeta = customMetaPool.get(classTarget);
    if (userCustomCheckMeta == null) {
      try {
        customMetaPool.put(classTarget, userCustomCheckMeta = classTarget.newInstance());
      } catch (InstantiationException | IllegalAccessException exception) {
        exception.printStackTrace();
      }
    }
    return userCustomCheckMeta;
  }

  @Override
  public BukkitPermissionCache permissionCache() {
    return permissionCache;
  }

  @Override
  public CustomClientSupport customClientSupport() {
    return customClientSupport;
  }

  @Override
  public void setCustomClientSupport(CustomClientSupport customClientSupport) {
    this.customClientSupport = customClientSupport;
  }

  @Override
  public boolean shouldIgnoreNextPacket() {
    return ignoreNextPacket;
  }

  @Override
  public boolean shouldIgnoreNextOutboundPacket() {
    return ignoreNextOutboundPacket;
  }

  @Override
  public void ignoreNextPacket() {
    this.ignoreNextPacket = true;
  }

  @Override
  public void ignoreNextOutboundPacket() {
    this.ignoreNextOutboundPacket = true;
  }

  @Override
  public void receiveNextPacket() {
    this.ignoreNextPacket = false;
  }

  @Override
  public void receiveNextOutboundPacket() {
    this.ignoreNextOutboundPacket = false;
  }

  @Override
  public boolean hasShadow() {
    return hasShadow;
  }

  @Override
  public void setShadow(boolean hasShadow) {
    this.hasShadow = hasShadow;
  }

  @Override
  public ShadowPacketDataLink shadowLinkage() {
    return shadowRepo;
  }

  @Override
  public void setShadowLinkage(ShadowPacketDataLink shadowRepo) {
    this.shadowRepo = shadowRepo;
  }

  @Override
  public void setBlockShapeAccess(OCBlockShapeAccess newBlockShapeAccess) {
    if (blockShapeAccess != null) {
      newBlockShapeAccess.applyFrom(blockShapeAccess);
    }
    blockShapeAccess = newBlockShapeAccess;
  }

  @Override
  public OCBlockShapeAccess blockShapeAccess() {
    return blockShapeAccess;
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
  public PlayerIdentificationContext identificationContext() {
    return identificationContext;
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
    return IntavePlugin.singletonInstance().trustFactorService().trustFactorSetting(key, player());
  }

  @Override
  public void setDefaultMessagingChannel() {
    for (UserMessageChannel channel : UserMessageChannel.values()) {
      if (channel.enabledByDefault && BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
        toggleReceive(channel);
      }
    }
  }

  @Override
  public boolean receives(UserMessageChannel channel) {
    if (!BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
      receivingUserChannels.remove(channel);
      return false;
    }
    return receivingUserChannels.contains(channel);
  }

  @Override
  public void toggleReceive(UserMessageChannel channel) {
    boolean remove = receives(channel);
    if (remove) {
      receivingUserChannels.remove(channel);
    } else {
      receivingUserChannels.add(channel);
      removeChannelConstraint(channel);
    }
    UserMessageSubscriptions.setChannelActivation(player(), channel, !remove);
  }

  @Override
  public void setChannelConstraint(UserMessageChannel channel, Predicate<Player> constraint) {
    receiveWhitelist.put(channel, constraint);
  }

  @Override
  public boolean hasChannelConstraint(UserMessageChannel channel) {
    return receiveWhitelist.containsKey(channel);
  }

  @Override
  public Predicate<Player> channelPlayerConstraint(UserMessageChannel channel) {
    return receiveWhitelist.get(channel);
  }

  @Override
  public void applyAttackNerfer(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    //noinspection deprecation
    plugin().eventService().combatMitigator().mitigate(this, strategy, checkId);
  }

  @Override
  public void removeChannelConstraint(UserMessageChannel channel) {
    receiveWhitelist.remove(channel);
  }

  @Override
  public int latency() {
    return meta().connectionData().latency;
  }

  @Override
  public int latencyJitter() {
    return meta().connectionData().latencyJitter;
  }

  @Override
  public PlayerContext playerAttributeContext() {
    return playerPlaceholderContext;
  }

  @Override
  public Map<Pose, HitBoxBoundaries> poseSizes() {
    return poseSizes;
  }

  @Override
  public void clearTypeTranslations() {
    typeTranslations.clear();
    blockShapeAccess.identityInvalidate();
  }

  @Override
  public void applyTypeTranslation(Material from, Material to) {
    typeTranslations.put(from, to);
  }

  @Override
  public Map<Material, Material> typeTranslations() {
    return typeTranslations;
  }

  @Override
  public void noteHardTransactionResponse() {
    UserMetaConnectionData connectionData = userMeta.connectionData();
    if (connectionData.hardTransactionResponse++ > 100) {
      Player player = player();
      IntaveLogger.logger().error(player.getName() + " has been removed for repeated feedback faults");
      synchronizedDisconnect("Timed out");
    }
  }

  @Override
  public void synchronizedDisconnect(String reason) {
    IntaveLogger.logger().info("Queuing manual disconnect of player " + player().getName() + " for \"" + reason + "\"");
    IntaveLogger.logger().info("This measure is a security-constraint necessity, but feel free to contact us if this happens too often");
    Synchronizer.synchronize(() -> {
      if(player().isOnline()) {
        player().kickPlayer(reason);
      }
    });
  }

  @Override
  public void unregister() {
    FakePlayer fakePlayer = meta().attackData().fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.remove();
    }
    EntityNoDamageTickChanger.removeNoDamageTickChangeOf(this);
    for (UserMessageChannel value : UserMessageChannel.values()) {
      UserMessageSubscriptions.setChannelActivation(player(), value, false);
    }
  }

  @Override
  public void refreshSprintState() {
    Player player = player();
    FeedbackService feedback = plugin().eventService().feedback();
    feedback.singleSynchronize(player, null, (player1, target) -> {
      sendStatsUpdate(player, 0, 0);
      feedback.singleSynchronize(player, null, (player2, target1) -> {
        feedback.singleSynchronize(player, null, (player3, target2) -> {
          sendStatsUpdate(player, player.getFoodLevel(), player.getSaturation());
        }, SELF_SYNCHRONIZATION);
      }, SELF_SYNCHRONIZATION);
    }, SELF_SYNCHRONIZATION);
  }

  private void sendStatsUpdate(Player player, int foodLevel, float saturationLevel) {
    float healthScale = (float)(player.isHealthScaled() ? player.getHealth() * player.getHealthScale() / player.getMaxHealth() : player.getHealth());
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_HEALTH);
    packet.getFloat().write(0, healthScale);
    packet.getFloat().write(1, saturationLevel);
    packet.getIntegers().write(0, foodLevel);
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private IntavePlugin plugin() {
    return IntavePlugin.singletonInstance();
  }
}