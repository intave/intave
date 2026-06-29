package de.jpx3.intave.player.collider.simple;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class UniversalSimpleCollider implements SimpleCollider {
  @Override
  public SimpleColliderResult collide(User user, BoundingBox boundingBox, double motionX, double motionY, double motionZ) {
    BlockShape collider = Collision.shape(
      user, user.meta().movement(), boundingBox.expand(motionX, motionY, motionZ)
    );
    double startMotionY = motionY;
    motionY = collider.allowedOffset(Y_AXIS, boundingBox, motionY);
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    motionX = collider.allowedOffset(X_AXIS, boundingBox, motionX);
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    motionZ = collider.allowedOffset(Z_AXIS, boundingBox, motionZ);
    return new SimpleColliderResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }

  @Override
  public SimpleColliderResult collide(
    User user, SimulationEnvironment environment,
    BoundingBox boundingBox, Motion motion
  ) {
    double motionX = motion.motionX;
    double motionY = motion.motionY;
    double motionZ = motion.motionZ;
    BlockShape collider = Collision.shape(
      user, environment, boundingBox.expand(motionX, motionY, motionZ)
    );
    double startMotionY = motionY;
    motionY = collider.allowedOffset(Y_AXIS, boundingBox, motionY);
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    motionX = collider.allowedOffset(X_AXIS, boundingBox, motionX);
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    motionZ = collider.allowedOffset(Z_AXIS, boundingBox, motionZ);
    return new SimpleColliderResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }
}
