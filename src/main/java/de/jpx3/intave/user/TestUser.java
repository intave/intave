package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.connect.customclient.CustomClientSupportConfig;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.module.actionbar.DisplayType;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import de.jpx3.intave.module.violation.placeholder.UserContext;
import de.jpx3.intave.player.collider.complex.Collider;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.permission.PermissionCache;
import de.jpx3.intave.user.storage.PlayerStorage;
import de.jpx3.intave.user.storage.Storage;
import de.jpx3.intave.user.storage.Storages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Relocate
final class TestUser implements User {
  private final Map<Class<? extends CheckCustomMetadata>, CheckCustomMetadata> metadataPool = new ConcurrentHashMap<>();
  private final Player player;
  private PlayerStorage storage;
  private final MetadataBundle meta;
  private final Map<Pose, HitboxSize> poseSizes;
  private final Function<String, Object> callback;

  TestUser(Player player, Function<String, Object> callback) {
    this.player = player;
    UUID id = player.getUniqueId();
    if (id != null) {
      this.storage = Storages.emptyPlayerStorageFor(id);
    }
    this.callback = callback;
    this.meta = new MetadataBundle(null, this);
    Integer protocolVersion = (Integer) callback.apply("protocolVersion");
    if (protocolVersion == null) {
      protocolVersion = 0;
    }
    this.poseSizes = Pose.poseSizesByVersion(protocolVersion);
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
    return meta;
  }

  @Override
  public void delayedSetup() {

  }

  @Override
  public void applyNewProtocolVersion() {

  }

  @Override
  public boolean justJoined() {
    return callback.apply("justJoined").equals(true);
  }

  @Override
  public long joined() {
    return (long) callback.apply("joined");
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
    return (boolean) callback.apply("shouldIgnoreNextInboundPacket");
  }

  @Override
  public boolean shouldIgnoreNextOutboundPacket() {
    return (boolean) callback.apply("shouldIgnoreNextOutboundPacket");
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
  public void onStorageReady(Consumer<? super Storage> consumer) {

  }

  @Override
  public void notifyStorageLoadSubscribers() {

  }

  @Override
  public <T extends Storage> T storageOf(Class<T> storageClass) {
    return storage.storageOf(storageClass);
  }

  @Override
  public ExtendedBlockStateCache blockStates() {
    return (ExtendedBlockStateCache) callback.apply("blockStates");
  }

  @Override
  public Collider collider() {
    return (Collider) callback.apply("collider");
  }

  @Override
  public SimpleCollider simplifiedCollider() {
    return (SimpleCollider) callback.apply("simplifiedCollider");
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
    return (TrustFactor) callback.apply("trustFactor");
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
  public void nerf(AttackNerfStrategy strategy, String checkId) {

  }

  @Override
  public void nerfOnce(AttackNerfStrategy strategy, String checkId) {

  }

  @Override
  public void nerfPermanently(AttackNerfStrategy strategy, String checkId) {

  }

  @Override
  public int latency() {
    return (int) callback.apply("latency");
  }

  @Override
  public int latencyJitter() {
    return (int) callback.apply("latencyJitter");
  }

  @Override
  public int protocolVersion() {
    return (int) callback.apply("protocolVersion");
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
  public String actionDisplayOf(DisplayType type) {
    return null;
  }

  @Override
  public String currentActionDisplay() {
    return null;
  }

  @Override
  public void setCurrentActionDisplay(String currentActionDisplay) {

  }

  @Override
  public String overrideActionDisplay() {
    return null;
  }

  @Override
  public void setOverrideActionDisplay(String overrideActionDisplay) {

  }

  @Override
  public void pushActionDisplayToSubscribers(DisplayType type, String message) {

  }

  @Override
  public UUID actionTarget() {
    return null;
  }

  @Override
  public void setActionTarget(UUID target) {

  }

  @Override
  public boolean anyActionSubscriptions() {
    return false;
  }

  @Override
  public void addActionReceiver(UUID subscriber, DisplayType type) {

  }

  @Override
  public void removeActionSubscription(UUID id) {

  }

  @Override
  public void noteFeedbackFault() {

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
  public void refreshSprintState(Consumer<Void> callback) {

  }

  @Override
  public void tickFeedback(EmptyFeedbackCallback callback) {

  }

  @Override
  public void tracedTickFeedback(EmptyFeedbackCallback callback, FeedbackObserver tracker) {
    
  }

  @Override
  public void doubleTickFeedback(PacketEvent event, EmptyFeedbackCallback before, EmptyFeedbackCallback after) {

  }

  @Override
  public void doubleTracedTickFeedback(PacketEvent event, EmptyFeedbackCallback callback, EmptyFeedbackCallback callback2, FeedbackObserver tracker) {

  }
}
