package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.google.common.collect.Lists;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.nayoro.NayoroRelay;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.module.nayoro.event.EntityMoveEvent;
import de.jpx3.intave.module.nayoro.event.PlayerMoveEvent;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

@Reserved
public final class RotationStandardDeviationRelayHeuristic extends MetaCheckPart<Heuristics, RotationStandardDeviationRelayHeuristic.RotationStandardDeviationMeta> {
  public RotationStandardDeviationRelayHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationStandardDeviationMeta.class);
  }

  @NayoroRelay
  public void onMove(PlayerContainer player, PlayerMoveEvent event) {
    RotationStandardDeviationMeta meta = player.meta(RotationStandardDeviationMeta.class);
    Environment environment = player.environment();
    int lastAttacked = player.lastAttackedEntity();
    if (lastAttacked != -1) {
//      Bukkit.broadcastMessage(lastAttacked + " " + player.recentlyAttacked(500) + " " + environment.entityMoved(lastAttacked, 0.05));
    }
    if (lastAttacked != -1 && player.recentlyAttacked(500) && environment.entityMoved(lastAttacked, 0.05)) {
      float yaw = event.yaw();
      float lastYaw = event.lastYaw();
      float yawSpeed = MathHelper.distanceInDegrees(yaw, lastYaw);
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(player.perfectYaw(), yaw);
//      Bukkit.broadcastMessage(yawSpeed + " " + distanceToPerfectYaw + " " + environment.positionOf(lastAttacked).toString());
      if (yawSpeed > 2.6) {
        meta.distancesToPerfectYaw.add(distanceToPerfectYaw);
      }
      if (meta.distancesToPerfectYaw.size() >= 7) {
        evaluateYawPatterns(player);
        meta.distancesToPerfectYaw.clear();
      }
      float pitch = event.pitch();
      float lastPitch = event.lastPitch();
      float pitchSpeed = Math.abs(pitch - lastPitch);
      float distanceToPerfectPitch = Math.abs(pitch - player.perfectPitch());
      if (pitchSpeed > 0.5 && yawSpeed > 3) {
        meta.distancesToPerfectPitch.add(distanceToPerfectPitch);
      }
      if (meta.distancesToPerfectPitch.size() >= 10) {
        evaluatePitchPatterns(player);
        meta.distancesToPerfectPitch.clear();
      }
    }
  }

  @NayoroRelay
  public void onEntityMove(PlayerContainer player, EntityMoveEvent move) {
//    if (move.entityId() == player.lastAttackedEntity() && player.recentlyAttacked(500)) {
//      Bukkit.broadcastMessage(MathHelper.formatPosition(new Location(null, move.x(), move.y(), move.z())));
//    }
  }

  private void evaluateYawPatterns(PlayerContainer player) {
    RotationStandardDeviationMeta meta = player.meta(RotationStandardDeviationMeta.class);
    double standardDeviation = standardDeviation(meta.distancesToPerfectYaw);
//    Bukkit.broadcastMessage(player.id() + "/" + player.version() + " x " + "standard deviation (yaw) (" + MathHelper.formatDouble(standardDeviation, 4) + ")");
    if (standardDeviation < 1.0) {
      if (meta.rotationBalanceYaw++ >= 2) {
        String description = "standard deviation (yaw) (" + MathHelper.formatDouble(standardDeviation, 4) + ")";
        player.noteAnomaly("121", Confidence.LIKELY, description);
        meta.rotationBalanceYaw--;
        //dmc24
        player.nerf(AttackNerfStrategy.DMG_LIGHT, "24");
      }
    } else {
      meta.rotationBalanceYaw -= meta.rotationBalanceYaw > 0 ? 0.2 : 0;
    }
  }

  private void evaluatePitchPatterns(PlayerContainer player) {
    RotationStandardDeviationMeta meta = player.meta(RotationStandardDeviationMeta.class);
    double standardDeviation = standardDeviation(meta.distancesToPerfectPitch);
    if (standardDeviation < 3.0) {
      if (meta.rotationBalancePitch++ >= 4) {
        String description = "standard deviation (pitch) (" + standardDeviation + ")";
        player.noteAnomaly("122", Confidence.LIKELY, description);
        meta.rotationBalancePitch -= 2;
        //dmc25
        player.nerf(AttackNerfStrategy.HT_LIGHT, "25");
      }
    } else {
      meta.rotationBalancePitch -= meta.rotationBalancePitch > 0 ? 0.2 : 0;
    }
  }

  private double standardDeviation(List<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  public static class RotationStandardDeviationMeta extends CheckCustomMetadata {
    private final List<Float> distancesToPerfectYaw = Lists.newArrayList();
    private final List<Float> distancesToPerfectPitch = Lists.newArrayList();
    private double rotationBalanceYaw;
    private double rotationBalancePitch;
  }
}