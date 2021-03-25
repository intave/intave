package de.jpx3.intave.detect.checks.movement.physics.simulators;

import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;
import de.jpx3.intave.detect.checks.movement.physics.block.CustomBlocks;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.user.User;

public abstract class PoseSimulator {
  private Physics physics;

  public final void checkLinkage(Physics physics) {
    this.physics = physics;
  }

  public abstract ComplexColliderSimulationResult performSimulation(
    User user, ProcessorMotionContext context,
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

  public CustomBlocks customBlocks() {
    return physics.blockCollisionRepository();
  }

  public boolean requiresKeyCalculation() {
    return true;
  }
}