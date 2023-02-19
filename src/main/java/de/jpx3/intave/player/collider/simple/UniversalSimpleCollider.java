package de.jpx3.intave.player.collider.simple;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class UniversalSimpleCollider implements SimpleCollider {
  @Override
  public SimpleColliderResult collide(User user, BoundingBox boundingBox, double motionX, double motionY, double motionZ) {
    Player player = user.player();
    BlockShape collider = Collision.shape(
      player, boundingBox.expand(motionX, motionY, motionZ)
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
  public SimpleColliderResult collide(User user, BoundingBox boundingBox, Motion motion) {
    Player player = user.player();
    double motionX = motion.motionX;
    double motionY = motion.motionY;
    double motionZ = motion.motionZ;
    BlockShape collider = Collision.shape(
      player, boundingBox.expand(motionX, motionY, motionZ)
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
