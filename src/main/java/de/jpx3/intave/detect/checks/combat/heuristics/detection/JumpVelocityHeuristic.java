package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Client.*;

public class JumpVelocityHeuristic extends IntaveMetaCheckPart<Heuristics, JumpVelocityHeuristic.JumpVelocityHeuristicMeta> {

  public JumpVelocityHeuristic(Heuristics parentCheck) {
    super(parentCheck, JumpVelocityHeuristic.JumpVelocityHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, FLYING, LOOK, POSITION_LOOK
    }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    if(movementData.physicsJumped) {
      double motionY = movementData.motionY();
      double diffY = motionY - 0.42d;

      if(Math.abs(diffY) < 0.000000000000017) {
        String message = "jumped with wrong motion";

        if(movementData.pastVelocity == 0) {
          message += " and got velocity";
        }

        Anomaly anomaly = Anomaly.anomalyOf("200",
          Confidence.NONE,
          Anomaly.Type.KILLAURA,
          message, Anomaly.AnomalyOption.DELAY_16s
        );
        parentCheck().saveAnomaly(player, anomaly);
      }
    }
  }


  public static class JumpVelocityHeuristicMeta extends UserCustomCheckMeta {
  }
}