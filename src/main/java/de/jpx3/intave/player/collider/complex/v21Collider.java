package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet;
import it.unimi.dsi.fastutil.doubles.DoubleSet;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;

import java.util.Arrays;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class v21Collider implements Collider {
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

    Motion maybeBackOffFromEdgeResult = Motion.copyFrom(motion);

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
      Motion.copyFrom(maybeBackOffFromEdgeResult),
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
    BoundingBox box = environment.boundingBox();
    BlockShape collisionShape = Collision.shape(user, environment, box.expand(motion));
    Motion firstCollision = motion.length() == 0.0D ? motion : collideSingleBox(motion, box, collisionShape);
    boolean xChange = motion.motionX != firstCollision.motionX;
    boolean yChange = motion.motionY != firstCollision.motionY;
    boolean zChange = motion.motionZ != firstCollision.motionZ;
    boolean yChangeAndFalling = yChange && motion.motionY < 0.0D;
    if (environment.stepHeight() > 0.0F && (yChangeAndFalling || environment.onGround()) && (xChange || zChange)) {
      BoundingBox box2 = yChangeAndFalling ? box.offset(0, firstCollision.motionY, 0) : box;
      BoundingBox box3 = box2.offset(motion.motionX, environment.stepHeight(), motion.motionZ);
      if (!yChangeAndFalling) {
        box3 = box3.offset(0, -0.00001f, 0);
      }
      BlockShape newCollisionShape = Collision.shape(user, environment, box3);
      BlockShape combinedShape = BlockShapes.merge(collisionShape, newCollisionShape);
      float[] floats = collectCandidateStepUpHeights(box2, combinedShape, (float) environment.stepHeight(), (float) firstCollision.motionY);
      for (float step : floats) {
        Motion simulatedStep = motion.copy();
        simulatedStep.motionY = step;
        Motion stepCappedMotion = collideSingleBox(simulatedStep, box2, combinedShape);
        if (stepCappedMotion.horizontalLengthSqr() > firstCollision.horizontalLengthSqr()) {
          double boxYShift = box.minY - box2.minY;
          return stepCappedMotion.add(0.0D, -boxYShift, 0.0D);
        }
      }
    }
    return firstCollision;
  }

  private float[] collectCandidateStepUpHeights(
    BoundingBox box, BlockShape blockShape,
    float stepHeight, float step
  ) {
    if (blockShape.isEmpty()) {
      return FloatArrays.EMPTY_ARRAY;
    }
    DoubleSet coords = new DoubleOpenHashSet();
    blockShape.appendUnsortedCoordsTo(Y_AXIS, coords);
    double[] coordsArray = coords.toDoubleArray();
    Arrays.sort(coordsArray);
    FloatSet candidates = new FloatArraySet();
    for (double coord : coordsArray) {
      float requiredStep = (float) (coord - box.minY);
      if (!(requiredStep < 0) && requiredStep != step) {
        if (requiredStep > stepHeight) {
          break;
        }
        candidates.add(requiredStep);
      }
    }
    if (candidates.isEmpty()) {
      return FloatArrays.EMPTY_ARRAY;
    }
    float[] result = candidates.toFloatArray();
    Arrays.sort(result);
    return result;
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
