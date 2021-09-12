package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.MotionVector;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

public final class LegacyComplexColliderProcessor implements ComplexColliderProcessor {
  @Override
  public ComplexColliderSimulationResult collide(User user, MotionVector context, boolean inWeb, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    if (inWeb) {
      context.motionX *= 0.25D;
      context.motionY *= 0.05f;
      context.motionZ *= 0.25D;
    }
    double startMotionX = context.motionX;
    double startMotionY = context.motionY;
    double startMotionZ = context.motionZ;
    boolean step = false;
    if (movementData.onGround && movementData.sneaking) {
      BoundingBox boundingBox = movementData.boundingBox();
      double size;
      for (size = 0.05D; context.motionX != 0.0D && Collision.nonePresent(player, boundingBox.offset(context.motionX, -1.0D, 0.0D)); startMotionX = context.motionX) {
        if (context.motionX < size && context.motionX >= -size) {
          context.motionX = 0.0D;
        } else if (context.motionX > 0.0D) {
          context.motionX -= size;
        } else {
          context.motionX += size;
        }
      }
      for (; context.motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(0.0D, -1.0D, context.motionZ)); startMotionZ = context.motionZ) {
        if (context.motionZ < size && context.motionZ >= -size) {
          context.motionZ = 0.0D;
        } else if (context.motionZ > 0.0D) {
          context.motionZ -= size;
        } else {
          context.motionZ += size;
        }
      }
      for (; context.motionX != 0.0D && context.motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(context.motionX, -1.0D, context.motionZ)); startMotionZ = context.motionZ) {
        if (context.motionX < size && context.motionX >= -size) {
          context.motionX = 0.0D;
        } else if (context.motionX > 0.0D) {
          context.motionX -= size;
        } else {
          context.motionX += size;
        }
        startMotionX = context.motionX;
        if (context.motionZ < size && context.motionZ >= -size) {
          context.motionZ = 0.0D;
        } else if (context.motionZ > 0.0D) {
          context.motionZ -= size;
        } else {
          context.motionZ += size;
        }
      }
    }
    BlockShape collisionShape = Collision.colliderShapeIn(player, movementData.boundingBox().addCoord(context.motionX, context.motionY, context.motionZ));
    BoundingBox startBoundingBox = movementData.boundingBox();
    BoundingBox entityBoundingBox = movementData.boundingBox();
    context.motionY = collisionShape.allowedYOffset(entityBoundingBox, context.motionY);
    entityBoundingBox = (entityBoundingBox.offset(0.0D, context.motionY, 0.0D));
    boolean flag1 = movementData.onGround || startMotionY != context.motionY && startMotionY < 0.0D;
    context.motionX = collisionShape.allowedXOffset(entityBoundingBox, context.motionX);
    entityBoundingBox = entityBoundingBox.offset(context.motionX, 0.0D, 0.0D);
    context.motionZ = collisionShape.allowedZOffset(entityBoundingBox, context.motionZ);
    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);
    if (flag1 && (startMotionX != context.motionX || startMotionZ != context.motionZ)) {
      double copyX = context.motionX;
      double copyY = context.motionY;
      double copyZ = context.motionZ;
      BoundingBox axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      context.motionY = STEP_HEIGHT;
      BlockShape collider = Collision.colliderShapeIn(player, entityBoundingBox.addCoord(startMotionX, context.motionY, startMotionZ));
      BoundingBox axisalignedbb4 = entityBoundingBox;
      BoundingBox axisalignedbb5 = axisalignedbb4.addCoord(startMotionX, 0.0D, startMotionZ);
      double d9 = context.motionY;
      d9 = collider.allowedYOffset(axisalignedbb5, d9);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, d9, 0.0D);
      double d15 = startMotionX;
      d15 = collider.allowedXOffset(axisalignedbb4, d15);
      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0D, 0.0D);
      double d16 = startMotionZ;
      d16 = collider.allowedZOffset(axisalignedbb4, d16);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d16);
      BoundingBox axisalignedbb14 = entityBoundingBox;
      double d17 = context.motionY;
      d17 = collider.allowedYOffset(axisalignedbb14, d17);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;
      d18 = collider.allowedXOffset(axisalignedbb14, d18);
      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;
      d19 = collider.allowedZOffset(axisalignedbb14, d19);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;
      if (d20 > d10) {
        context.motionX = d15;
        context.motionZ = d16;
        context.motionY = -d9;
        entityBoundingBox = axisalignedbb4;
      } else {
        context.motionX = d18;
        context.motionZ = d19;
        context.motionY = -d17;
        entityBoundingBox = axisalignedbb14;
      }
      context.motionY = collider.allowedYOffset(entityBoundingBox, context.motionY);
      entityBoundingBox = entityBoundingBox.offset(0.0, context.motionY, 0.0);
      if (copyX * copyX + copyZ * copyZ >= context.motionX * context.motionX + context.motionZ * context.motionZ) {
        context.motionX = copyX;
        context.motionY = copyY;
        context.motionZ = copyZ;
        entityBoundingBox = axisalignedbb3;
      } else {
        step = true;
      }
    }
    boolean collidedVertically = startMotionY != context.motionY;
    boolean collidedHorizontally = startMotionX != context.motionX || startMotionZ != context.motionZ;
    boolean onGround = startMotionY != context.motionY && startMotionY < 0.0;
    boolean moveResetX = startMotionX != context.motionX;
    boolean moveResetZ = startMotionZ != context.motionZ;
    double newPositionX = (entityBoundingBox.minX + entityBoundingBox.maxX) / 2.0D;
    double newPositionY = entityBoundingBox.minY;
    double newPositionZ = (entityBoundingBox.minZ + entityBoundingBox.maxZ) / 2.0D;
    context.motionX = newPositionX - positionX;
    context.motionY = newPositionY - positionY;
    context.motionZ = newPositionZ - positionZ;
    return new ComplexColliderSimulationResult(
      MotionVector.from(context), onGround,
      collidedHorizontally, collidedVertically, moveResetX, moveResetZ, step
    );
  }
}