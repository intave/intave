package de.jpx3.intave.check.movement.physics;

final class HorseSimulator extends BaseSimulator {
  private static final double MAXIMUM_HORSE_MOVEMENT_SPEED = 0.22499999403953552D;//0.3374999970197678;

//  @Override
//  @Deprecated
//  public Simulation performSimulation(
//    User user, Motion motion,
//    float forward, float strafe,
//    boolean attackReduce, boolean sprinting, boolean jumped, boolean handActive
//  ) {
//    MovementMetadata movementData = user.meta().movement();
//    float horseForward = forward;
//    float horseStrafe = strafe * 0.5F;
//
//    if (horseForward <= 0.0F) {
//      horseForward *= 0.25F;
////      this.gallopTime = 0;
//    }
//
////    if (movementData.onGround /*&& this.jumpPower == 0.0F && this.isRearing() && !this.allowStandSliding*/) {
////      horseStrafe = 0.0F;
////      horseForward = 0.0F;
////    }
//
}