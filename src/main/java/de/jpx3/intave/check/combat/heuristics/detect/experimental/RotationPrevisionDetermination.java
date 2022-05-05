package de.jpx3.intave.check.combat.heuristics.detect.experimental;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionBlueprintMeta.RotationData;
import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionDetermination.RotationPrevisionDeterminationMeta;

public final class RotationPrevisionDetermination extends RotationPrevisionBlueprint<RotationPrevisionDeterminationMeta> {
  public RotationPrevisionDetermination(Heuristics parentCheck) {
    super(parentCheck, RotationPrevisionDeterminationMeta.class, 30);
  }

  @Override
  public void check(User user, List<RotationData> rotationValues) {
    double determination = determinationCoefficientYaw(rotationValues);
    user.player().sendMessage("Determination: " + determination);
  }

  public static class RotationPrevisionDeterminationMeta extends RotationPrevisionBlueprintMeta {
    double vl;
    // Nothing yet!
  }
}