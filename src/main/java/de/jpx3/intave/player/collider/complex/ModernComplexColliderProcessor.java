package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.MotionVector;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

public final class ModernComplexColliderProcessor implements ComplexColliderProcessor {
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
    if (movementData.onGround && movementData.sneaking) {
      calculateBackOffFromEdge(user, STEP_HEIGHT, context);
    }
    double startMotionX = context.motionX;
    double startMotionY = context.motionY;
    double startMotionZ = context.motionZ;
    boolean step = false;
    BlockShape firstCollider = Collision.colliderShapeIn(
      player, movementData.boundingBox().addCoord(context.motionX, context.motionY, context.motionZ)
    );
    BoundingBox startBoundingBox = movementData.boundingBox();
    BoundingBox entityBoundingBox = movementData.boundingBox();
    if (context.motionY != 0.0) {
      context.motionY = firstCollider.allowedYOffset(entityBoundingBox, context.motionY);
      if (context.motionY != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0D, context.motionY, 0.0D);
      }
    }
    boolean flag = Math.abs(context.motionX) < Math.abs(context.motionZ);
    if (flag && context.motionZ != 0.0) {
      context.motionZ = firstCollider.allowedZOffset(entityBoundingBox, context.motionZ);
      if (context.motionZ != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);
      }
    }
    if (context.motionX != 0.0) {
      context.motionX = firstCollider.allowedXOffset(entityBoundingBox, context.motionX);
      if (context.motionX != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(context.motionX, 0.0D, 0.0D);
      }
    }
    if (!flag && context.motionZ != 0.0) {
      context.motionZ = firstCollider.allowedZOffset(entityBoundingBox, context.motionZ);
      if (context.motionZ != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);
      }
    }
    boolean flag1 = movementData.onGround || startMotionY != context.motionY && startMotionY < 0.0D;
    if (flag1 && (startMotionX != context.motionX || startMotionZ != context.motionZ)) {
      double copyX = context.motionX;
      double copyY = context.motionY;
      double copyZ = context.motionZ;
      BoundingBox boundingBox3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      context.motionY = STEP_HEIGHT;
      BlockShape secondCollider = Collision.colliderShapeIn(
        player,
        entityBoundingBox.addCoord(startMotionX, context.motionY, startMotionZ)
      );
      BoundingBox boundingBox4 = entityBoundingBox;
      BoundingBox boundingBox5 = boundingBox4.addCoord(startMotionX, 0.0D, startMotionZ);
      double d9 = context.motionY;
      d9 = secondCollider.allowedYOffset(boundingBox5, d9);
      boundingBox4 = boundingBox4.offset(0.0D, d9, 0.0D);
      double d16 = startMotionZ;
      d16 = secondCollider.allowedZOffset(boundingBox4, d16);
      boundingBox4 = boundingBox4.offset(0.0D, 0.0D, d16);
      double d15 = startMotionX;
      d15 = secondCollider.allowedXOffset(boundingBox4, d15);
      boundingBox4 = boundingBox4.offset(d15, 0.0D, 0.0D);
      BoundingBox boundingBox14 = entityBoundingBox;
      double d17 = context.motionY;
      d17 = secondCollider.allowedYOffset(boundingBox14, d17);
      boundingBox14 = boundingBox14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;
      d18 = secondCollider.allowedXOffset(boundingBox14, d18);
      boundingBox14 = boundingBox14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;
      d19 = secondCollider.allowedZOffset(boundingBox14, d19);
      boundingBox14 = boundingBox14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;
      if (d20 > d10) {
        context.motionX = d15;
        context.motionZ = d16;
        context.motionY = -d9;
        entityBoundingBox = boundingBox4;
      } else {
        context.motionX = d18;
        context.motionZ = d19;
        context.motionY = -d17;
        entityBoundingBox = boundingBox14;
      }
      context.motionY = secondCollider.allowedYOffset(entityBoundingBox, context.motionY);
      entityBoundingBox = entityBoundingBox.offset(0.0, context.motionY, 0.0);
      if (copyX * copyX + copyZ * copyZ >= context.motionX * context.motionX + context.motionZ * context.motionZ) {
        context.motionX = copyX;
        context.motionY = copyY;
        context.motionZ = copyZ;
        entityBoundingBox = boundingBox3;
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
      collidedHorizontally, collidedVertically,
      moveResetX, moveResetZ, step
    );
  }

  private void calculateBackOffFromEdge(User user, double length, MotionVector context) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    BoundingBox boundingBox = movementData.boundingBox();
    double motionX = context.motionX;
    double motionZ = context.motionZ;
    while (motionX != 0.0D && Collision.nonePresent(player, boundingBox.offset(motionX, -length, 0.0D))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
      } else {
        motionX += 0.05D;
      }
    }
    while (motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(0.0D, -length, motionZ))) {
      if (motionZ < 0.05D && motionZ >= -0.05D) {
        motionZ = 0.0D;
      } else if (motionZ > 0.0D) {
        motionZ -= 0.05D;
      } else {
        motionZ += 0.05D;
      }
    }
    while (motionX != 0.0D && motionZ != 0.0D && Collision.nonePresent(player, boundingBox.offset(motionX, -length, motionZ))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
      } else {
        motionX += 0.05D;
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
  }
}