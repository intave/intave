package de.jpx3.intave.world.collider;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.collider.complex.ComplexColliderProcessor;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;
import de.jpx3.intave.world.collider.complex.LegacyComplexColliderProcessor;
import de.jpx3.intave.world.collider.complex.ModernComplexColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderProcessor;
import de.jpx3.intave.world.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.world.collider.simple.UniversalSimpleColliderProcessor;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.entity.Player;

public final class Collider {
  private static final ComplexColliderProcessor legacyComplexCollisionResolver;
  private static final ComplexColliderProcessor modernComplexCollisionResolver;
  private static final SimpleColliderProcessor universalSimpleColliderProcessor;

  private Collider() {
  }

  static {
    legacyComplexCollisionResolver = new LegacyComplexColliderProcessor();
    modernComplexCollisionResolver = new ModernComplexColliderProcessor();
    universalSimpleColliderProcessor = new UniversalSimpleColliderProcessor();
  }

  public static ComplexColliderProcessor suitableComplexColliderProcessorFor(User user) {
    ProtocolMetadata clientData = user.meta().protocol();
    return clientData.applyModernCollider() ? modernComplexCollisionResolver : legacyComplexCollisionResolver;
  }

  public static SimpleColliderProcessor suitableSimpleColliderProcessorFor(User user) {
    return universalSimpleColliderProcessor;
  }

  public static ComplexColliderSimulationResult simulateComplexCollision(
    User user, MotionVector context, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    return user.complexColliderProcessor().collide(user, context, inWeb, positionX, positionY, positionZ);
  }

  public static SimpleColliderSimulationResult simulateSimpleCollision(
    Player player,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    User user = UserRepository.userOf(player);
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(positionX, positionY, positionZ);
    SimpleColliderProcessor simpleColliderProcessor = user.simpleColliderProcessor();
    return simpleColliderProcessor.collide(user, boundingBox, motionX, motionY, motionZ);
  }
}