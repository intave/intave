package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.tools.RotationMathHelper.averageOf;

public final class RotationLHeuristics extends IntaveMetaCheckPart<Heuristics, RotationLHeuristics.RotationLMeta> {
  public RotationLHeuristics(Heuristics parentCheck) {
    super(parentCheck, RotationLMeta.class);
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
    User user = UserRepository.userOf(player);
    RotationLMeta heuristicMeta = metaOf(user);

    User.UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();

    if (!attackData.recentlyAttacked(1000)) {
      return;
    }

    double distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);

    if (heuristicMeta.yawSpeeds.size() > 40) {
      double yawAverage = averageOf(heuristicMeta.yawSpeeds);
      double maxDistanceToPerfectYaw = heuristicMeta.distancesToPerfectYaw
        .stream()
        .mapToDouble(i -> i)
        .max()
        .orElse(0);

      List<Double> angleData = heuristicMeta.distancesToPerfectYaw;
      double averageRatio = yawAverage / averageOf(angleData);
      double maxRatio = maxDistanceToPerfectYaw / yawAverage;

      if (maxRatio < 2 && maxDistanceToPerfectYaw < 30) {
        String descriptor = "rotated suspiciously (" + maxRatio + " / " + maxDistanceToPerfectYaw + ")";
        Anomaly anomaly = Anomaly.anomalyOf("91", Confidence.PROBABLE, Anomaly.Type.KILLAURA, descriptor, Anomaly.AnomalyOption.LIMIT_8);
        parentCheck().saveAnomaly(player, anomaly);
      }

      if (yawAverage >= 3.5 && maxDistanceToPerfectYaw <= 12.5 && averageRatio > 1) {
        String descriptor = "precise rotation yaw (" + yawAverage + ")";
        Anomaly anomaly = Anomaly.anomalyOf("92", Confidence.PROBABLE, Anomaly.Type.KILLAURA, descriptor,  Anomaly.AnomalyOption.LIMIT_8);
        parentCheck().saveAnomaly(player, anomaly);
      }

      heuristicMeta.distancesToPerfectYaw.clear();
      heuristicMeta.yawSpeeds.clear();
    }

    heuristicMeta.distancesToPerfectYaw.add(distanceToPerfectYaw);
    heuristicMeta.yawSpeeds.add((double) yawSpeed);
  }

  public final static class RotationLMeta extends UserCustomCheckMeta {
    private final List<Double> yawSpeeds = Lists.newArrayList();
    private final List<Double> distancesToPerfectYaw = Lists.newArrayList();
  }
}