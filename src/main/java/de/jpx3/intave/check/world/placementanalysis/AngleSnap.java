package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public class AngleSnap extends PlayerCheckPart<PlacementAnalysis> {
  private final List<Float> rotationHistory = new LinkedList<>();
  private double vl = 0;

  public AngleSnap(User user, PlacementAnalysis parentCheck) {
    super(user, parentCheck);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      POSITION_LOOK, LOOK, POSITION, FLYING
    }
  )
  public void on(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    float distanceTo45Deg = Math.min(Math.abs(45 - movementData.rotationYaw % 45), Math.abs(movementData.rotationYaw % 45));
    float pastDistanceTo45Deg = Math.min(Math.abs(45 - movementData.lastRotationYaw % 45), Math.abs(movementData.lastRotationYaw % 45));

    if (distanceTo45Deg < 0.08) {
      // 5th not included
      float rotationSum = 0;
      int maxHistory = Math.min(5, rotationHistory.size());
      for (int i = 0; i < rotationHistory.size() - 1; i++) {
        if (i > maxHistory) {
          break;
        }
        rotationSum += Math.abs(rotationHistory.get(i) - rotationHistory.get(i + 1));
      }

      boolean recentBlockPlacement = user.meta().movement().pastBlockPlacement < 20;
      if (rotationSum > 60 && recentBlockPlacement && user.meta().movement().lastTeleport > 5 && rotationSum < 300) {
        if (vl > 2) {
//          int outputVL = rotationSum > 150 || (Math.abs(rotationSum - 90) < 0.1) ? 25 : 10;
//          float lastRot = Math.abs(movementData.rotationYaw - movementData.lastRotationYaw);
//          if (pastDistanceTo45Deg < 0.08 && (lastRot > 30)) {
//            outputVL += 35;
//          }
//          outputVL += (int) (vl * 3);
          int outputVL = (int) vl * 3;
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails((int)rotationSum + "deg snap to 45deg angle over " + maxHistory + " ticks")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
            .withVL(outputVL).build();
          Modules.violationProcessor().processViolation(violation);
        }
        rotationHistory.clear();
        vl++;
        vl = Math.min(6, vl);
      } else {
        vl = Math.max(0, vl - 0.0025);
      }
    }
    while (rotationHistory.size() > 3 * 20) {
      rotationHistory.remove(0);
    }
    rotationHistory.add(movementData.rotationYaw);
  }
}
