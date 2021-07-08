package de.jpx3.intave.detect.checks.movement.physics.simulator;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;

public final class HorsePoseSimulator extends DefaultPoseSimulator {
  private final static double MAXIMUM_HORSE_MOVEMENT_SPEED = 0.22499999403953552D;//0.3374999970197678;

  @Override
  public ComplexColliderSimulationResult performSimulation(
    User user, MotionVector context,
    float forward, float strafe,
    boolean attackReduce, boolean jumped, boolean handActive
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    float horseForward = forward;
    float horseStrafe = strafe * 0.5F;

    if (horseForward <= 0.0F) {
      horseForward *= 0.25F;
//      this.gallopTime = 0;
    }

//    if (movementData.onGround /*&& this.jumpPower == 0.0F && this.isRearing() && !this.allowStandSliding*/) {
//      horseStrafe = 0.0F;
//      horseForward = 0.0F;
//    }

//    System.out.println("horseForward:" + horseForward);

    float aiMoveSpeed = 0;
    movementData.setJumpMovementFactor(0.02f);
    movementData.setAiMoveSpeed(0.2f);

    return super.performSimulation(user, context, horseForward, horseStrafe, attackReduce, jumped, handActive);
  }
}