package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.user.User;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;

public abstract class PoseSimulator {
  private Physics physics;

  public final void checkLinkage(Physics physics) {
    this.physics = physics;
  }

  public abstract ComplexColliderSimulationResult performSimulation(
    User user, MotionVector context,
    float keyForward, float keyStrafe,
    boolean attackReduce, boolean jumped, boolean handActive
  );

  public abstract void prepareNextTick(
    User user,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  );

  public Physics physics() {
    return physics;
  }

  public boolean affectedByMovementKeys() {
    return true;
  }
}