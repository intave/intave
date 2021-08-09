package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.DELAY_128s;
import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.SUGGEST_MINING;
import static de.jpx3.intave.event.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.event.packet.PacketId.Client.POSITION_LOOK;

public final class RotationAccuracyPitchHeuristic extends MetaCheckPart<Heuristics, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta> {
  public RotationAccuracyPitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAttackData attackData = meta.attackData();
    WrappedEntity attackedEntity = attackData.lastAttackedEntity();
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    if (movementData.lastTeleport < 20) {
      return;
    }

    if (attackedEntity != null && attackedEntity.moving(0.05) && attackData.recentlyAttacked(1000)) {
      float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
      float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());

      int timeAddOnDetection = 40 * 20;

      if (pitchSpeed > 1.0) {
        // Check perfect yaw
        if (distanceToPerfectPitch == 0) {
          heuristicMeta.threshold += timeAddOnDetection;
          int vl = heuristicMeta.threshold / timeAddOnDetection;
          Confidence confidence = vl <= 2 ? Confidence.PROBABLE : Confidence.LIKELY;
          String description = "rotated pitch too precisely (0.0) vl:" + vl + ", conf:" + confidence.level();
          int options = DELAY_128s | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("71", confidence, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
        } else if (heuristicMeta.threshold > 0) {
          heuristicMeta.threshold--;
        }
      }
    }
  }

  public final static class RotationAccuracyHeuristicMeta extends UserCustomCheckMeta {
    public int threshold;
  }
}