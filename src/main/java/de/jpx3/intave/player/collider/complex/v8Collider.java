package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class v8Collider implements Collider {
  @Override
  public ColliderResult collide(User user, SimulationEnvironment environment, Motion motion, double positionX, double positionY, double positionZ, boolean inWeb) {
    Player player = user.player();
//    MetadataBundle meta = user.meta();
//    MovementMetadata movement = meta.movement();
    if (inWeb) {
      motion.motionX *= 0.25D;
      motion.motionY *= 0.05f;
      motion.motionZ *= 0.25D;
    }
    double startMotionX = motion.motionX;
    double startMotionY = motion.motionY;
    double startMotionZ = motion.motionZ;
    boolean step = false;
    boolean edgeSneak = false;
    if (environment.onGround() && environment.isSneaking()) {
      BoundingBox boundingBox = environment.boundingBox();
      double size;
      for (size = 0.05D; motion.motionX != 0.0D && Collision.nonePresent(player, boundingBox.offset(motion.motionX, -1.0D, 0.0D)); startMotionX = motion.motionX) {
        if (motion.motionX < size && motion.motionX >= -size) {
          motion.motionX = 0.0D;
        } else if (motion.motionX > 0.0D) {
          motion.motionX -= size;
        } else {
          motion.motionX += size;
        }
        edgeSneak = true;
      }
      for (; motion.motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(0.0D, -1.0D, motion.motionZ)); startMotionZ = motion.motionZ) {
        if (motion.motionZ < size && motion.motionZ >= -size) {
          motion.motionZ = 0.0D;
        } else if (motion.motionZ > 0.0D) {
          motion.motionZ -= size;
        } else {
          motion.motionZ += size;
        }
        edgeSneak = true;
      }
      for (; motion.motionX != 0.0D && motion.motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(motion.motionX, -1.0D, motion.motionZ)); startMotionZ = motion.motionZ) {
        if (motion.motionX < size && motion.motionX >= -size) {
          motion.motionX = 0.0D;
        } else if (motion.motionX > 0.0D) {
          motion.motionX -= size;
        } else {
          motion.motionX += size;
        }
        startMotionX = motion.motionX;
        if (motion.motionZ < size && motion.motionZ >= -size) {
          motion.motionZ = 0.0D;
        } else if (motion.motionZ > 0.0D) {
          motion.motionZ -= size;
        } else {
          motion.motionZ += size;
        }
        edgeSneak = true;
      }
    }
    BlockShape collisionShape = Collision.shape(player, environment.boundingBox().expand(motion.motionX, motion.motionY, motion.motionZ));
    BoundingBox startBoundingBox = environment.boundingBox();
    BoundingBox entityBoundingBox = environment.boundingBox();
    motion.motionY = collisionShape.allowedOffset(Y_AXIS, entityBoundingBox, motion.motionY);
    entityBoundingBox = entityBoundingBox.offset(0.0D, motion.motionY, 0.0D);
    boolean flag1 = environment.onGround() || startMotionY != motion.motionY && startMotionY < 0.0D;
    motion.motionX = collisionShape.allowedOffset(X_AXIS, entityBoundingBox, motion.motionX);
    entityBoundingBox = entityBoundingBox.offset(motion.motionX, 0.0D, 0.0D);
    motion.motionZ = collisionShape.allowedOffset(Z_AXIS, entityBoundingBox, motion.motionZ);
    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, motion.motionZ);
    if (flag1 && (startMotionX != motion.motionX || startMotionZ != motion.motionZ)) {
      double copyX = motion.motionX;
      double copyY = motion.motionY;
      double copyZ = motion.motionZ;
      BoundingBox axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      motion.motionY = environment.stepHeight();
      BlockShape shape = Collision.shape(player, entityBoundingBox.expand(startMotionX, motion.motionY, startMotionZ));
      BoundingBox axisalignedbb4 = entityBoundingBox;
      BoundingBox axisalignedbb5 = axisalignedbb4.expand(startMotionX, 0.0D, startMotionZ);
      double d9 = motion.motionY;
      d9 = shape.allowedOffset(Y_AXIS, axisalignedbb5, d9);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, d9, 0.0D);
      double d15 = startMotionX;
      d15 = shape.allowedOffset(X_AXIS, axisalignedbb4, d15);
      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0D, 0.0D);
      double d16 = startMotionZ;
      d16 = shape.allowedOffset(Z_AXIS, axisalignedbb4, d16);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d16);
      BoundingBox axisalignedbb14 = entityBoundingBox;
      double d17 = motion.motionY;
      d17 = shape.allowedOffset(Y_AXIS, axisalignedbb14, d17);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;
      d18 = shape.allowedOffset(X_AXIS, axisalignedbb14, d18);
      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;
      d19 = shape.allowedOffset(Z_AXIS, axisalignedbb14, d19);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;
      if (d20 > d10) {
        motion.motionX = d15;
        motion.motionZ = d16;
        motion.motionY = -d9;
        entityBoundingBox = axisalignedbb4;
      } else {
        motion.motionX = d18;
        motion.motionZ = d19;
        motion.motionY = -d17;
        entityBoundingBox = axisalignedbb14;
      }
      motion.motionY = shape.allowedOffset(Y_AXIS, entityBoundingBox, motion.motionY);
      entityBoundingBox = entityBoundingBox.offset(0.0, motion.motionY, 0.0);
      if (copyX * copyX + copyZ * copyZ >= motion.motionX * motion.motionX + motion.motionZ * motion.motionZ) {
        motion.motionX = copyX;
        motion.motionY = copyY;
        motion.motionZ = copyZ;
        entityBoundingBox = axisalignedbb3;
      } else {
        step = true;
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
      Motion.copyFrom(motion), onGround,
      collidedHorizontally, collidedVertically,
      moveResetX, moveResetZ, step, edgeSneak
    );
  }
}