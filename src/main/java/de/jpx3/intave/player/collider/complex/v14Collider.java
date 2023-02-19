package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class v14Collider implements Collider {
  @Override
  public ColliderResult collide(
    User user,
    Motion motion,
    double positionX,
    double positionY,
    double positionZ,
    boolean inWeb
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    if (inWeb) {
      motion.motionX *= 0.25D;
      motion.motionY *= 0.05f;
      motion.motionZ *= 0.25D;
    }
    boolean edgeSneak = false;
    if (movement.onGround() && movement.isSneaking()) {
      edgeSneak = calculateBackOffFromEdge(user, movement.stepHeight, motion);
    }
    double startMotionX = motion.motionX();
    double startMotionY = motion.motionY();
    double startMotionZ = motion.motionZ();
    boolean step = false;
    BlockShape collisionShape = Collision.shape(
      player, movement.boundingBox().expand(motion.motionX, motion.motionY, motion.motionZ)
    );
    BoundingBox startBoundingBox = movement.boundingBox();
    BoundingBox shiftedBoundingBox = movement.boundingBox();
    if (motion.motionY != 0.0) {
      motion.motionY = collisionShape.allowedOffset(Y_AXIS, shiftedBoundingBox, motion.motionY);
      if (motion.motionY != 0.0) {
        shiftedBoundingBox = shiftedBoundingBox.offset(0.0D, motion.motionY, 0.0D);
      }
    }
    boolean flag = Math.abs(motion.motionX) < Math.abs(motion.motionZ);
    if (flag && motion.motionZ != 0.0) {
      motion.motionZ = collisionShape.allowedOffset(Z_AXIS, shiftedBoundingBox, motion.motionZ);
      if (motion.motionZ != 0.0) {
        shiftedBoundingBox = shiftedBoundingBox.offset(0.0, 0.0, motion.motionZ);
      }
    }
    if (motion.motionX != 0.0) {
      motion.motionX = collisionShape.allowedOffset(X_AXIS, shiftedBoundingBox, motion.motionX);
      if (motion.motionX != 0.0) {
        shiftedBoundingBox = shiftedBoundingBox.offset(motion.motionX, 0.0D, 0.0D);
      }
    }
    if (!flag && motion.motionZ != 0.0) {
      motion.motionZ = collisionShape.allowedOffset(Z_AXIS, shiftedBoundingBox, motion.motionZ);
      if (motion.motionZ != 0.0) {
        shiftedBoundingBox = shiftedBoundingBox.offset(0.0, 0.0, motion.motionZ);
      }
    }
    boolean flag1 = movement.onGround || startMotionY != motion.motionY && startMotionY < 0.0D;
    if (flag1 && (startMotionX != motion.motionX || startMotionZ != motion.motionZ)) {
      double copyX = motion.motionX;
      double copyY = motion.motionY;
      double copyZ = motion.motionZ;
      BoundingBox boundingBox3 = shiftedBoundingBox;
      shiftedBoundingBox = startBoundingBox;
      motion.motionY = movement.stepHeight;
      BlockShape stepCollisionShape = Collision.shape(
        player, shiftedBoundingBox.expand(startMotionX, motion.motionY, startMotionZ)
      );
      BoundingBox boundingBox4 = shiftedBoundingBox;
      BoundingBox boundingBox5 = boundingBox4.expand(startMotionX, 0.0D, startMotionZ);
      double d9 = motion.motionY;
      d9 = stepCollisionShape.allowedOffset(Y_AXIS, boundingBox5, d9);
      boundingBox4 = boundingBox4.offset(0.0D, d9, 0.0D);
      double d16 = startMotionZ;
      d16 = stepCollisionShape.allowedOffset(Z_AXIS, boundingBox4, d16);
      boundingBox4 = boundingBox4.offset(0.0D, 0.0D, d16);
      double d15 = startMotionX;
      d15 = stepCollisionShape.allowedOffset(X_AXIS, boundingBox4, d15);
      boundingBox4 = boundingBox4.offset(d15, 0.0D, 0.0D);
      BoundingBox boundingBox14 = shiftedBoundingBox;
      double d17 = motion.motionY;
      d17 = stepCollisionShape.allowedOffset(Y_AXIS, boundingBox14, d17);
      boundingBox14 = boundingBox14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;
      d18 = stepCollisionShape.allowedOffset(X_AXIS, boundingBox14, d18);
      boundingBox14 = boundingBox14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;
      d19 = stepCollisionShape.allowedOffset(Z_AXIS, boundingBox14, d19);
      boundingBox14 = boundingBox14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;
      if (d20 > d10) {
        motion.motionX = d15;
        motion.motionZ = d16;
        motion.motionY = -d9;
        shiftedBoundingBox = boundingBox4;
      } else {
        motion.motionX = d18;
        motion.motionZ = d19;
        motion.motionY = -d17;
        shiftedBoundingBox = boundingBox14;
      }
      motion.motionY = stepCollisionShape.allowedOffset(Y_AXIS, shiftedBoundingBox, motion.motionY);
      shiftedBoundingBox = shiftedBoundingBox.offset(0.0, motion.motionY, 0.0);
      if (copyX * copyX + copyZ * copyZ >= motion.motionX * motion.motionX + motion.motionZ * motion.motionZ) {
        motion.motionX = copyX;
        motion.motionY = copyY;
        motion.motionZ = copyZ;
        shiftedBoundingBox = boundingBox3;
      } else {
        step = true;
      }
    }
    boolean collidedVertically = startMotionY != motion.motionY;
    boolean collidedHorizontally = startMotionX != motion.motionX || startMotionZ != motion.motionZ;
    boolean onGround = startMotionY != motion.motionY && startMotionY < 0.0;
    boolean moveResetX = startMotionX != motion.motionX;
    boolean moveResetZ = startMotionZ != motion.motionZ;
    double newPositionX = (shiftedBoundingBox.minX + shiftedBoundingBox.maxX) / 2.0D;
    double newPositionY = shiftedBoundingBox.minY;
    double newPositionZ = (shiftedBoundingBox.minZ + shiftedBoundingBox.maxZ) / 2.0D;
    motion.motionX = newPositionX - positionX;
    motion.motionY = newPositionY - positionY;
    motion.motionZ = newPositionZ - positionZ;
    return new ColliderResult(
      Motion.copyFrom(motion),
      onGround,
      collidedHorizontally,
      collidedVertically,
      moveResetX,
      moveResetZ,
      step, edgeSneak
    );
  }

  private boolean calculateBackOffFromEdge(User user, double length, Motion context) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    BoundingBox boundingBox = movementData.boundingBox();
    double motionX = context.motionX;
    double motionZ = context.motionZ;
    boolean edgeSneak = false;
    while (motionX != 0.0D
      && Collision.nonePresent(player, boundingBox.offset(motionX, -length, 0.0D))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
        edgeSneak = true;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
        edgeSneak = true;
      } else {
        motionX += 0.05D;
        edgeSneak = true;
      }
    }
    while (motionZ != 0.0D
      && Collision.nonePresent(player, boundingBox.offset(0.0D, -length, motionZ))) {
      if (motionZ < 0.05D && motionZ >= -0.05D) {
        motionZ = 0.0D;
        edgeSneak = true;
      } else if (motionZ > 0.0D) {
        motionZ -= 0.05D;
        edgeSneak = true;
      } else {
        motionZ += 0.05D;
        edgeSneak = true;
      }
    }
    while (motionX != 0.0D
      && motionZ != 0.0D
      && Collision.nonePresent(player, boundingBox.offset(motionX, -length, motionZ))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
        edgeSneak = true;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
        edgeSneak = true;
      } else {
        motionX += 0.05D;
        edgeSneak = true;
      }
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
}
