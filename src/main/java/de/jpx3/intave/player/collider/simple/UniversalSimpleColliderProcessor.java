package de.jpx3.intave.player.collider.simple;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

public final class UniversalSimpleColliderProcessor implements SimpleColliderProcessor {
  @Override
  public SimpleColliderSimulationResult collide(User user, BoundingBox boundingBox, double motionX, double motionY, double motionZ) {
    Player player = user.player();
    BlockShape collider = Collision.colliderShapeIn(
      player, boundingBox.addCoord(motionX, motionY, motionZ)
    );
    double startMotionY = motionY;
    motionY = collider.allowedYOffset(boundingBox, motionY);
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    motionX = collider.allowedXOffset(boundingBox, motionX);
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    motionZ = collider.allowedZOffset(boundingBox, motionZ);
    return new SimpleColliderSimulationResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }
}
