package de.jpx3.intave.check.movement.physics.eval;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.check.movement.physics.SimulationEnvironment;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.Direction.Plane;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ViolationMetadata;

public final class Evaluation {
  private static final double VELOCITY_VL_THRESHOLD = 6;

  private final Evaluators evaluators = new Evaluators();

  public Evaluation() {
    super();
  }

  public EvaluationResult evaluate(User user, Simulation simulation, SimulationEnvironment movement) {
    Motion predictedMotion = simulation.motion();
    double predictedX = predictedMotion.motionX;
    double predictedY = predictedMotion.motionY;
    double predictedZ = predictedMotion.motionZ;

    double motionX = movement.motionX();
    double motionY = movement.motionY();
    double motionZ = movement.motionZ();

    double differenceX = predictedX - motionX;
    double differenceY = predictedY - motionY;
    double differenceZ = predictedZ - motionZ;

    double totalDistance = MathHelper.hypot3d(differenceX, differenceY, differenceZ);

    if (totalDistance <= 0.00001) {
      return EvaluationResult.empty();
    }

//    if (user.typeTranslationOf())

    ViolationMetadata violationMetadata = user.meta().violationLevel();

    UncertaintyParameters parameters = UncertaintyParameters.of(user);
    parameters.reset();

    this.evaluators.forEach(evaluator -> evaluator.evaluate(parameters, user, simulation, movement));

    // todo: make it sensitive for specific directions
    double horizontalUncertainty = parameters.uncertaintyOf(Plane.HORIZONTAL);
    double verticalUncertainty = parameters.uncertaintyOf(Plane.VERTICAL);

    double horizontalMultiplier = parameters.multiplierOf(Plane.HORIZONTAL);
    double verticalMultiplier = parameters.multiplierOf(Plane.VERTICAL);

    double horizontalDeviation = Hypot.fast(predictedX - motionX, predictedZ - motionZ);
    double verticalDeviation = Math.abs(predictedY - motionY);

    double horizontalVL = horizontalDeviation * horizontalMultiplier;
    double verticalVL = verticalDeviation * verticalMultiplier;

    // begin dump

    boolean velocityDetected = false;
    boolean skipVLCalculation = totalDistance <= 0.00001;
    boolean checkVelocity = !skipVLCalculation
      && movement.pastInWeb() > 5
      && !movement.inWater()
      && !movement.collidedWithBoat();

    boolean elytraFlying = movement.pose() == Pose.FALL_FLYING;

    if (checkVelocity && !elytraFlying && movement.pastExternalVelocity() < 10 && !movement.receivedFlyingPacketIn(2)) {
      boolean actuallyMoved = (Math.abs(predictedX) > 0.01 || Math.abs(predictedZ) > 0.01);
      if (totalDistance > 0.005 /*&& !onLadder*/) {
        if (actuallyMoved) {
          boolean aggressive = violationMetadata.physicsVelocityVL++ >= VELOCITY_VL_THRESHOLD || movement.pastExternalVelocity() == 0;
          if (aggressive || totalDistance > 0.01) {
            if (aggressive) {
              horizontalVL = Math.max(2, horizontalVL);
              velocityDetected = true;
            }
            horizontalVL *= 20.0;
          }
        } else {
          if (Math.abs(differenceY) < 0.015 && movement.pastExternalVelocity() < 2) {
            horizontalVL = 0;
          }
        }
      }
    }

    boolean flyingJump = false;
    if ((Math.abs(predictedX) < 0.04 && Math.abs(predictedZ) < 0.04) && Math.abs(predictedY - movement.jumpMotion()) < 0.05 &&
      differenceY > 0.01 && differenceY < 0.02 /* only allow positive differenceY */ && (movement.lastOnGround() || movement.onGround()) && movement.receivedFlyingPacketIn(6)) {
      flyingJump = true;
//      verticalViolationIncrease = 0;
      verticalVL = 0;
//      movement.endMotionYOverride = true;
//      movement.endMotionYOverrideValue = predictedY;
    }

    // TODO: 05/28/22 check if this worked, and deal with adjustments
    // trustfactor limit is just temporary
    boolean suspectSafeWalk = user.trustFactor().atOrBelow(TrustFactor.YELLOW);
    if (totalDistance > 0.008 && suspectSafeWalk /*&& movement.pastBlockPlacement <= 8*/ && horizontalVL > 0.1 && !movement.isSneaking()) {
      boolean smallMovement = (Math.abs(movement.motionX()) < 0.08 || Math.abs(movement.motionZ()) < 0.08) && movement.onGround();
      if (smallMovement && !movement.receivedFlyingPacketIn(3)) {
        horizontalVL = Math.max(100, horizontalVL * 50);
      }
    }

    // end dump


    if (horizontalVL < 0.0001 && verticalVL < 0.0001) {
      return EvaluationResult.empty();
    }

    return EvaluationResult.empty();
  }
}
