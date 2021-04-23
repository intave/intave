package de.jpx3.intave.world.collider;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.world.collider.processor.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.processor.LegacyComplexColliderProcessor;
import de.jpx3.intave.world.collider.processor.NewComplexColliderProcessor;
import de.jpx3.intave.world.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.world.collider.result.QuickColliderSimulationResult;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public final class Collider {
  private static ComplexColliderProcessor legacyCollisionResolver;
  private static ComplexColliderProcessor newCollisionResolver;

  private Collider() {
  }

  public static void setup() {
    legacyCollisionResolver = new LegacyComplexColliderProcessor();
    newCollisionResolver = new NewComplexColliderProcessor();
  }

  public static ComplexColliderProcessor suitableComplexColliderProcessorFor(User user) {
    UserMetaClientData clientData = user.meta().clientData();
    return clientData.applyNewEntityCollisions() ? newCollisionResolver : legacyCollisionResolver;
  }

  public static ComplexColliderSimulationResult simulateComplexCollision(
    User user, MotionVector context, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    return user.colliderProcessor().simulateCollision(user, context, inWeb, positionX, positionY, positionZ);
  }

  @Deprecated
  // please remove this method, must have at least player to simulate accurate collisions ~richy
  public static QuickColliderSimulationResult simulateQuickCollision(
    World world,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(positionX, positionY, positionZ);
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(
      world,
      boundingBox.addCoord(motionX, motionY, motionZ)
    );
    return performSimpleBoxCollision(boundingBox, collisionBoxes, motionX, motionY, motionZ);
  }

  public static QuickColliderSimulationResult simulateQuickCollision(
    Player player,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(positionX, positionY, positionZ);
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(
      player,
      boundingBox.addCoord(motionX, motionY, motionZ)
    );
    return performSimpleBoxCollision(boundingBox, collisionBoxes, motionX, motionY, motionZ);
  }

  private static QuickColliderSimulationResult performSimpleBoxCollision(
    WrappedAxisAlignedBB boundingBox,
    List<WrappedAxisAlignedBB> collisionBoxes,
    double motionX, double motionY, double motionZ
  ) {
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
    return new QuickColliderSimulationResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }
}