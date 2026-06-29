package de.jpx3.intave.check.movement.physics.evaluation;

import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.check.movement.physics.MoveMetric.NEARBY_COLLISION_INACCURACY;
import static java.lang.Math.abs;

final class InitialEvaluator extends Evaluator {
  @Override
  public String shortName() {
    return "INITIAL";
  }

  @Override
  public void evaluate(UncertaintyParameters parameters, User user, Simulation simulation, SimulationEnvironment movement) {
    boolean accountedSkippedMovement = movement.receivedFlyingPacketIn(2);
    if (accountedSkippedMovement && movement.ticksPast(NEARBY_COLLISION_INACCURACY) == 0) {
      if (abs(movement.motionX()) < 0.05 && abs(movement.motionZ()) < 0.05 && movement.motionY() < 0 && movement.motionY() > -0.4) {
        parameters.verticalUncertainty(this, 0.15);
      }
    }
  }
}
