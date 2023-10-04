package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

public final class TestSimulationEnvironment implements SimulationEnvironment {
  private double positionX, positionY, positionZ;
  private double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  private double lastPositionX, lastPositionY, lastPositionZ;
  private double motionX, motionY, motionZ;
  private double baseMotionX, baseMotionY, baseMotionZ;
  private double jumpHeight;
  private float height = 1.8F;
  private float width = 0.6F;
  private float yaw, pitch;
  private float resetMotion = 0.05F;
  private float aiMovementSpeed;
  private float friction = 0.91F;
  private float gravity = 0.08F;
  private float stepHeight = 0.6F;
  private boolean inWater, inLava;
  private boolean sprinting, sneaking;
  private boolean collidedHorizontally, collidedVertically;
  private int pastInWeb = 100;
  private final float frictionPosSubtraction = 1;
  private double fallDistance;
  private boolean inWeb;
  private boolean onGround;
  private boolean lastOnGround;

  private final Motion motionProcessorContext = new Motion();
  private BoundingBox boundingBox = BoundingBox.fromBounds(0, 0, 0, 0, 0, 0);

  private Vector motionMultiplier;

  public void copyPositionToLastPosition() {
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;
  }

  public void copyPositionToVerifiedPosition() {
    verifiedPositionX = positionX;
    verifiedPositionY = positionY;
    verifiedPositionZ = positionZ;
  }

  public void setPositionX(double positionX) {
    this.positionX = positionX;
  }

  public void setPositionY(double positionY) {
    this.positionY = positionY;
  }

  public void setPositionZ(double positionZ) {
    this.positionZ = positionZ;
  }

  public void setVerifiedPositionX(double verifiedPositionX) {
    this.verifiedPositionX = verifiedPositionX;
  }

  public void setVerifiedPositionY(double verifiedPositionY) {
    this.verifiedPositionY = verifiedPositionY;
  }

  public void setVerifiedPositionZ(double verifiedPositionZ) {
    this.verifiedPositionZ = verifiedPositionZ;
  }

  public void setLastPositionX(double lastPositionX) {
    this.lastPositionX = lastPositionX;
  }

  public void setLastPositionY(double lastPositionY) {
    this.lastPositionY = lastPositionY;
  }

  public void setLastPositionZ(double lastPositionZ) {
    this.lastPositionZ = lastPositionZ;
  }

  public void setMotionX(double motionX) {
    this.motionX = motionX;
  }

  public void setMotionY(double motionY) {
    this.motionY = motionY;
  }

  public void setMotionZ(double motionZ) {
    this.motionZ = motionZ;
  }

  public void setJumpHeight(double jumpHeight) {
    this.jumpHeight = jumpHeight;
  }

  public void setYaw(float yaw) {
    this.yaw = yaw;
  }

  public void setPitch(float pitch) {
    this.pitch = pitch;
  }

  public void setResetMotion(float resetMotion) {
    this.resetMotion = resetMotion;
  }

  public void setAiMovementSpeed(float aiMovementSpeed) {
    this.aiMovementSpeed = aiMovementSpeed;
  }

  public void setFriction(float friction) {
    this.friction = friction;
  }

  public void setStepHeight(float stepHeight) {
    this.stepHeight = stepHeight;
  }

  public void setGravity(float gravity) {
    this.gravity = gravity;
  }

  public void setInWater(boolean inWater) {
    this.inWater = inWater;
  }

  public void setInLava(boolean inLava) {
    this.inLava = inLava;
  }

  public void setSneaking(boolean sneaking) {
    this.sneaking = sneaking;
  }

  public void setInWeb(boolean inWeb) {
    this.inWeb = inWeb;
  }

  public void setOnGround(boolean onGround) {
    this.onGround = onGround;
  }

  public void setLastOnGround(boolean lastOnGround) {
    this.lastOnGround = lastOnGround;
  }

  @Override
  public Pose pose() {
    return Pose.STANDING;
  }

  @Override
  public Vector lookVector() {
    float f = pitch * ((float) Math.PI / 180F);
    float f1 = -yaw * ((float) Math.PI / 180F);
    float f2 = cos(f1);
    float f3 = sin(f1);
    float f4 = cos(f);
    float f5 = sin(f);
    return new Vector(f3 * f4, -f5, (double) (f2 * f4));
  }

  @Override
  public double positionX() {
    return positionX;
  }

  @Override
  public double positionY() {
    return positionY;
  }

  @Override
  public double positionZ() {
    return positionZ;
  }

  @Override
  public double verifiedPositionX() {
    return verifiedPositionX;
  }

  @Override
  public double verifiedPositionY() {
    return verifiedPositionY;
  }

  @Override
  public double verifiedPositionZ() {
    return verifiedPositionZ;
  }

  @Override
  public double lastPositionX() {
    return lastPositionX;
  }

  @Override
  public double lastPositionY() {
    return lastPositionY;
  }

  @Override
  public double lastPositionZ() {
    return lastPositionZ;
  }

