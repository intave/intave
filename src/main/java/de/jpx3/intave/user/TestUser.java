package de.jpx3.intave.user;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.user.storage.Storage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Relocate
final class TestUser implements User {
  private final Map<Class<? extends CheckCustomMetadata>, CheckCustomMetadata> metadataPool = new ConcurrentHashMap<>();
  private final Player player;
  private final int protocolVersion;

  TestUser(Player player, int protocolVersion) {
    this.player = player;
    this.protocolVersion = protocolVersion;
  }

  @Override
  public UUID id() {
    return player.getUniqueId();
  }

  @Override
  public Object playerHandle() {
    return null;
  }

  @Override
  public Object playerConnection() {
    return null;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public MetadataBundle meta() {
    return null;
  }

  @Override
  public void delayedSetup() {

  }

  @Override
  public void applyNewProtocolVersion() {

  }

  @Override
  public boolean justJoined() {
    return false;
  }

  @Override
  public long joined() {
    return 0;
  }

  @Override
  public boolean hasPlayer() {
    return true;
  }

  @Override
  public CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> metaClass) {
    return metadataPool.computeIfAbsent(metaClass, initializeMe -> {
      try {
        return initializeMe.newInstance();
      } catch (RuntimeException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    });
  }

  @Override
  public CustomClientSupportConfig customClientSupport() {
    return null;
  }

  @Override
  public void setCustomClientSupport(CustomClientSupportConfig customClientSupportConfig) {

  }

  @Override
  public PermissionCache permissionCache() {
    return null;
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
    return null;
  }

  @Override
  public <T extends Storage> T storageOf(Class<T> storageClass) {
    return null;
  }

  @Override
  public ExtendedBlockStateCache blockStates() {
    return null;
  }

  @Override
  public Collider collider() {
    return null;
  }

  @Override
  public SimpleCollider simplifiedCollider() {
    return null;
  }

  @Override
  public PlayerContext playerContext() {
    return null;
  }

  @Override
  public UserContext userContext() {
    return null;
  }

  @Override
  public TrustFactor trustFactor() {
    return null;
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
    return null;
  }

  @Override
  public void removeChannelConstraint(MessageChannel channel) {

  }

  @Override
  public void applyAttackNerfer(AttackNerfStrategy strategy, String checkId) {

  }

  @Override
  public void applyShortAttackStimulus(AttackNerfStrategy strategy, String checkId) {

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
    return protocolVersion;
  }

  @Override
  public HitboxSize sizeOf(Pose pose) {
    return null;
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

  }

  @Override
  public void refreshSprintState() {

  }
}
