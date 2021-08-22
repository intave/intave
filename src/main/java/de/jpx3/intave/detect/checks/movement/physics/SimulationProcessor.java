package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.user.User;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;

public interface SimulationProcessor {
  ComplexColliderSimulationResult simulate(User user, Simulator simulator);

  default ComplexColliderSimulationResult simulateWithoutKeyPress(
    User user, Simulator simulator
  ) {
    return simulateWithKeyPress(user, simulator,0, 0, false);
  }

  ComplexColliderSimulationResult simulateWithKeyPress(User user, Simulator simulator, int forward, int strafe, boolean jumped);
}
