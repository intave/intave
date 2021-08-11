package de.jpx3.intave.user;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.connect.customclient.CustomClientSupport;
import de.jpx3.intave.connect.shadow.ShadowPacketDataLink;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.permission.BukkitPermissionCache;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.placeholder.PlayerContext;
import de.jpx3.intave.tools.placeholder.PlayerIdentificationContext;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

@Relocate
public interface User {
  Object playerHandle();

  Object playerConnection();

  Player player();

  MetadataBundle meta();

  void delayedSetup();

  boolean justJoined();

  boolean hasPlayer();

  CheckCustomMetadata checkMetadata(Class<? extends CheckCustomMetadata> classTarget);

  CustomClientSupport customClientSupport();

  BukkitPermissionCache permissionCache();

  void setCustomClientSupport(CustomClientSupport customClientSupport);

  boolean shouldIgnoreNextInboundPacket();

  boolean shouldIgnoreNextOutboundPacket();

  void ignoreNextInboundPacket();

  void ignoreNextOutboundPacket();

  void receiveNextInboundPacketAgain();

  void receiveNextOutboundPacketAgain();

  boolean hasShadow();

  void setShadow(boolean hasShadow);

  ShadowPacketDataLink shadowLinkage();

  void setShadowLinkage(ShadowPacketDataLink shadowRepo);

  void setBlockShapeAccess(OCBlockShapeAccess newBlockShapeAccess);

  OCBlockShapeAccess blockShapeAccess();

  ComplexColliderProcessor complexColliderProcessor();

  SimpleColliderProcessor simpleColliderProcessor();

  PlayerIdentificationContext identificationContext();

  TrustFactor trustFactor();

  void setTrustFactor(TrustFactor trustFactor);

  int trustFactorSetting(String key);

  boolean receives(MessageChannel channel);

  void toggleReceive(MessageChannel channel);

  void setChannelConstraint(MessageChannel channel, Predicate<Player> constraint);

  boolean hasChannelConstraint(MessageChannel channel);

  Predicate<Player> channelPlayerConstraint(MessageChannel channel);

  void applyAttackNerfer(AttackNerfStrategy strategy, String checkId);

  void removeChannelConstraint(MessageChannel channel);

  int latency();

  int latencyJitter();

  PlayerContext playerAttributeContext();

  Map<Pose, HitBoxBoundaries> poseSizes();

  void clearTypeTranslations();

  void applyTypeTranslation(Material from, Material to);

  Map<Material, Material> typeTranslations();

  void noteHardTransactionResponse();

  void synchronizedDisconnect(String reason);

  void unregister();

  void refreshSprintState();
}
