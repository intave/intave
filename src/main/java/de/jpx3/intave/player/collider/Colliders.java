package de.jpx3.intave.player.collider;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.player.collider.complex.*;
import de.jpx3.intave.player.collider.simple.SimpleCollider;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.player.collider.simple.UniversalSimpleCollider;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class Colliders {
  private static final Collider V7_COMPLEX_COLLIDER;
  private static final Collider V8_COMPLEX_COLLIDER;
  private static final Collider V14_COMPLEX_COLLIDER;
  private static final Collider V21_COMPLEX_COLLIDER;
  private static final SimpleCollider UNIVERSAL_SIMPLE_COLLIDER;

  private Colliders() {
  }

  static {
    V7_COMPLEX_COLLIDER = new v8Collider();
    V8_COMPLEX_COLLIDER = new v8Collider();
    V14_COMPLEX_COLLIDER = new v14Collider();
    V21_COMPLEX_COLLIDER = new v21Collider();
    UNIVERSAL_SIMPLE_COLLIDER = new UniversalSimpleCollider();
  }

  public static Collider suitableComplexColliderProcessorFor(User user) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.protocolVersion() >= ProtocolMetadata.VER_1_21) {
      return V21_COMPLEX_COLLIDER;
//      return V14_COMPLEX_COLLIDER;
    } else if (clientData.applyModernCollider()) {
      return V14_COMPLEX_COLLIDER;
    } else if (clientData.protocolVersion() >= VER_1_8) {
      return V8_COMPLEX_COLLIDER;
    } else {
      return V7_COMPLEX_COLLIDER;
    }
  }

  public static SimpleCollider suitableSimpleColliderProcessorFor(User user) {
    return UNIVERSAL_SIMPLE_COLLIDER;
  }

  public static Collider anyCollider() {
    return V8_COMPLEX_COLLIDER;
  }

  public static SimpleCollider anySimpleCollider() {
    return UNIVERSAL_SIMPLE_COLLIDER;
  }

  public static ColliderResult collision(
    User user, SimulationEnvironment environment,
    Motion motion, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    // Apply motion multiplier
    Vector motionMultiplier = user.meta().movement().motionMultiplier();
    if (motionMultiplier != null) {
      motion.motionX *= motionMultiplier.getX();
      motion.motionY *= motionMultiplier.getY();
      motion.motionZ *= motionMultiplier.getZ();
    }

    return user.collider().collide(user, environment, motion, positionX, positionY, positionZ, inWeb);
  }

  public static SimpleColliderResult simplifiedCollision(
    Player player,
    SimulationEnvironment environment,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    User user = UserRepository.userOf(player);
    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, positionX, positionY, positionZ);
    SimpleCollider simpleCollider = user.simplifiedCollider();
    return simpleCollider.collide(user, environment, boundingBox, Motion.of(motionX, motionY, motionZ));
  }
}