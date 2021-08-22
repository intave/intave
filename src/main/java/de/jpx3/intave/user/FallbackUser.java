package de.jpx3.intave.user;

import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.connect.shadow.ShadowPacketDataLink;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.event.mitigate.AttackNerfStrategy;
import de.jpx3.intave.event.mitigate.placeholder.PlayerContext;
import de.jpx3.intave.event.mitigate.placeholder.UserContext;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.ExpiringPermissionCache;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.world.blockshape.BlankUserOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Relocate
final class FallbackUser implements User {
  private final MetadataBundle metadata;
  private final PermissionCache permissionCache;
  private final ComplexColliderProcessor complexColliderProcessor;
  private final SimpleColliderProcessor simpleColliderProcessor;
  private final Map<Pose, HitBoxBoundaries> poseSizes;
  private OCBlockShapeAccess blockShapeAccess;
  private CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createDefault();

  private final UserContext userContext = new UserContext(this);
  private final PlayerContext playerContext = PlayerContext.empty();

  FallbackUser() {
    this.metadata = new MetadataBundle(null, this);
    this.permissionCache = new ExpiringPermissionCache(16, TimeUnit.SECONDS);
    this.blockShapeAccess = new BlankUserOCBlockShapeAccess();
    this.complexColliderProcessor = Collider.suitableComplexColliderProcessorFor(this);
    this.simpleColliderProcessor = Collider.suitableSimpleColliderProcessorFor(this);
    this.poseSizes = Pose.AT_LEAST_1_8_POSE;
    this.metadata.setup();
  }

  @Override
  public void delayedSetup() {
  }

  @Override
  public MetadataBundle meta() {
    return this.metadata;
  }

  @Override
  public Object playerHandle() {
    throw new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public Object playerConnection() {
    throw new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public Player player() {
    throw new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public boolean justJoined() {
    return false;
  }

  @Override
  public boolean hasPlayer() {
    return false;
  }

  @Override
  public CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> classTarget) {
    try {
      return classTarget.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
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
    return false;
  }

  @Override
  public boolean shouldIgnoreNextOutboundPacket() {
    return false;
  }

  @Override
  public void ignoreNextInboundPacket() {
  }

  @Override
  public void ignoreNextOutboundPacket() {
  }

  @Override
  public void receiveNextInboundPacketAgain() {
  }

  @Override
  public void receiveNextOutboundPacketAgain() {
  }

  @Override
  public boolean hasShadow() {
    return false;
  }

  @Override
  public void setShadow(boolean hasShadow) {
  }

  @Override
  public ShadowPacketDataLink shadowLinkage() {
    return null;
  }

  @Override
  public void setShadowLinkage(ShadowPacketDataLink shadowRepo) {
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
  public PlayerContext playerContext() {
    return playerContext;
  }

  @Override
  public UserContext userContext() {
    return userContext;
  }

  @Override
  public TrustFactor trustFactor() {
    return TrustFactor.DARK_RED;
  }

  @Override
  public void setTrustFactor(TrustFactor trustFactor) {
  }

  @Override
  public int trustFactorSetting(String key) {
    return 0;
  }

  @Override
  public boolean receives(MessageChannel channel) {
    return false;
  }

  @Override
  public void toggleReceive(MessageChannel channel) {
  }

  @Override
  public void setChannelConstraint(MessageChannel channel, Predicate<Player> constraint) {
  }

  @Override
  public boolean hasChannelConstraint(MessageChannel channel) {
    return false;
  }

  @Override
  public Predicate<Player> channelPlayerConstraint(MessageChannel channel) {
    return player -> false;
  }

  @Override
  public void applyAttackNerfer(AttackNerfStrategy strategy, String checkId) {
  }

  @Override
  public void removeChannelConstraint(MessageChannel channel) {
  }

  @Override
  public int latency() {
    return 0;
  }

  @Override
  public int latencyJitter() {
    return 0;
  }

  @Override
  public HitBoxBoundaries sizeOf(Pose pose) {
    return poseSizes.get(pose);
  }

  @Override
  public void clearTypeTranslations() {
  }

  @Override
  public void applyTypeTranslation(Material from, Material to) {
  }

  @Override
  public Material typeTranslationOf(Material source) {
    return null;
  }

  @Override
  public void noteHardTransactionResponse() {
  }

  @Override
  public void synchronizedDisconnect(String reason) {
  }

  @Override
  public void unregister() {
    FakePlayer fakePlayer = meta().attack().fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.remove();
    }
  }

  @Override
  public void refreshSprintState() {
  }

  @Override
  public String toString() {
    return "FallbackUser{}";
  }
}
