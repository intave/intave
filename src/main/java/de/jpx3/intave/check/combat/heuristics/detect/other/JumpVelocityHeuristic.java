package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class JumpVelocityHeuristic extends MetaCheckPart<Heuristics, JumpVelocityHeuristic.JumpVelocityHeuristicMeta> {

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
    MovementMetadata movementData = user.meta().movement();

    if (movementData.physicsJumped) {
      double motionY = movementData.motionY();
      double diffY = motionY - 0.42d;
      if (Math.abs(diffY) < 0.000000000000017) {
        String message = "jumped with wrong motion";
        if (movementData.pastVelocity == 0) {
          message += " and got velocity";
        }
        Anomaly anomaly = Anomaly.anomalyOf("jump:vel",
          Confidence.LIKELY,
          Anomaly.Type.KILLAURA,
          message, Anomaly.AnomalyOption.DELAY_16s
        );
        parentCheck().saveAnomaly(player, anomaly);
      }
    }
  }

  public static class JumpVelocityHeuristicMeta extends CheckCustomMetadata {
  }
}