  @Override
  public void setBoundingBox(BoundingBox boundingBox) {
    this.boundingBox = boundingBox;
  }

  @Override
  public BoundingBox boundingBox() {
    return boundingBox;
  }

  @Override
  public double motionX() {
    return motionX;
  }

  @Override
  public double motionY() {
    return motionY;
  }

  @Override
  public double motionZ() {
    return motionZ;
  }

  @Override
  public double baseMotionX() {
    return baseMotionX;
  }

  @Override
  public double baseMotionY() {
    return baseMotionY;
  }

  @Override
  public double baseMotionZ() {
    return baseMotionZ;
  }

  @Override
  public void setBaseMotionX(double baseMotionX) {
    this.baseMotionX = baseMotionX;
  }

  @Override
  public void setBaseMotionY(double baseMotionY) {
    this.baseMotionY = baseMotionY;
  }

  @Override
  public void setBaseMotionZ(double baseMotionZ) {
    this.baseMotionZ = baseMotionZ;
  }

  @Override
  public Motion motionProcessorContext() {
    return motionProcessorContext;
  }

  @Override
  public boolean motionXReset() {
    return false;
  }

  @Override
  public boolean motionZReset() {
    return false;
  }

  @Override
  public Vector motionMultiplier() {
    return motionMultiplier;
  }

  @Override
  public void resetMotionMultiplier() {
    motionMultiplier = null;
  }

  @Override
  public float rotationYaw() {
    return yaw;
  }

  @Override
  public float yawSine() {
    return sin(yaw * ((float) Math.PI / 180F));
  }

  @Override
  public float yawCosine() {
    return cos(yaw * ((float) Math.PI / 180F));
  }

  @Override
  public float rotationPitch() {
    return pitch;
  }

  @Override
  public float aiMoveSpeed(boolean sprinting) {
    return aiMovementSpeed;
  }

  @Override
  public float friction() {
    return friction;
  }

  @Override
  public double stepHeight() {
    return stepHeight;
  }

  @Override
  public double resetMotion() {
    return resetMotion;
  }

  @Override
  public double jumpMotion() {
    return jumpHeight;
  }

  @Override
  public double gravity() {
    return gravity;
  }

  @Override
  public boolean isSneaking() {
    return sneaking;
  }

  @Override
  public boolean isSprinting() {
    return sprinting;
  }

  @Override
  public boolean inWater() {
    return inWater;
  }

  @Override
  public boolean inLava() {
    return inLava;
  }

  @Override
  public boolean inWeb() {
    return inWeb;
  }

  @Override
  public int pastInWeb() {
    return pastInWeb;
  }

  @Override
  public void resetInWeb() {
    inWeb = false;
  }

  @Override
  public boolean onGround() {
    return onGround;
  }

  @Override
  public boolean lastOnGround() {
    return lastOnGround;
  }

  @Override
  public boolean collidedHorizontally() {
    return collidedHorizontally;
  }

  @Override
  public boolean collidedVertically() {
    return collidedVertically;
  }

  @Override
  public boolean collidedWithBoat() {
    return false;
  }

  @Override
  public double frictionPosSubtraction() {
    return frictionPosSubtraction;
  }

  @Override
  public boolean receivedFlyingPacketIn(int ticks) {
    return false;
  }

  @Override
  public Material collideMaterial() {
    return Material.AIR;
  }

  @Override
  public Material frictionMaterial() {
    return Material.AIR;
  }

  @Override
  public boolean blockOnPositionSoulSpeedAffected() {
    return false;
  }

  @Override
  public double fallDistance() {
    return fallDistance;
  }

  @Override
  public void resetFallDistance() {
    fallDistance = 0;
  }

  @Override
  public boolean isInVehicle() {
    return false;
  }

  @Override
  public void dismountRidingEntity(String boatSetback) {

  }

  @Override
  public void setPushedByEntity(boolean pushedByEntity) {

  }

  @Override
  public boolean pushedByEntity() {
    return false;
  }

  @Override
  public int pastAnyVelocity() {
    return 100;
  }

  @Override
  public int pastExternalVelocity() {
    return 100;
  }

  @Override
  public int pastNearbyCollisionInaccuracy() {
    return 100;
  }

  @Override
  public void increaseFlyingPacketTicks() {

  }

  @Override
  public void increaseEntityUseTicks() {

  }

  @Override
  public void increasePlayerAttackTicks() {

  }

  @Override
  public void increasePushedByWaterFlowTicks() {

  }

  @Override
  public void resetPushedByWaterFlowTicks() {

  }

  @Override
  public void resetPhysicsPacketRelinkFlyVL() {

  }

  @Override
  public void increasePowderSnowTicks() {

  }

  @Override
  public void resetPowderSnowTicks() {

  }

  @Override
  public void increaseEdgeSneakTickGrants() {

  }

  @Override
  public void increaseVehicleTicks() {

  }

  @Override
  public void aquaticUpdateLavaReset() {

  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public float width() {
    return width;
  }

  @Override
  public double heightRounded() {
    return height;
  }

  @Override
  public double widthRounded() {
    return width;
  }
}
