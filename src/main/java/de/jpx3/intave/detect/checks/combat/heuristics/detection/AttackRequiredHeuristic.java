package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackCancelType;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
import static de.jpx3.intave.world.raytrace.Raytracer.distanceOf;

public final class AttackRequiredHeuristic extends IntaveMetaCheckPart<Heuristics, AttackRequiredHeuristic.VentolotlMeta> {
  private final IntavePlugin plugin;

  public AttackRequiredHeuristic(Heuristics parentCheck) {
    super(parentCheck, AttackRequiredHeuristic.VentolotlMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    metaOf(user).didSwing = true;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveAttack(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      metaOf(player).didAttack = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "VEHICLE_MOVE")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaClientData clientData = user.meta().clientData();
    UserMetaAttackData attackData = user.meta().attackData();
    UserMetaMovementData movementData = user.meta().movementData();
    WrappedEntity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || movementData.lastTeleport < 5) {
      return;
    }
    if (clientData.protocolVersion() != PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
      return;
    }

    boolean dead = entity.fakeDead || entity.dead;
    if (dead) {
      return;
    }

    VentolotlMeta meta = metaOf(player);
    if (meta.didSwing && !meta.didAttack) {
      // Raytrace if cursor is upon entity
      boolean cursorUponEntity = cursorUponEntity(player, user, entity);
      if (cursorUponEntity) {
        long timeToLastFlag = AccessHelper.now() - meta.lastFlag;
        if (timeToLastFlag < 10_000 && timeToLastFlag > 50) {
          int vl = (meta.vl += 200) / 200;
          int options = Anomaly.AnomalyOption.DELAY_64s | Anomaly.AnomalyOption.DELAY_128s;
          boolean flag = vl >= 8;
          Confidence confidence = /*flag ? Confidence.PROBABLE :*/ Confidence.NONE;
          Anomaly anomaly = Anomaly.anomalyOf("151", confidence, Anomaly.Type.KILLAURA, "missed attack packet vl:" + vl, options);
          parentCheck().saveAnomaly(player, anomaly);
          if (flag) {
            plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.LIGHT);
          }
        }
        meta.lastFlag = AccessHelper.now();
      }
    }

    if (meta.didSwing && meta.didAttack && meta.vl > 0) {
      meta.vl--;
    }

    meta.expectedAttack = false;
    meta.didAttack = false;
    meta.didSwing = false;
  }

  private boolean cursorUponEntity(
    Player player,
    User user,
    WrappedEntity entity
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    float expandHitbox = 0.05f;
    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    // mouse delay fix
    Raytracer.EntityInteractionRaytrace distanceOfResult = distanceOf(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expandHitbox
    );
    if (distanceOfResult.reach > blockReachDistance) {
      return false;
    }
    if (!hasAlwaysMouseDelayFix) {
      // normal
      distanceOfResult = distanceOf(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch,
        expandHitbox
      );
    }
    return distanceOfResult.reach <= blockReachDistance;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) - 0.005f;
  }

  public final static class VentolotlMeta extends UserCustomCheckMeta {
    public boolean expectedAttack;
    public boolean didAttack, didSwing;
    public long lastFlag;
    public int vl;
  }
}