package de.jpx3.intave.check.movement.physics.eval;

import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.check.movement.physics.SimulationEnvironment;
import de.jpx3.intave.user.User;

import static java.lang.Math.abs;

final class InitialEvaluator extends Evaluator {
  @Override
  public String shortName() {
    return "INITIAL";
  }

  @Override
  public void evaluate(UncertaintyParameters parameters, User user, Simulation simulation, SimulationEnvironment movement) {
    boolean accountedSkippedMovement = movement.receivedFlyingPacketIn(2);
    if (accountedSkippedMovement && movement.pastNearbyCollisionInaccuracy() == 0) {
      if (abs(movement.motionX()) < 0.05 && abs(movement.motionZ()) < 0.05 && movement.motionY() < 0 && movement.motionY() > -0.4) {
        parameters.verticalUncertainty(this, 0.15);
      }
    }
  }
}
