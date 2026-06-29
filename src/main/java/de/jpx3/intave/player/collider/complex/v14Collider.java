package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class v14Collider implements Collider {
  @Override
  public ColliderResult collide(
    User user,
    SimulationEnvironment environment,
    Motion motion,
    double positionX,
    double positionY,
    double positionZ,
    boolean inWeb
  ) {
    // webs
    if (inWeb) {
      motion.motionX *= 0.25D;
      motion.motionY *= 0.05f;
      motion.motionZ *= 0.25D;
    }

    // "maybeBackOffFromEdge"
    boolean edgeSneak = false;
    if (environment.onGround() && environment.isSneaking()) {
      edgeSneak = calculateBackOffFromEdge(user, environment, environment.stepHeight(), motion);
    }

    // "collide"
    double initialX = motion.motionX;
    double initialY = motion.motionY;
    double initialZ = motion.motionZ;

    boolean[] stepped = new boolean[1];
    motion.setTo(motionAfterCollision(user, environment, motion, stepped));

    boolean collidedVertically = initialY != motion.motionY;
    boolean collidedHorizontally = initialX != motion.motionX || initialZ != motion.motionZ;
    boolean onGround = initialY != motion.motionY && initialY < 0.0;
    boolean moveResetX = initialX != motion.motionX;
    boolean moveResetZ = initialZ != motion.motionZ;

    return new ColliderResult(
      Motion.copyFrom(motion),
      null,
      onGround,
      collidedHorizontally,
      collidedVertically,
      moveResetX,
      moveResetZ,
      stepped[0], edgeSneak,
      environment.stepHeight()
    );
  }

  private boolean calculateBackOffFromEdge(User user, SimulationEnvironment environment, double length, Motion context) {
    BoundingBox boundingBox = environment.boundingBox();
    double motionX = context.motionX;
    double motionZ = context.motionZ;
    boolean edgeSneak = false;
    while (motionX != 0.0D
      && Collision.nonePresent(user, environment, boundingBox.offset(motionX, -length, 0.0D))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
      } else {
        motionX += 0.05D;
      }
      edgeSneak = true;
    }
    while (motionZ != 0.0D
      && Collision.nonePresent(user, environment, boundingBox.offset(0.0D, -length, motionZ))) {
      if (motionZ < 0.05D && motionZ >= -0.05D) {
        motionZ = 0.0D;
      } else if (motionZ > 0.0D) {
        motionZ -= 0.05D;
      } else {
        motionZ += 0.05D;
      }
      edgeSneak = true;
    }
    while (motionX != 0.0D
      && motionZ != 0.0D
      && Collision.nonePresent(user, environment, boundingBox.offset(motionX, -length, motionZ))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
      } else {
        motionX += 0.05D;
      }
      edgeSneak = true;
      if (motionZ < 0.05D && motionZ >= -0.05D) {
        motionZ = 0.0D;
      } else if (motionZ > 0.0D) {
        motionZ -= 0.05D;
      } else {
        motionZ += 0.05D;
      }
    }
    context.motionX = motionX;
    context.motionZ = motionZ;
    return edgeSneak;
  }

  private Motion motionAfterCollision(User user, SimulationEnvironment environment, Motion motion, boolean[] stepped) {
    BoundingBox aabb = environment.boundingBox();
    BlockShape collisionShape = Collision.shape(user, environment, aabb.expand(motion));
    Motion firstCollision = motion.length() == 0.0D ? motion : collideSingleBox(motion, aabb, collisionShape);
    boolean xChange = motion.motionX != firstCollision.motionX;
    boolean yChange = motion.motionY != firstCollision.motionY;
    boolean zChange = motion.motionZ != firstCollision.motionZ;
    boolean onGroundOrFalling = environment.onGround() || yChange && motion.motionY < 0.0D;
    if (environment.stepHeight() > 0.0F && onGroundOrFalling && (xChange || zChange)) {
      Motion firstStep = collideSingleBox(new Motion(motion.motionX, environment.stepHeight(), motion.motionZ), aabb, collisionShape);
      Motion secondStep = collideSingleBox(new Motion(0.0D, environment.stepHeight(), 0.0D), aabb.expand(motion.motionX, 0.0D, motion.motionZ), collisionShape);

      if (secondStep.motionY < environment.stepHeight()) {
        Motion thirdStep = collideSingleBox(new Motion(motion.motionX, 0.0D, motion.motionZ), aabb.move(secondStep), collisionShape).add(secondStep);
        if (thirdStep.horizontalLengthSqr() > firstStep.horizontalLengthSqr()) {
          firstStep = thirdStep;
        }
      }
      if (firstStep.horizontalLengthSqr() > firstCollision.horizontalLengthSqr()) {
        stepped[0] = true;
        return firstStep.add(collideSingleBox(new Motion(0.0D, -firstStep.motionY + motion.motionY, 0.0D), aabb.move(firstStep), collisionShape));
      }
    }
    return firstCollision;
  }

  private Motion collideSingleBox(Motion input, BoundingBox playerBox, BlockShape collision) {
    if (collision.isEmpty()) {
      return input;
    }
    double motionX = input.motionX;
    double motionY = input.motionY;
    double motionZ = input.motionZ;
    if (motionY != 0.0D) {
      motionY = collision.allowedOffset(Y_AXIS, playerBox, motionY);
      if (motionY != 0.0D) {
        playerBox = playerBox.offset(0.0D, motionY, 0.0D);
      }
    }
    boolean zAxisDominant = Math.abs(motionX) < Math.abs(motionZ);
    if (zAxisDominant && motionZ != 0.0D) {
      motionZ = collision.allowedOffset(Z_AXIS, playerBox, motionZ);
      if (motionZ != 0.0D) {
        playerBox = playerBox.offset(0.0D, 0.0D, motionZ);
      }
    }
    if (motionX != 0.0D) {
      motionX = collision.allowedOffset(X_AXIS, playerBox, motionX);
      if (!zAxisDominant && motionX != 0.0D) {
        playerBox = playerBox.offset(motionX, 0.0D, 0.0D);
      }
    }
    if (!zAxisDominant && motionZ != 0.0D) {
      motionZ = collision.allowedOffset(Z_AXIS, playerBox, motionZ);
    }
    return new Motion(motionX, motionY, motionZ);
  }
}
