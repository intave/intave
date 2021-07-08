package de.jpx3.intave.world.collider.simple;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.entity.Player;

import java.util.List;

public final class UniversalSimpleColliderProcessor implements SimpleColliderProcessor {
  @Override
  public SimpleColliderSimulationResult simulateCollision(User user, WrappedAxisAlignedBB boundingBox, double motionX, double motionY, double motionZ) {
    Player player = user.player();
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(
      player, boundingBox.addCoord(motionX, motionY, motionZ)
    );
    double startMotionY = motionY;
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionY = collisionBox.calculateYOffset(boundingBox, motionY);
    }
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionX = collisionBox.calculateXOffset(boundingBox, motionX);
    }
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionZ = collisionBox.calculateZOffset(boundingBox, motionZ);
    }
    return new SimpleColliderSimulationResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }
}
