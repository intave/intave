package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

public final class RotationAccuracyPitchHeuristic extends IntaveMetaCheckPart<Heuristics, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta> {
  public RotationAccuracyPitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAttackData attackData = meta.attackData();
    WrappedEntity attackedEntity = attackData.lastAttackedEntity();

    if (movementData.lastTeleport < 20) {
      return;
    }

    if (attackedEntity != null && attackedEntity.moving(0.2) && attackData.recentlyAttacked(1000)) {
      float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
      float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());

      if (pitchSpeed > 1.0) {
        // Check perfect yaw
        if (distanceToPerfectPitch == 0) {
          String description = "rotated pitch too precisely (0.0)";
          int options = Anomaly.AnomalyOption.LIMIT_2 | Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("71", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
        }
      }
    }
  }

  public final static class RotationAccuracyHeuristicMeta extends UserCustomCheckMeta {

  }
}