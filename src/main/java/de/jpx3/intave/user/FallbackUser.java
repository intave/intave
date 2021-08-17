package de.jpx3.intave.user;

import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.connect.customclient.CustomClientSupport;
import de.jpx3.intave.connect.shadow.ShadowPacketDataLink;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.placeholder.PlayerContext;
import de.jpx3.intave.placeholder.PlayerIdentificationContext;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.BukkitPermissionCache;
import de.jpx3.intave.world.blockshape.BlankUserOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Relocate
public final class FallbackUser implements User {
  private final Map<Class<? extends CheckCustomMetadata>, CheckCustomMetadata> customMetaPool = new ConcurrentHashMap<>();

  private final MetadataBundle metadata;
  private final BukkitPermissionCache permissionCache;
  private final ComplexColliderProcessor complexColliderProcessor;
  private final SimpleColliderProcessor simpleColliderProcessor;
  private final Map<Pose, HitBoxBoundaries> poseSizes;
  private OCBlockShapeAccess blockShapeAccess;
  private CustomClientSupport customClientSupport = CustomClientSupport.createDefault();
  private final PlayerContext playerPlaceholderContext = new PlayerContext(this);
  private final PlayerIdentificationContext identificationContext;

  FallbackUser() {
    this.metadata = new MetadataBundle(null, this);
    this.permissionCache = new BukkitPermissionCache();
    setBlockShapeAccess(new BlankUserOCBlockShapeAccess());
    this.complexColliderProcessor = Collider.suitableComplexColliderProcessorFor(this);
    this.simpleColliderProcessor = Collider.suitableSimpleColliderProcessorFor(this);
    this.identificationContext = new PlayerIdentificationContext("", new UUID(0,0), InetAddress.getLoopbackAddress());
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
  public PlayerContext playerAttributeContext() {
    return playerPlaceholderContext;
  }

  @Override
  public Map<Pose, HitBoxBoundaries> poseSizes() {
    return poseSizes;
  }

  @Override
  public void clearTypeTranslations() {
  }

  @Override
  public void applyTypeTranslation(Material from, Material to) {
  }

  @Override
  public Map<Material, Material> typeTranslations() {
    return Collections.emptyMap();
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
}
