package de.jpx3.intave.check.movement.physics.environment;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.check.movement.physics.MoveMetric;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import org.bukkit.Material;
import org.bukkit.util.Vector;

public interface SimulationEnvironment {
  Pose pose();
  Vector lookVector();

  void updateMovement(
	  double newPositionX, double newPositionY, double newPositionZ,
	  float newRotationYaw, float newRotationPitch,
	  boolean hasMovement, boolean hasRotation
  );

  default void updateMovement(
    @Nullable Position newPosition,
    @Nullable Rotation newRotation
  ) {
    boolean hasMovement = newPosition != null;
    boolean hasRotation = newRotation != null;
    updateMovement(
      hasMovement ? newPosition.getX() : 0,
      hasMovement ? newPosition.getY() : 0,
      hasMovement ? newPosition.getZ() : 0,
      hasRotation ? newRotation.yaw() : 0,
      hasRotation ? newRotation.pitch() : 0,
      hasMovement,
      hasRotation
    );
  }

  default Position position() {
    return new Position(positionX(), positionY(), positionZ());
  }
  double positionX();
  double positionY();
  double positionZ();

  default Position verifiedLastPosition() {
    return new Position(verifiedLastPositionX(), verifiedLastPositionY(), verifiedLastPositionZ());
  }
  double verifiedLastPositionX();
  double verifiedLastPositionY();
  double verifiedLastPositionZ();

  void setVerifiedLastPosition(Position position, String reason);

  default Position lastPosition() {
    return new Position(lastPositionX(), lastPositionY(), lastPositionZ());
  }
  double lastPositionX();
  double lastPositionY();
  double lastPositionZ();

  default void setLastPosition(Position position) {
    setLastPosition(position.getX(), position.getY(), position.getZ());
  }

  void setLastPosition(double x, double y, double z);

  void setBoundingBox(BoundingBox boundingBox);
  BoundingBox boundingBox();

  default Motion motion() {
    return new Motion(motionX(), motionY(), motionZ());
  }
  double motionX();
  double motionY();
  double motionZ();

  default Motion mutableBaseMotionCopy() {
    return new Motion(baseMotionX(), baseMotionY(), baseMotionZ());
  }
  double baseMotionX();
  double baseMotionY();
  double baseMotionZ();

  default void setBaseMotion(Motion baseMotion) {
    setBaseMotion(baseMotion.motionX(), baseMotion.motionY(), baseMotion.motionZ());
  }
  void setBaseMotion(
    double baseMotionX,
    double baseMotionY,
    double baseMotionZ
  );

  boolean motionXReset();
  boolean motionZReset();

  Vector motionMultiplier();
  void resetMotionMultiplier();

  float rotationYaw();
  float yawSine();
  float yawCosine();

  float rotationPitch();

  float aiMoveSpeed(boolean sprinting);
  float friction();
  double stepHeight();
  double resetMotion();

  double jumpMotion();
  void setJumpMotion(double jumpMotion);

  double gravity();

  float blockSpeedFactor();

  boolean isSneaking();
  boolean isSprinting();
  boolean inWater();
  void setInWater(boolean inWater);
  boolean inLava();
  boolean inWeb();
  void resetInWeb();
  boolean onGround();

  boolean lastOnGround();
  boolean collidedHorizontally();
  boolean collidedVertically();

  void checkSupportingBlock(Motion motion);

  boolean collidedWithBoat();
  double frictionPosSubtraction();
  boolean receivedFlyingPacketIn(int ticks);

  Material collideMaterial();
  Material frictionMaterial();
  Material previousCollideMaterial();
  Material previousFrictionMaterial();
  boolean blockOnPositionSoulSpeedAffected();

  double fallDistance();
  void resetFallDistance();

  boolean isInVehicle();
  void dismountRidingEntity(String boatSetback);

  void setPushedByEntity(boolean pushedByEntity);
  boolean pushedByEntity();

  void setBeforeMoveColliderResult(ColliderResult result);
  ColliderResult beforeMoveColliderResult();

  int ticks(MoveMetric metric);
  int ticksPast(MoveMetric metric);

  default void tick(MoveMetric metric, boolean active) {
    if (active) {
      activeTick(metric);
    } else {
      inactiveTick(metric);
    }
  }

  void activeTick(MoveMetric metric);
  void inactiveTick(MoveMetric metric);

  default void activeTick(MoveMetric first, MoveMetric... others) {
    activeTick(first);
    for (MoveMetric other : others) {
      activeTick(other);
    }
  }

  default void inactiveTick(MoveMetric first, MoveMetric... others) {
    inactiveTick(first);
    for (MoveMetric other : others) {
      inactiveTick(other);
    }
  }

  void resetPhysicsPacketRelinkFlyVL();

  void updateEyesInWater();
  void aquaticUpdateLavaReset();

  float height();
  float width();
  double heightRounded();
  double widthRounded();
  float eyeHeight();

  Fluid interactingFluid();

  void assumeOccurred(Simulation simulation);
  void tickComplete(boolean hasMovement, boolean hasRotation);

  default SimulationEnvironment unmodifiable() {
    return UnmodifiableSimulationEnvironmentView.of(this);
  }
}
