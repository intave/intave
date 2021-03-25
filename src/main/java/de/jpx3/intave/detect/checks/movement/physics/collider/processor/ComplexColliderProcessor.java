package de.jpx3.intave.detect.checks.movement.physics.collider.processor;

import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.user.User;

public interface ComplexColliderProcessor {
  float STEP_HEIGHT = 0.6f;

  ComplexColliderSimulationResult simulateCollision(
    User user, ProcessorMotionContext context,
    boolean inWeb,
    double positionX, double positionY, double positionZ
  );
}