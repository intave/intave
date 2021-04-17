package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
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
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.RotationMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class RotationModuloResetHeuristic extends IntaveMetaCheckPart<Heuristics, RotationModuloResetHeuristic.RotationModuloResetHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationModuloResetHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationModuloResetHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }
  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaAttackData attackData = user.meta().attackData();
    RotationModuloResetHeuristicMeta heuristicMeta = metaOf(user);

    WrappedEntity attackedEntity = attackData.lastAttackedEntity();
    if (attackedEntity == null || attackData.recentlySwitchedEntity(5000) || movementData.lastTeleport < 100) {
      return;
    }

    float rotationYaw = movementData.rotationYaw;
    float lastRotationYaw = movementData.lastRotationYaw;

    /*
    1: Check stage
     */
    if (heuristicMeta.roundedRotationLooking) {
      if (entityInLineOfSight(user)) {
        float penaltyYaw = movementData.lastRotationYaw;
        if (penaltyYaw != 0) {
          String description = "possible rotation modulo clamp";
          int options = Anomaly.AnomalyOption.LIMIT_4 | Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("101", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.MEDIUM);
        }
      }
      heuristicMeta.roundedRotationLooking = false;
      return;
    }

    /*
    2: Prepare for stage 1
     */
    if (attackData.recentlyAttacked(1000) && attackData.lastReach() > 1.0) {
      float receivedDistance = Math.abs(rotationYaw - lastRotationYaw);
      boolean roundingConditions = Math.abs(rotationYaw) <= 360 && Math.abs(lastRotationYaw) <= 360;
      boolean suspiciousYaw = roundingConditions && receivedDistance > 100;

      if (suspiciousYaw && entityInLineOfSight(user)) {
        heuristicMeta.roundedRotationLooking = true;
      }
    }
  }

  private boolean entityInLineOfSight(User user) {
    User.UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
    Raytracer.EntityInteractionRaytrace rayTraceResult = Raytracer.distanceOf(
      user.player(),
      attackData.lastAttackedEntity(),
      alternativePositionY,
      movementData.lastPositionX,
      movementData.lastPositionY,
      movementData.lastPositionZ,
      movementData.rotationYaw,
      movementData.rotationPitch,
      0.1f
    );
    return rayTraceResult.reach != 10;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION")
    }
  )
  public void receiveMovementPacket2(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    RotationModuloResetHeuristicMeta meta = metaOf(user);

    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);

    if(movementData.lastTeleport > 5) {
      if (ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
        return;
      }
      if (meta.lastLastYawMotion < 7 && meta.lastYawMotion > 50 && yawMotion < 3) {
        // lastLastYawMotion < 7 && lastYawMotion > 50 && yawMotion < 7 && lastSwing <= 3
        String description = "rotation hop (llMotion:"
            + MathHelper.formatDouble(meta.lastLastYawMotion, 2)
            + " lMotion:" +  MathHelper.formatDouble(meta.lastYawMotion, 2)
            + " curMotion:" + MathHelper.formatDouble(yawMotion, 2)
            + " swing:" + Math.max(meta.lastSwing,99)
            + " attack:" + Math.max(meta.lastAttack, 99)
            + " tp:" + Math.max(movementData.lastTeleport, 99)
            + ")";
        int options = Anomaly.AnomalyOption.DELAY_128s;
        Anomaly anomaly = Anomaly.anomalyOf("102", Confidence.NONE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
    }
    if(movementData.lastTeleport != 0) {
      prepareNextTick(meta, yawMotion);
    }
  }

  private void prepareNextTick(RotationModuloResetHeuristicMeta meta, double yawMotion) {
    meta.lastLastYawMotion = meta.lastYawMotion;
    meta.lastYawMotion = yawMotion;
    meta.lastSwing++;
    meta.lastAttack++;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ARM_ANIMATION")
    }
  )
  public void receiveSwingPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationModuloResetHeuristicMeta meta = metaOf(user);

    meta.lastSwing = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveAttackPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationModuloResetHeuristicMeta meta = metaOf(user);

    EnumWrappers.EntityUseAction entityUseAction = event.getPacket().getEntityUseActions().read(0);

    if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  public static final class RotationModuloResetHeuristicMeta extends UserCustomCheckMeta {
    private int lastSwing;
    private int lastAttack;
    private boolean roundedRotationLooking;
    private double lastYawMotion;
    private double lastLastYawMotion;
  }
}