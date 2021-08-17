package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationModuloResetHeuristic extends MetaCheckPart<Heuristics, RotationModuloResetHeuristic.RotationModuloResetHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationModuloResetHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationModuloResetHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    AttackMetadata attackData = user.meta().attack();
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
          //dmc20
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "20");
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
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    boolean alternativePositionY = clientData.protocolVersion() == ProtocolMetadata.VER_1_8;
    Raytracing.EntityInteractionRaytrace rayTraceResult = Raytracing.blockIgnoringEntityRaytrace(
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

  public static final class RotationModuloResetHeuristicMeta extends CheckCustomMetadata {
    private boolean roundedRotationLooking;
  }
}