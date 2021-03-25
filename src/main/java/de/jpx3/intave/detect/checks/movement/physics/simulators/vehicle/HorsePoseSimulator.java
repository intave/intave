package de.jpx3.intave.detect.checks.movement.physics.simulators.vehicle;

import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.detect.checks.movement.physics.simulators.DefaultPoseSimulator;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;

public final class HorsePoseSimulator extends DefaultPoseSimulator {
  private final static double MAXIMUM_HORSE_MOVEMENT_SPEED = 0.22499999403953552D;//0.3374999970197678;

  @Override
  public ComplexColliderSimulationResult performSimulation(
    User user, ProcessorMotionContext context,
    float forward, float strafe,
    boolean attackReduce, boolean jumped, boolean handActive
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    float rotationYaw = movementData.rotationYaw;

    strafe = strafe * 0.5F;

    if (forward <= 0.0F) {
      forward *= 0.25F;
    }

//    if (movementData.onGround && this.jumpPower == 0.0F && this.isRearing() && !this.allowStandSliding) {
//      strafe = 0.0F;
//      forward = 0.0F;
//    }

//    if (this.jumpPower > 0.0F && !this.isHorseJumping() && movementData.onGround) {
//      this.motionY = this.getHorseJumpStrength() * (double) this.jumpPower;
//
//      if (this.isPotionActive(MobEffects.JUMP_BOOST)) {
//        this.motionY += (double) ((float) (this.getActivePotionEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);
//      }
//
//      this.setHorseJumping(true);
//      this.isAirBorne = true;
//
//      if (forward > 0.0F) {
//        float f = WrappedMathHelper.sin(rotationYaw * 0.017453292F);
//        float f1 = WrappedMathHelper.cos(rotationYaw * 0.017453292F);
//        context.motionX += (double) (-0.4F * f * this.jumpPower);
//        context.motionZ += (double) (0.4F * f1 * this.jumpPower);
//      }
//
//      this.jumpPower = 0.0F;
//    }

//    // Set jump movement factor
//    float originalJumpMovementFactor = movementData.jumpMovementFactor();
//    movementData.setJumpMovementFactor((float) (0.019904632));
//
//    // Set AI move speed
//    float originalAIMoveSpeed = movementData.aiMoveSpeed();
//    movementData.setAiMoveSpeed((float) 0.019904632);

    ComplexColliderSimulationResult collisionResult = super.performSimulation(user, context, forward, strafe, attackReduce, jumped, handActive);

//    movementData.setJumpMovementFactor(originalJumpMovementFactor);
//    movementData.setAiMoveSpeed(originalAIMoveSpeed);


    if (movementData.onGround) {
//      this.jumpPower = 0.0F;
//      this.setHorseJumping(false);
    }

    return collisionResult;
  }
}