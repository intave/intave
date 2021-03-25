package de.jpx3.intave.detect.checks.movement.physics.collider;

import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;
import de.jpx3.intave.detect.checks.movement.physics.collider.processor.ComplexColliderProcessor;
import de.jpx3.intave.detect.checks.movement.physics.collider.processor.LegacyComplexColliderProcessor;
import de.jpx3.intave.detect.checks.movement.physics.collider.processor.NewComplexColliderProcessor;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.QuickColliderSimulationResult;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.world.collision.Collision;
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

  public static ComplexColliderSimulationResult simulateComplexCollision(
    User user, ProcessorMotionContext context, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    UserMetaClientData clientData = user.meta().clientData();
    return clientData.applyNewEntityCollisions()
      ? newCollisionResolver.simulateCollision(user, context, inWeb, positionX, positionY, positionZ)
      : legacyCollisionResolver.simulateCollision(user, context, inWeb, positionX, positionY, positionZ);
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