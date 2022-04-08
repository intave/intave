package de.jpx3.intave.player.collider;

import de.jpx3.intave.player.collider.complex.ColliderProcessor;
import de.jpx3.intave.player.collider.complex.ColliderSimulationResult;
import de.jpx3.intave.player.collider.complex.v14ColliderProcessor;
import de.jpx3.intave.player.collider.complex.v8ColliderProcessor;
import de.jpx3.intave.player.collider.simple.SimpleColliderProcessor;
import de.jpx3.intave.player.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.player.collider.simple.UniversalSimpleColliderProcessor;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Motion;
import de.jpx3.intave.shade.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class Collider {
  private final static ColliderProcessor v7ComplexColliderProcessor;
  private static final ColliderProcessor v8ComplexColliderProsessor;
  private static final ColliderProcessor v14ComplexColliderProcessor;
  private static final SimpleColliderProcessor universalSimpleColliderProcessor;

  private Collider() {
  }

  static {
    v7ComplexColliderProcessor = new v8ColliderProcessor();//new v7ColliderProcessor();
    v8ComplexColliderProsessor = new v8ColliderProcessor();
    v14ComplexColliderProcessor = new v14ColliderProcessor();
    universalSimpleColliderProcessor = new UniversalSimpleColliderProcessor();
  }

  public static ColliderProcessor suitableComplexColliderProcessorFor(User user) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.applyModernCollider()) {
      return v14ComplexColliderProcessor;
    } else if (clientData.protocolVersion() >= VER_1_8) {
      return v8ComplexColliderProsessor;
    } else {
      return v7ComplexColliderProcessor;
    }
  }

  public static SimpleColliderProcessor suitableSimpleColliderProcessorFor(User user) {
    return universalSimpleColliderProcessor;
  }

  public static ColliderSimulationResult collision(
    User user, Motion motion, boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    return user.collider().collide(user, motion, inWeb, positionX, positionY, positionZ);
  }

  public static SimpleColliderSimulationResult simplifiedCollision(
    Player player,
    Position position,
    Motion motion
  ) {
    User user = UserRepository.userOf(player);
    BoundingBox boundingBox = BoundingBox.fromPosition(user, position);
    return user.simplifiedCollider().collide(user, boundingBox, motion);
  }

  public static SimpleColliderSimulationResult simplifiedCollision(
    Player player,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    User user = UserRepository.userOf(player);
    BoundingBox boundingBox = BoundingBox.fromPosition(user, positionX, positionY, positionZ);
    SimpleColliderProcessor simpleColliderProcessor = user.simplifiedCollider();
    return simpleColliderProcessor.collide(user, boundingBox, motionX, motionY, motionZ);
  }
}