package de.jpx3.intave.check.combat.heuristics.detect.experimental;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.user.User;

import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_64s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_4;
import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionBlueprintMeta.RotationData;
import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionDetermination.RotationPrevisionDeterminationMeta;

public final class RotationPrevisionDetermination extends RotationPrevisionBlueprint<RotationPrevisionDeterminationMeta> {
  public RotationPrevisionDetermination(Heuristics parentCheck) {
    super(parentCheck, RotationPrevisionDeterminationMeta.class, 100, true);
  }

  @Override
  public void check(User user, List<RotationData> rotationValues) {
    RotationPrevisionDeterminationMeta meta = metaOf(userOf(user.player()));
    double determination = determinationCoefficientYaw(rotationValues);

    List<Float> yawDeltas = rotationValues.stream()
      .map(rotationData -> Math.abs(rotationData.yawDelta))
      .collect(Collectors.toList());
    double yawAverage = average(yawDeltas);

    if (yawAverage > 0.5 && determination > 0.55) {
      String description = String.format("suspicious aiming pattern %.2f %.2f %.2f", determination, yawAverage, meta.vl);
      if (++meta.vl >= 5) {
        Anomaly anomaly = Anomaly.anomalyOf("yaw:det(1)", Confidence.LIKELY, Anomaly.Type.KILLAURA, description, DELAY_64s | LIMIT_4);
        parentCheck().saveAnomaly(user.player(), anomaly);
      } else {
        Anomaly anomaly = Anomaly.anomalyOf("yaw:det(0)", Confidence.NONE, Anomaly.Type.KILLAURA, description);
        parentCheck().saveAnomaly(user.player(), anomaly);
      }
    } else {
      meta.vl = Math.max(0, meta.vl - 0.25);
    }
  }

  public static class RotationPrevisionDeterminationMeta extends RotationPrevisionBlueprintMeta {
    double vl;
    // Nothing yet!
  }
}