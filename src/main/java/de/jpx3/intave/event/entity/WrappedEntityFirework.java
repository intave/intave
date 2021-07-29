package de.jpx3.intave.event.entity;

import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.reflect.hitbox.typeaccess.EntityTypeData;
import de.jpx3.intave.tools.client.RotationHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.util.Vector;

public final class WrappedEntityFirework extends WrappedEntity {
  private final User attachedUser;

  public WrappedEntityFirework(
    User attachedUser,
    int entityId,
    EntityTypeData entityTypeData
  ) {
    super(entityId, entityTypeData, false, false);
    this.attachedUser = attachedUser;
  }

  @Override
  public void entityPlayerMoveUpdate() {
    UserMetaMovementData movementData = this.attachedUser.meta().movementData();
    if (movementData.pose() == Pose.FALL_FLYING) {
      if (this.ticksAlive <= 1) {
        movementData.fireworkTolerant = true;
      }
      applyElytraBoost();
    }
  }

  private void applyElytraBoost() {
    UserMetaMovementData movementData = this.attachedUser.meta().movementData();
    Vector lookVector = RotationHelper.vectorForRotation(movementData.rotationPitch, movementData.rotationYaw);
    movementData.physicsMotionX += lookVector.getX() * 0.1 + (lookVector.getX() * 1.5 - movementData.physicsMotionX) * 0.5;
    movementData.physicsMotionY += lookVector.getY() * 0.1 + (lookVector.getY() * 1.5 - movementData.physicsMotionY) * 0.5;
    movementData.physicsMotionZ += lookVector.getZ() * 0.1 + (lookVector.getZ() * 1.5 - movementData.physicsMotionZ) * 0.5;
  }
}