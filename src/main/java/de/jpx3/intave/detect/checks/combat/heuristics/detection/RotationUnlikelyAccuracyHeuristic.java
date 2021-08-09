package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.event.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.event.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.tools.Rotation.averageOf;

public final class RotationUnlikelyAccuracyHeuristic extends MetaCheckPart<Heuristics, RotationUnlikelyAccuracyHeuristic.ULMeta> {
  public RotationUnlikelyAccuracyHeuristic(Heuristics parentCheck) {
    super(parentCheck, ULMeta.class);
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
    ULMeta heuristicMeta = metaOf(user);
    UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    if (!attackData.recentlyAttacked(1000)) {
      return;
    }
    WrappedEntity entity = attackData.lastAttackedEntity();
    if (entity == null) {
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
        int options = Anomaly.AnomalyOption.LIMIT_8 | Anomaly.AnomalyOption.SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("91", Confidence.MAYBE, Anomaly.Type.KILLAURA, descriptor, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
      if (yawAverage >= 3.5 && maxDistanceToPerfectYaw <= 12.5 && averageRatio > 1) {
        String descriptor = "precise rotation yaw (" + yawAverage + ")";
        int options = Anomaly.AnomalyOption.LIMIT_4 | Anomaly.AnomalyOption.SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("92", Confidence.MAYBE, Anomaly.Type.KILLAURA, descriptor, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
      heuristicMeta.distancesToPerfectYaw.clear();
      heuristicMeta.yawSpeeds.clear();
    }
    if (entity.moving(0.05)) {
      heuristicMeta.distancesToPerfectYaw.add(distanceToPerfectYaw);
      heuristicMeta.yawSpeeds.add((double) yawSpeed);
    }
  }

  public final static class ULMeta extends UserCustomCheckMeta {
    private final List<Double> yawSpeeds = Lists.newArrayList();
    private final List<Double> distancesToPerfectYaw = Lists.newArrayList();
  }
}