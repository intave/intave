package de.jpx3.intave.user;

import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.state.BlockStateCaches;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.ExpiringPermissionCache;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.user.storage.PlayerStorage;
import de.jpx3.intave.user.storage.Storage;
import de.jpx3.intave.user.storage.Storages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Relocate
final class FallbackUser implements User {
  private final MetadataBundle metadata;
  private final PermissionCache permissionCache;
  private final Collider collider;
  private final SimpleCollider simpleCollider;
  private final Map<Pose, HitboxSize> poseSizes;
  private final ExtendedBlockStateCache blockStateAccess;
  private final CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createDefault();

  private final UserContext userContext = new UserContext(this);
  private final PlayerContext playerContext = PlayerContext.empty();

  private final PlayerStorage storage = Storages.emptyPlayerStorageFor(UUID.randomUUID());

  FallbackUser() {
    this.metadata = new MetadataBundle(null, this);
    this.permissionCache = new ExpiringPermissionCache(16, TimeUnit.SECONDS);
    this.blockStateAccess = BlockStateCaches.empty();
    this.collider = Colliders.suitableComplexColliderProcessorFor(this);
    this.simpleCollider = Colliders.suitableSimpleColliderProcessorFor(this);
    this.poseSizes = Pose.AT_LEAST_1_8_POSE;
    this.metadata.setup();
  }

  @Override
  public void delayedSetup() {
  }

  @Override
  public void applyNewProtocolVersion() {

  }

  @Override
  public MetadataBundle meta() {
    return this.metadata;
  }

  @Override
  public UUID id() {
    return UUID.randomUUID();
  }

  @Override
  public Object playerHandle() {
    throw UnsupportedFallbackOperationException.INSTANCE;//new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public Object playerConnection() {
    throw UnsupportedFallbackOperationException.INSTANCE;//new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public Player player() {
    throw UnsupportedFallbackOperationException.INSTANCE;//new UnsupportedFallbackOperationException("Can't locate a player here");
  }

  @Override
  public boolean justJoined() {
    return false;
  }

  @Override
  public long joined() {
    return System.currentTimeMillis();
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
    return CustomClientSupportConfig.createDefault();
  }

  @Override
  public void setCustomClientSupport(CustomClientSupportConfig customClientSupportConfig) {
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
  public Storage mainStorage() {
    return storage;
  }

  @Override
  public <T extends Storage> T storageOf(Class<T> storageClass) {
    return storage.storageOf(storageClass);
  }

  @Override
  public ExtendedBlockStateCache blockStates() {
    return blockStateAccess;
  }

  @Override
  public Collider collider() {
    return collider;
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
  public void applyShortAttackStimulus(AttackNerfStrategy strategy, String checkId) {

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
  public int protocolVersion() {
    return meta().protocol().protocolVersion();
  }

  @Override
  public HitboxSize sizeOf(Pose pose) {
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
  public void kick(String reason) {
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
  }

  @Override
  public void refreshSprintState() {
  }

  @Override
  public String toString() {
    return "FallbackUser{}";
  }
}
