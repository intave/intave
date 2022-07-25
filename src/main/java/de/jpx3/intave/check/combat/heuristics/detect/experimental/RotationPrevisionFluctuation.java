package de.jpx3.intave.check.combat.heuristics.detect.experimental;

import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.user.User;

import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionBlueprintMeta.RotationData;
import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionFluctuation.RotationPrevisionFluctuationMeta;

@Reserved
public final class RotationPrevisionFluctuation extends RotationPrevisionBlueprint<RotationPrevisionFluctuationMeta> {
  public RotationPrevisionFluctuation(Heuristics parentCheck) {
    super(parentCheck, RotationPrevisionFluctuationMeta.class, 250, true);
  }

  @Override
  public void check(User user, List<RotationData> rotationValues) {

    List<Float> yawDeltas = rotationValues.stream()
      .map(rotationData -> Math.abs(rotationData.yawDelta))
      .collect(Collectors.toList());
    double yawAverage = average(yawDeltas);

    // We are calculating the average divergence from the expected center with non-absolute values
    // Automated aiming measure with balanced random (-5 to 5 as example) will lead to an average of fluctuation of 0
    List<Float> fluctuation = rotationValues.stream()
      .map(rotationData -> MathHelper.noAbsDistanceInDegrees(rotationData.yaw, rotationData.expectedYaw))
      .collect(Collectors.toList());
    double fluctuationAverage = average(fluctuation);
    double fluctuationDeviation = standardDeviation(fluctuation);

    // The largest outlier
    double max = rotationValues.stream()
      .mapToDouble(rotationData -> MathHelper.distanceInDegrees(rotationData.yawDelta, rotationData.expectedYawDelta))
      .max()
      .orElse(0);
    // Currently, not accurate enough
    if (yawAverage >= 1.5 && Math.abs(fluctuationAverage) <= 0.5 && fluctuationDeviation >= 2) {
      String description = String.format("suspicious aiming fluctuation %.2f %.2f", fluctuationAverage, yawAverage);
      //Anomaly anomaly = Anomaly.anomalyOf("410", Confidence.NONE, Anomaly.Type.KILLAURA, description);
      //parentCheck().saveAnomaly(user.player(), anomaly);
    }
  }

  public static class RotationPrevisionFluctuationMeta extends RotationPrevisionBlueprintMeta {
    double vl;
    // Nothing yet!
  }
}