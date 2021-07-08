package de.jpx3.intave.world.collider.complex;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.user.User;

public interface ComplexColliderProcessor {
  float STEP_HEIGHT = 0.6f;

  ComplexColliderSimulationResult simulateCollision(
    User user, MotionVector context,
    boolean inWeb,
    double positionX, double positionY, double positionZ
  );
}