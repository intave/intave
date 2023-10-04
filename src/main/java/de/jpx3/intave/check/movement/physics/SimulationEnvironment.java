package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import org.bukkit.Material;
import org.bukkit.util.Vector;

public interface SimulationEnvironment {
  /**
   * pose
   *
   * @return
   */
  Pose pose();

  /**
   * look vector
   *
   * @return
   */
  Vector lookVector();

  default Position position() {
    return new Position(positionX(), positionY(), positionZ());
  }
  double positionX();
  double positionY();
  double positionZ();

  /**
   * verified position
   *
   * @return
   */
  default Position verifiedPosition() {
    return new Position(verifiedPositionX(), verifiedPositionY(), verifiedPositionZ());
  }
  double verifiedPositionX();
  double verifiedPositionY();
  double verifiedPositionZ();

  /**
   * last position
   *
   * @return
   */
  default Position lastPosition() {
    return new Position(lastPositionX(), lastPositionY(), lastPositionZ());
  }
  double lastPositionX();
  double lastPositionY();
  double lastPositionZ();

  void setBoundingBox(BoundingBox boundingBox);
  BoundingBox boundingBox();

  default Motion motion() {
    return new Motion(motionX(), motionY(), motionZ());
  }
  double motionX();
  double motionY();
  double motionZ();

  default Motion baseMotion() {
    return new Motion(baseMotionX(), baseMotionY(), baseMotionZ());
  }
  double baseMotionX();
  double baseMotionY();
  double baseMotionZ();

  default void setBaseMotion(Motion baseMotion) {
    setBaseMotionX(baseMotion.motionX());
    setBaseMotionY(baseMotion.motionY());
    setBaseMotionZ(baseMotion.motionZ());
  }
  void setBaseMotionX(double baseMotionX);
  void setBaseMotionY(double baseMotionY);
  void setBaseMotionZ(double baseMotionZ);

  Motion motionProcessorContext();

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
  double gravity();

  // states
  boolean isSneaking();
  boolean isSprinting();
  boolean inWater();
  boolean inLava();
  boolean inWeb();
  int pastInWeb();
  void resetInWeb();
  boolean onGround();

  boolean lastOnGround();
  boolean collidedHorizontally();
  boolean collidedVertically();
  boolean collidedWithBoat();
  double frictionPosSubtraction();
  boolean receivedFlyingPacketIn(int ticks);

  Material collideMaterial();
  Material frictionMaterial();
  boolean blockOnPositionSoulSpeedAffected();

  double fallDistance();
  void resetFallDistance();

  boolean isInVehicle();
  void dismountRidingEntity(String boatSetback);

  void setPushedByEntity(boolean pushedByEntity);
  boolean pushedByEntity();

  int pastAnyVelocity();
  int pastExternalVelocity();
  int pastNearbyCollisionInaccuracy();

  void increaseFlyingPacketTicks();
  void increaseEntityUseTicks();
  void increasePlayerAttackTicks();
  void increasePushedByWaterFlowTicks();
  void resetPhysicsPacketRelinkFlyVL();

  void increasePowderSnowTicks();
  void resetPowderSnowTicks();

  void increaseEdgeSneakTickGrants();
  void increaseVehicleTicks();
  void resetPushedByWaterFlowTicks();

  void aquaticUpdateLavaReset();

  float height();
  float width();
  double heightRounded();
  double widthRounded();
}
