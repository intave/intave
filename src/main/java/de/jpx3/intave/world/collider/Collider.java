package de.jpx3.intave.world.collider;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;
import de.jpx3.intave.world.collider.complex.LegacyComplexColliderProcessor;
import de.jpx3.intave.world.collider.complex.ModernComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.world.collider.simple.UniversalSimpleColliderProcessor;
import org.bukkit.entity.Player;

public final class Collider {
  private static ComplexColliderProcessor legacyCollisionResolver;
  private static ComplexColliderProcessor modernCollisionResolver;
  private static SimpleColliderProcessor simpleColliderProcessor;

  private Collider() {
  }

  public static void setup() {
    legacyCollisionResolver = new LegacyComplexColliderProcessor();
    modernCollisionResolver = new ModernComplexColliderProcessor();
    simpleColliderProcessor = new UniversalSimpleColliderProcessor();
  }

  public static ComplexColliderProcessor suitableComplexColliderProcessorFor(User user) {
    UserMetaClientData clientData = user.meta().clientData();
    return clientData.applyModernCollider() ? modernCollisionResolver : legacyCollisionResolver;
  }

  public static SimpleColliderProcessor suitableSimpleColliderProcessorFor(User user) {
    return simpleColliderProcessor;
  }

  public static ComplexColliderSimulationResult simulateComplexCollision(
    User user, MotionVector context, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    return user.complexColliderProcessor().simulateCollision(user, context, inWeb, positionX, positionY, positionZ);
  }

  public static SimpleColliderSimulationResult simulateSimpleCollision(
    Player player,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    User user = UserRepository.userOf(player);
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(positionX, positionY, positionZ);
    return user.simpleColliderProcessor().simulateCollision(user, boundingBox, motionX, motionY, motionZ);
  }
}