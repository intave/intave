package de.jpx3.intave.detect.checks.movement.physics.pose;

import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.collision.block.BlockCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionResult;
import de.jpx3.intave.detect.checks.movement.physics.water.AquaticWaterMovementBase;
import de.jpx3.intave.user.User;

public abstract class PhysicsCalculationPart {
  private Physics physics;

  public final void setup(Physics physics) {
    this.physics = physics;
  }

  public abstract EntityCollisionResult performSimulation(
    User user, Physics.PhysicsProcessorContext context,
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

  public EntityCollisionRepository entityCollisionRepository() {
    return physics.entityCollisionRepository();
  }

  public AquaticWaterMovementBase aquaticWaterMovementBase() {
    return physics.aquaticWaterMovementBase();
  }

  public BlockCollisionRepository blockCollisionRepository() {
    return physics.blockCollisionRepository();
  }

  public boolean requiresKeyCalculation() {
    return true;
  }
}