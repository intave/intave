package de.jpx3.intave.world.collider.complex;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.entity.Player;

import java.util.List;

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
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(
      player, movementData.boundingBox().addCoord(context.motionX, context.motionY, context.motionZ)
    );
    WrappedAxisAlignedBB startBoundingBox = movementData.boundingBox();
    WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();
    if (context.motionY != 0.0) {
      for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
        context.motionY = collisionBox.calculateYOffset(entityBoundingBox, context.motionY);
      }
      if (context.motionY != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0D, context.motionY, 0.0D);
      }
    }
    boolean flag = Math.abs(context.motionX) < Math.abs(context.motionZ);
    if (flag && context.motionZ != 0.0) {
      for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
        context.motionZ = collisionBox.calculateZOffset(entityBoundingBox, context.motionZ);
      }
      if (context.motionZ != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);
      }
    }
    if (context.motionX != 0.0) {
      for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
        context.motionX = collisionBox.calculateXOffset(entityBoundingBox, context.motionX);
      }
      if (context.motionX != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(context.motionX, 0.0D, 0.0D);
      }
    }
    if (!flag && context.motionZ != 0.0) {
      for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
        context.motionZ = collisionBox.calculateZOffset(entityBoundingBox, context.motionZ);
      }
      if (context.motionZ != 0.0) {
        entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);
      }
    }
    boolean flag1 = movementData.onGround || startMotionY != context.motionY && startMotionY < 0.0D;
    if (flag1 && (startMotionX != context.motionX || startMotionZ != context.motionZ)) {
      double copyX = context.motionX;
      double copyY = context.motionY;
      double copyZ = context.motionZ;
      WrappedAxisAlignedBB axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      context.motionY = STEP_HEIGHT;
      List<WrappedAxisAlignedBB> list = Collision.resolve(
        player,
        entityBoundingBox.addCoord(startMotionX, context.motionY, startMotionZ)
      );
      WrappedAxisAlignedBB axisalignedbb4 = entityBoundingBox;
      WrappedAxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(startMotionX, 0.0D, startMotionZ);
      double d9 = context.motionY;
      for (WrappedAxisAlignedBB axisalignedbb6 : list) {
        d9 = axisalignedbb6.calculateYOffset(axisalignedbb5, d9);
      }
      axisalignedbb4 = axisalignedbb4.offset(0.0D, d9, 0.0D);
      double d16 = startMotionZ;
      for (WrappedAxisAlignedBB axisalignedbb8 : list) {
        d16 = axisalignedbb8.calculateZOffset(axisalignedbb4, d16);
      }
      axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d16);
      double d15 = startMotionX;
      for (WrappedAxisAlignedBB axisalignedbb7 : list) {
        d15 = axisalignedbb7.calculateXOffset(axisalignedbb4, d15);
      }
      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0D, 0.0D);
      WrappedAxisAlignedBB axisalignedbb14 = entityBoundingBox;
      double d17 = context.motionY;
      for (WrappedAxisAlignedBB axisalignedbb9 : list) {
        d17 = axisalignedbb9.calculateYOffset(axisalignedbb14, d17);
      }
      axisalignedbb14 = axisalignedbb14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;
      for (WrappedAxisAlignedBB axisalignedbb10 : list) {
        d18 = axisalignedbb10.calculateXOffset(axisalignedbb14, d18);
      }
      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;
      for (WrappedAxisAlignedBB axisalignedbb11 : list) {
        d19 = axisalignedbb11.calculateZOffset(axisalignedbb14, d19);
      }
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
      for (WrappedAxisAlignedBB axisalignedbb12 : list) {
        context.motionY = axisalignedbb12.calculateYOffset(entityBoundingBox, context.motionY);
      }
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
      collidedHorizontally, collidedVertically,
      moveResetX, moveResetZ, step
    );
  }

  private void calculateBackOffFromEdge(User user, double length, MotionVector context) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    WrappedAxisAlignedBB boundingBox = movementData.boundingBox();
    double motionX = context.motionX;
    double motionZ = context.motionZ;
    while (motionX != 0.0D && Collision.isNotInsideBlocks(player, boundingBox.offset(motionX, -length, 0.0D))) {
      if (motionX < 0.05D && motionX >= -0.05D) {
        motionX = 0.0D;
      } else if (motionX > 0.0D) {
        motionX -= 0.05D;
      } else {
        motionX += 0.05D;
      }
    }
    while (motionZ != 0.0D && Collision.isNotInsideBlocks(player, boundingBox.offset(0.0D, -length, motionZ))) {
      if (motionZ < 0.05D && motionZ >= -0.05D) {
        motionZ = 0.0D;
      } else if (motionZ > 0.0D) {
        motionZ -= 0.05D;
      } else {
        motionZ += 0.05D;
      }
    }
    while (motionX != 0.0D && motionZ != 0.0D && Collision.isNotInsideBlocks(player, boundingBox.offset(motionX, -length, motionZ))) {
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