package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

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
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    if (!attackData.recentlyAttacked(1000)) {
      return;
    }
    EntityShade entity = attackData.lastAttackedEntity();
    if (entity == null) {
      return;
    }
    double distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    if (heuristicMeta.yawSpeeds.size() > 40) {
      double yawAverage = averageOf(heuristicMeta.yawSpeeds);
      double maxDistanceToPerfectYaw = heuristicMeta.distancesToPerfectYaw
        .stream()
        .mapToDouble(Double::doubleValue)
        .max()
        .orElse(0);
      List<Double> angleData = heuristicMeta.distancesToPerfectYaw;
      double averageRatio = yawAverage / averageOf(angleData);
      double maxRatio = maxDistanceToPerfectYaw / yawAverage;
      if (maxRatio < 2 && maxDistanceToPerfectYaw < 30) {
        String descriptor = "rotated suspiciously (" + MathHelper.formatDouble(maxRatio, 4) + " / " + MathHelper.formatDouble(maxDistanceToPerfectYaw, 4) + ")";
        int options = Anomaly.AnomalyOption.LIMIT_8 | Anomaly.AnomalyOption.SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("91", Confidence.MAYBE, Anomaly.Type.KILLAURA, descriptor, options);
        parentCheck().saveAnomaly(player, anomaly);
      }
      if (yawAverage >= 3.5 && maxDistanceToPerfectYaw <= 12.5 && averageRatio > 1) {
        String descriptor = "precise rotation yaw (" + MathHelper.formatDouble(yawAverage, 4) + ")";
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

  private double averageOf(List<? extends Number> data) {
    double sum = 0;
    for (Number element : data) {
      sum += element.doubleValue();
    }
    if (sum == 0) {
      return 0;
    }
    return sum / data.size();
  }

  public static final class ULMeta extends CheckCustomMetadata {
    private final List<Double> yawSpeeds = Lists.newArrayList();
    private final List<Double> distancesToPerfectYaw = Lists.newArrayList();
  }
}