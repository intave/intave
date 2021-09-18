package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.shade.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.util.Vector;

public final class WrappedEntityFirework extends WrappedEntity {
  private final User attachedUser;

  public WrappedEntityFirework(
    User attachedUser,
    int entityId,
    EntityTypeData entityTypeData
  ) {
    super(entityId, entityTypeData, false);
    this.attachedUser = attachedUser;
  }

  @Override
  public void entityPlayerMoveUpdate() {
    MovementMetadata movementData = this.attachedUser.meta().movement();
    if (movementData.pose() == Pose.FALL_FLYING) {
      if (this.ticksAlive <= 2) {
        movementData.fireworkTolerant = true;
      }
      movementData.onFirework = true;
      applyElytraBoost();
    }
  }

  private void applyElytraBoost() {
    MovementMetadata movementData = this.attachedUser.meta().movement();
    Vector lookVector = vectorForRotation(movementData.rotationYaw, movementData.rotationPitch);
    movementData.physicsMotionX += lookVector.getX() * 0.1 + (lookVector.getX() * 1.5 - movementData.physicsMotionX) * 0.5;
    movementData.physicsMotionY += lookVector.getY() * 0.1 + (lookVector.getY() * 1.5 - movementData.physicsMotionY) * 0.5;
    movementData.physicsMotionZ += lookVector.getZ() * 0.1 + (lookVector.getZ() * 1.5 - movementData.physicsMotionZ) * 0.5;
  }

  private Vector vectorForRotation(float yaw, float pitch) {
    float f = pitch * ((float)Math.PI / 180F);
    float f1 = -yaw * ((float)Math.PI / 180F);
    float f2 = WrappedMathHelper.cos(f1);
    float f3 = WrappedMathHelper.sin(f1);
    float f4 = WrappedMathHelper.cos(f);
    float f5 = WrappedMathHelper.sin(f);
    return new Vector(f3 * f4, -f5, (double)(f2 * f4));
  }
}