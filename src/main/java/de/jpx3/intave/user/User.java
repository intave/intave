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
import de.jpx3.intave.event.transaction.TransactionFeedbackService;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.event.violation.EntityNoDamageTickChanger;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.permission.BukkitPermissionCache;
import de.jpx3.intave.permission.BukkitPermissionCheck;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.placeholder.PlayerContext;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockshape.BlankUserOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.MultiChunkKeyOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolverFactory;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.processor.ComplexColliderProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static de.jpx3.intave.event.transaction.TransactionFeedbackService.TransactionOptions.SELF_SYNCHRONIZATION;

@Relocate
public final class User {
  private final Map<Class<? extends UserCustomCheckMeta>, UserCustomCheckMeta> customMetaPool = new ConcurrentHashMap<>();

  private final WeakReference<Player> playerRef;
  private final WeakReference<Object> nmsEntity;
  private final UserMeta userMeta;
  private final BukkitPermissionCache permissionCache;
  private final ComplexColliderProcessor colliderProcessor;
  private final boolean hasPlayer;
  private final List<UserMessageChannel> receivingUserChannels = new ArrayList<>();
  private final Map<UserMessageChannel, Predicate<Player>> receiveWhitelist = Maps.newEnumMap(UserMessageChannel.class);
  private final Map<Material, Material> typeTranslations = Maps.newHashMap();
  private OCBlockShapeAccess blockShapeAccess;
  private boolean ignoreNextPacket;
  private boolean ignoreNextOutboundPacket;
  private boolean hasShadow;
  private CustomClientSupport customClientSupport = CustomClientSupport.createDefault();
  private ShadowPacketDataLink shadowRepo = null;
  private final long birthTimestamp = AccessHelper.now();

  private final PlayerContext playerPlaceholderContext = new PlayerContext(this);
  private TrustFactor trustFactor = TrustFactor.DARK_RED;

  private User(Player player) {
    this.playerRef = new WeakReference<>(player);
    this.hasPlayer = player != null;
    this.nmsEntity = new WeakReference<>(hasPlayer ? ReflectiveHandleAccess.handleOf(player) : null);
    this.userMeta = new UserMeta(player, this);
    this.userMeta.setup();
    this.permissionCache = new BukkitPermissionCache();
    if (!hasPlayer) {
      useBlankBlockShapeAccess();
    } else {
      useDefaultBlockShapeAccess();
    }
    this.colliderProcessor = Collider.suitableComplexColliderProcessorFor(this);
    if (hasPlayer) {
      Synchronizer.synchronize(this::setDefaultMessagingChannel);
    }
  }

  public void delayedRefresh() {
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
    if (clientData.clientVersionBehindServerVersion()) {
      string += " (behind server)";
    }
    IntaveLogger.logger().pushPrintln(string);
  }

  public UserMeta meta() {
    return this.userMeta;
  }

  public Object playerHandle() {
    return nmsEntity.get();
  }

  public Player player() {
    Player player = playerRef.get();
    if (player == null) {
      throw new IntaveInternalException("Unable to reference player through service repo: Fallback user lacks reference");
    }
    return player;
  }

  public boolean justJoined() {
    return AccessHelper.now() - birthTimestamp < 5000;
  }

  public boolean hasOnlinePlayer() {
    Player player = playerRef.get();
    return AccessHelper.isOnline(player);
  }

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

  public BukkitPermissionCache permissionCache() {
    return permissionCache;
  }

  public CustomClientSupport customClientSupport() {
    return customClientSupport;
  }

  public void setCustomClientSupport(CustomClientSupport customClientSupport) {
    this.customClientSupport = customClientSupport;
  }

  public boolean shouldIgnoreNextPacket() {
    return ignoreNextPacket;
  }

  public boolean shouldIgnoreNextOutboundPacket() {
    return ignoreNextOutboundPacket;
  }

  public void ignoreNextPacket() {
    this.ignoreNextPacket = true;
  }

  public void ignoreNextOutboundPacket() {
    this.ignoreNextOutboundPacket = true;
  }

  public void receiveNextPacket() {
    this.ignoreNextPacket = false;
  }

  public void receiveNextOutboundPacket() {
    this.ignoreNextOutboundPacket = false;
  }

  public boolean hasShadow() {
    return hasShadow;
  }

  public void setShadow(boolean hasShadow) {
    this.hasShadow = hasShadow;
  }

  public ShadowPacketDataLink shadowRepo() {
    return shadowRepo;
  }

  public void setShadowRepo(ShadowPacketDataLink shadowRepo) {
    this.shadowRepo = shadowRepo;
  }

  public void useBlankBlockShapeAccess() {
    setBlockShapeAccess(new BlankUserOCBlockShapeAccess());
  }

  public void useDefaultBlockShapeAccess() {
    setBlockShapeAccess(new MultiChunkKeyOCBlockShapeAccess(player(), BoundingBoxResolverFactory.resolver()));
  }

  public void setBlockShapeAccess(OCBlockShapeAccess newBlockShapeAccess) {
    if (blockShapeAccess != null) {
      newBlockShapeAccess.applyFrom(blockShapeAccess);
    }
    blockShapeAccess = newBlockShapeAccess;
  }

  public OCBlockShapeAccess blockShapeAccess() {
    return blockShapeAccess;
  }

  public ComplexColliderProcessor colliderProcessor() {
    return colliderProcessor;
  }

