package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

final class v7Collider implements Collider {
  @Override
  public ColliderResult collide(User user, SimulationEnvironment environment, Motion motion, double positionX, double positionY, double positionZ, boolean inWeb) {
//    MovementMetadata movement = user.meta().movement();

    // ?
    // this.ySize *= 0.4F;

    double startMotionX = motion.motionX;
    double startMotionY = motion.motionY;
    double startMotionZ = motion.motionZ;

    double var13 = motion.motionX;
    double var15 = motion.motionY;
    double var17 = motion.motionZ;

    BoundingBox entityBoundingBox = environment.boundingBox();
    BoundingBox var19 = environment.boundingBox().copy();
    boolean var20 = environment.onGround() && environment.isSneaking();

    if (inWeb) {
      motion.motionX *= 0.25D;
      motion.motionY *= 0.05f;
      motion.motionZ *= 0.25D;
    }

    BlockShape var37 = Collision.shape(user, environment, entityBoundingBox.expand(startMotionX, startMotionY, startMotionZ));
    startMotionY = var37.allowedOffset(Direction.Axis.Y_AXIS, entityBoundingBox, startMotionY);
    entityBoundingBox = entityBoundingBox.offset(0, startMotionY, 0);

    boolean var36 = environment.onGround() || var15 != startMotionY && var15 < 0.0D;
    startMotionX = var37.allowedOffset(Direction.Axis.X_AXIS, entityBoundingBox, startMotionX);
    entityBoundingBox.offset(startMotionX, 0, 0);

    startMotionZ = var37.allowedOffset(Direction.Axis.Z_AXIS, entityBoundingBox, startMotionZ);
    entityBoundingBox.offset(0.0D, 0.0D, startMotionZ);

    double var25;
    double var27;
    double var38;

    boolean step = false;
    double stepHeight = 0.0D;
    if (var36 && (var20 /*|| this.ySize < 0.05F*/) && (var13 != startMotionX || var17 != startMotionZ)) {
      var38 = startMotionX;
      var25 = startMotionY;
      var27 = startMotionZ;
      startMotionX = var13;
      startMotionY = environment.stepHeight();
      startMotionZ = var17;

      BoundingBox var29 = entityBoundingBox.copy();
      entityBoundingBox = var19;

      var37 = Collision.shape(user, environment, entityBoundingBox.expand(var13, startMotionY, var17));
      startMotionY = var37.allowedOffset(Direction.Axis.Y_AXIS, entityBoundingBox, startMotionY);
      entityBoundingBox.offset(0, startMotionY, 0);

      startMotionX = var37.allowedOffset(Direction.Axis.X_AXIS, entityBoundingBox, startMotionX);
      entityBoundingBox.offset(startMotionX, 0, 0);

      startMotionZ = var37.allowedOffset(Direction.Axis.Z_AXIS, entityBoundingBox, startMotionZ);
      entityBoundingBox.offset(0, 0, startMotionZ);

      // where is the sneak limiter?

      if (var38 * var38 + var27 * var27 >= startMotionX * startMotionX + startMotionZ * startMotionZ) {
        startMotionX = var38;
        startMotionY = var25;
        startMotionZ = var27;
        entityBoundingBox = var29;
      } else {
        step = true;
        stepHeight = startMotionY;
      }
    }

    boolean collidedVertically = startMotionY != motion.motionY;
    boolean collidedHorizontally = startMotionX != motion.motionX || startMotionZ != motion.motionZ;
    boolean onGround = startMotionY != motion.motionY && startMotionY < 0.0;
    boolean moveResetX = startMotionX != motion.motionX;
    boolean moveResetZ = startMotionZ != motion.motionZ;
    double newPositionX = (entityBoundingBox.minX + entityBoundingBox.maxX) / 2.0D;
    double newPositionY = entityBoundingBox.minY;
    double newPositionZ = (entityBoundingBox.minZ + entityBoundingBox.maxZ) / 2.0D;
    motion.motionX = newPositionX - positionX;
    motion.motionY = newPositionY - positionY;
    motion.motionZ = newPositionZ - positionZ;
    return new ColliderResult(
      Motion.copyFrom(motion), null, onGround,
      collidedHorizontally, collidedVertically, moveResetX, moveResetZ,
      step, false, stepHeight
    );
  }
}