  public TrustFactor trustFactor() {
    return trustFactor;
  }

  public void setTrustFactor(TrustFactor trustFactor) {
    this.trustFactor = trustFactor;
  }

  public int trustFactorSetting(String key) {
    return IntavePlugin.singletonInstance().trustFactorService().trustFactorSetting(key, player());
  }

  public void setDefaultMessagingChannel() {
    for (UserMessageChannel channel : UserMessageChannel.values()) {
      if (channel.enabledByDefault && BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
        receivingUserChannels.add(channel);
      }
    }
  }

  public boolean receives(UserMessageChannel channel) {
    if (!hasPlayer) {
      return false;
    }
    if (!BukkitPermissionCheck.permissionCheck(player(), channel.permission())) {
      receivingUserChannels.remove(channel);
      return false;
    }
    return receivingUserChannels.contains(channel);
  }

  public void toggleReceive(UserMessageChannel channel) {
    if (!hasPlayer) {
      return;
    }
    if (receives(channel)) {
      receivingUserChannels.remove(channel);
    } else {
      receivingUserChannels.add(channel);
      removeChannelConstraint(channel);
    }
  }

  public void setChannelConstraint(UserMessageChannel channel, Predicate<Player> constraint) {
    receiveWhitelist.put(channel, constraint);
  }

  public boolean hasChannelConstraint(UserMessageChannel channel) {
    return receiveWhitelist.containsKey(channel);
  }

  public Predicate<Player> channelPlayerConstraint(UserMessageChannel channel) {
    return receiveWhitelist.get(channel);
  }

  public void applyAttackNerfer(AttackNerfStrategy strategy, String checkId) {
    if (trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }
    //noinspection deprecation
    plugin().eventService().combatMitigator().mitigate(this, strategy, checkId);
  }

  public void removeChannelConstraint(UserMessageChannel channel) {
    receiveWhitelist.remove(channel);
  }

  public int latency() {
    return meta().connectionData().latency;
  }

  public int latencyJitter() {
    return meta().connectionData().latencyJitter;
  }

  public PlayerContext userPlaceholderContext() {
    return playerPlaceholderContext;
  }

  public void clearTypeTranslations() {
    typeTranslations.clear();
    blockShapeAccess.identityInvalidate();
  }

  public void applyTypeTranslation(Material from, Material to) {
    typeTranslations.put(from, to);
  }

  public Map<Material, Material> typeTranslations() {
    return typeTranslations;
  }

  public void noteHardTransactionResponse() {
    UserMetaConnectionData connectionData = userMeta.connectionData;
    if (connectionData.hardTransactionResponse++ > 100 && hasPlayer) {
      Player player = player();
      IntaveLogger.logger().error(player.getName() + " has been removed for repeated validation faults");
      UserRepository.userOf(player).synchronizedDisconnect("Timed out");
    }
  }

  public void synchronizedDisconnect(String reason) {
    if (!hasOnlinePlayer()) {
      return;
    }
    IntaveLogger.logger().info("Performed manual disconnect of player " + player().getName() + " with reason \"" + reason + "\"");
    Synchronizer.synchronize(() -> {
      if(player().isOnline()) {
        player().kickPlayer(reason);
      }
    });
  }

  public void unregister() {
    FakePlayer fakePlayer = meta().attackData.fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.despawn();
    }
    EntityNoDamageTickChanger.removeNoDamageTickChangeOf(this);
  }

  public void refreshSprintState() {
    if (!hasPlayer) {
      return;
    }
    Player player = player();
    TransactionFeedbackService feedback = plugin().eventService().feedback();
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

  public static User empty() {
    return new User(null);
  }

  protected static User userFor(Player player) {
    return new User(player);
  }

  public static final class UserMeta {
    private final UserMetaViolationLevelData violationLevelData;
    private final UserMetaMovementData movementData;
    private final UserMetaAbilityData abilityData;
    private final UserMetaPotionData potionData;
    private final UserMetaClientData clientData;
    private final UserMetaConnectionData connectionData;
    private final UserMetaInventoryData inventoryData;
    private final UserMetaAttackData attackData;
    private final UserMetaPunishmentData punishmentData;

    public UserMeta(Player player, User user) {
      this.violationLevelData = new UserMetaViolationLevelData();
      this.clientData = new UserMetaClientData(player, user);
      this.abilityData = new UserMetaAbilityData(player);
      this.potionData = new UserMetaPotionData(player);
      this.inventoryData = new UserMetaInventoryData(player);
      this.connectionData = new UserMetaConnectionData(player);
      this.movementData = new UserMetaMovementData(player, user);
      this.attackData = new UserMetaAttackData(player);
      this.punishmentData = new UserMetaPunishmentData(player);
    }

    public UserMetaViolationLevelData violationLevelData() {
      return violationLevelData;
    }

    public UserMetaMovementData movementData() {
      return movementData;
    }

    public UserMetaInventoryData inventoryData() {
      return inventoryData;
    }

    public UserMetaAbilityData abilityData() {
      return abilityData;
    }

    public UserMetaPotionData potionData() {
      return potionData;
    }

    public UserMetaConnectionData connectionData() {
      return connectionData;
    }

    public UserMetaClientData clientData() {
      return clientData;
    }

    public UserMetaAttackData attackData() {
      return attackData;
    }

    public UserMetaPunishmentData punishmentData() {
      return punishmentData;
    }

    public void setup() {
      movementData.setup();
    }
  }
}