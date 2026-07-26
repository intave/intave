/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.movement.physics.environment;

import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator.Status;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

@Deprecated
public final class MockSimulationEnvironment implements SimulationEnvironment {
  private double positionX, positionY, positionZ;
  private double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  private double lastPositionX, lastPositionY, lastPositionZ;
  private double motionX, motionY, motionZ;
  private double baseMotionX, baseMotionY, baseMotionZ;
  private double jumpHeight;
  private float height = 1.8F;
  private float width = 0.6F;
  private float yaw, pitch;
  private float lastYaw, lastPitch;
  private float resetMotion = 0.05F;
  private float aiMovementSpeed;
  private float friction = 0.91F;
  private float gravity = 0.08F;
  private float stepHeight = 0.6F;
  private boolean inWater, inLava;
  private boolean sprinting, sneaking;
  private boolean lastSprinting, lastSneaking;
  private boolean collidedHorizontally, collidedVertically;
  private boolean motionXReset, motionZReset;
  private boolean enforceBoatStep;
  private Status boatStatus = Status.ON_LAND;
  private Status previousBoatStatus = Status.ON_LAND;
  private float boatGlide;
  private double boatWaterLevel;
  private int physicsPacketRelinkFlyVL;
	private final float frictionPosSubtraction = 1;
  private double fallDistance;
  private boolean inWeb;
  private boolean onGround;
  private boolean lastOnGround;
  private WorldBorder worldBorder = WorldBorder.createDefault();

  private Fluid interactingFluid;
  private BoundingBox boundingBox = BoundingBox.fromBounds(0, 0, 0, 0, 0, 0);

  private Vector motionMultiplier;

  private final Map<MoveMetric, Integer> activeTracker = new EnumMap<>(MoveMetric.class);
  private final Map<MoveMetric, Integer> pastTracker = new EnumMap<>(MoveMetric.class);

  {
    for (MoveMetric value : MoveMetric.values()) {
      activeTracker.put(value, value.activeDefault());
      pastTracker.put(value, value.pastDefault());
    }
  }

  private final User user;

  public MockSimulationEnvironment(User user) {
    this.user = user;
  }

  public MockSimulationEnvironment() {
    this.user = null;
  }

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
    this.lastYaw = yaw;
  }

  public void setPitch(float pitch) {
    this.pitch = pitch;
    this.lastPitch = pitch;
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

  @Override
  public void setLastSprinting(boolean lastSprinting) {
    this.lastSprinting = lastSprinting;
  }

  @Override
  public boolean isSleeping() {
    return false;
  }

  @Override
  public void setSleeping(boolean sleeping) {
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
  public void setPose(Pose pose) {

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
  public void updateMovement(double newPositionX, double newPositionY, double newPositionZ, float newRotationYaw, float newRotationPitch, boolean hasMovement, boolean hasRotation) {
    if (hasMovement) {
      positionX = newPositionX;
      positionY = newPositionY;
      positionZ = newPositionZ;
    }
    lastYaw = yaw;
    lastPitch = pitch;
    if (hasRotation) {
      yaw = newRotationYaw;
      pitch = newRotationPitch;
    }
  }

  @Override
  public void setPosition(double x, double y, double z) {
    positionX = x;
    positionY = y;
    positionZ = z;
  }

  @Override
  public void setRotation(float newRotationYaw, float newRotationPitch) {
    yaw = newRotationYaw;
    pitch = newRotationPitch;
    lastYaw = newRotationYaw;
    lastPitch = newRotationPitch;
  }

  @Override
  public User user() {
    return user;
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
  public double verifiedLastPositionX() {
    return verifiedPositionX;
  }

  @Override
  public double verifiedLastPositionY() {
    return verifiedPositionY;
  }

  @Override
  public double verifiedLastPositionZ() {
    return verifiedPositionZ;
  }

  @Override
  public void setVerifiedLastPosition(Position position, String reason) {
    verifiedPositionX = position.getX();
    verifiedPositionY = position.getY();
    verifiedPositionZ = position.getZ();
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
  public float lastRotationYaw() {
    return lastYaw;
  }

  @Override
  public float lastRotationPitch() {
    return lastPitch;
  }

  @Override
  public void setLastRotation(float lastRotationYaw, float lastRotationPitch) {
    lastYaw = lastRotationYaw;
    lastPitch = lastRotationPitch;
  }

  @Override
  public void setLastPosition(double x, double y, double z) {
    lastPositionX = x;
    lastPositionY = y;
    lastPositionZ = z;
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
  public double offsetMotionX() {
    return motionX;
  }

  @Override
  public double offsetMotionY() {
    return motionY;
  }

  @Override
  public double offsetMotionZ() {
    return motionZ;
  }

  @Override
  public List<Motion> postTickMotionCandidates() {
    return Collections.emptyList();
  }

  @Override
  public void setPostTickMotionCandidates(@NotNull List<Motion> postTickMotionCandidates) {

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
  public void setBaseMotion(double baseMotionX, double baseMotionY, double baseMotionZ) {
    this.baseMotionX = baseMotionX;
    this.baseMotionY = baseMotionY;
    this.baseMotionZ = baseMotionZ;
  }

  @Override
  public boolean motionXReset() {
    return motionXReset;
  }

  @Override
  public void setMotionResetX(boolean reset) {
    motionXReset = reset;
  }

  @Override
  public boolean motionZReset() {
    return motionZReset;
  }

  @Override
  public void setMotionResetZ(boolean reset) {
    motionZReset = reset;
  }

  @Override
  public Vector motionMultiplier() {
    return motionMultiplier;
  }

  @Override
  public void setMotionMultiplier(Vector motionMultiplier) {
    this.motionMultiplier = motionMultiplier;
    fallDistance = 0.0;
  }

  @Override
  public void resetMotionMultiplier() {
    motionMultiplier = null;
  }

  @Override
  public WorldBorder border() {
    return worldBorder;
  }

  @Override
  public void setWorldBorder(@NotNull WorldBorder worldBorder) {
    this.worldBorder = worldBorder;
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
  public boolean shouldHaveFallFlyingPose() {
    return false;
  }

  @Override
  public float friction(boolean sprinting) {
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
  public void setJumpMotion(double jumpMotion) {
    this.jumpHeight = jumpMotion;
  }

  @Override
  public boolean isJumping() {
    return false;
  }

  @Override
  public double gravity() {
    return gravity;
  }

  @Override
  public float jumpMovementFactor() {
    return 0.02F;
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
  public boolean lastSprinting() {
    return lastSprinting;
  }

  @Override
  public boolean hasSprintSpeed() {
    return sprinting;
  }

  @Override
  public boolean sprintingAllowed() {
    return true;
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
  public BlockPosition mainSupportingBlockPos() {
    return null;
  }

  @Override
  public void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos) {

  }

  @Override
  public boolean onGroundNoBlocks() {
    return false;
  }

  @Override
  public void setOnGroundNoBlocks(boolean onGroundNoBlocks) {

  }

  @Override
  public double frictionPosSubtraction() {
    return frictionPosSubtraction;
  }

  @Override
  public float frictionMultiplier() {
    return 0;
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
  public Material previousCollideMaterial() {
    return Material.AIR;
  }

  @Override
  public Material previousFrictionMaterial() {
    return Material.AIR;
  }

  @Override
  public void setCollideMaterial(Material collideMaterial) {

  }

  @Override
  public void setFrictionMaterial(Material frictionMaterial) {

  }

  @Override
  public void setPreviousCollideMaterial(Material previousCollideMaterial) {

  }

  @Override
  public void setPreviousFrictionMaterial(Material previousFrictionMaterial) {

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
  public void addFallDistance(double fallDistance) {
    this.fallDistance += fallDistance;
  }

  @Override
  public boolean isInVehicle() {
    return false;
  }

  @Override
  public void dismountRidingEntity(String boatSetback) {

  }

  @Override
  public Simulator simulator() {
    return Simulators.PLAYER;
  }

  @Override
  public void setSimulator(Simulator simulator) {
    if (simulator != Simulators.PLAYER) {
      throw new UnsupportedOperationException("TestSimulationEnvironment only supports PLAYER simulator");
    }
  }

  @Override
  public void setPushedByEntity(boolean pushedByEntity) {

  }

  @Override
  public boolean pushedByEntity() {
    return false;
  }

  @Override
  public void setSimulationResult(SimulationResult result) {

  }

  @Override
  public SimulationResult simulationResult() {
    return null;
  }

  @Override
  public void setLastMovementConfiguration(MovementConfiguration configuration) {

  }

  @Override
  public MovementConfiguration lastMovementConfiguration() {
    return MovementConfiguration.blank();
  }

  @Override
  public void activeTick(MoveMetric metric) {
    activeTracker.put(metric, activeTracker.getOrDefault(metric, 0) + 1);
    pastTracker.put(metric, 0);
  }

  @Override
  public void inactiveTick(MoveMetric metric) {
    activeTracker.put(metric, 0);
    pastTracker.put(metric, ticksPast(metric) + 1);
  }

  @Override
  public int reduceTicks() {
    return 0;
  }

  @Override
  public boolean denyJump() {
    return false;
  }

  @Override
  public void setEyesInWater(boolean eyesInWater) {

  }

  @Override
  public boolean areEyesInWater() {
    return false;
  }

  @Override
  public void setInteractingFluid(Fluid interactingFluid) {

  }

  @Override
  public void resetPhysicsPacketRelinkFlyVL() {
    physicsPacketRelinkFlyVL = 0;
  }

  @Override
  public int physicsPacketRelinkFlyVL() {
    return physicsPacketRelinkFlyVL;
  }

  @Override
  public void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
    this.physicsPacketRelinkFlyVL = physicsPacketRelinkFlyVL;
  }

  @Override
  public boolean enforceBoatStep() {
    return enforceBoatStep;
  }

  @Override
  public void setEnforceBoatStep(boolean enforceBoatStep) {
    this.enforceBoatStep = enforceBoatStep;
  }

  @Override
  public Status boatStatus() {
    return boatStatus;
  }

  @Override
  public void setBoatStatus(Status boatStatus) {
    this.boatStatus = boatStatus;
  }

  @Override
  public Status previousBoatStatus() {
    return previousBoatStatus;
  }

  @Override
  public void setPreviousBoatStatus(Status previousBoatStatus) {
    this.previousBoatStatus = previousBoatStatus;
  }

  @Override
  public float boatGlide() {
    return boatGlide;
  }

  @Override
  public void setBoatGlide(float boatGlide) {
    this.boatGlide = boatGlide;
  }

  @Override
  public double boatWaterLevel() {
    return boatWaterLevel;
  }

  @Override
  public void setBoatWaterLevel(double boatWaterLevel) {
    this.boatWaterLevel = boatWaterLevel;
  }

  @Override
  public int ticks(MoveMetric metric) {
    return activeTracker.getOrDefault(metric, 0);
  }

  @Override
  public int ticksPast(MoveMetric metric) {
    return pastTracker.getOrDefault(metric, metric.pastDefault());
  }

  @Override
  public void updateEyesInWater() {

  }

  @Override
  public void aquaticUpdateLavaReset() {

  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public void setHeight(float height) {

  }

  @Override
  public float width() {
    return width;
  }

  @Override
  public void setWidth(float width) {

  }

  @Override
  public double heightRounded() {
    return height;
  }

  @Override
  public double widthRounded() {
    return width;
  }

  @Override
  public float eyeHeight() {
    return height - 0.08F;
  }

  @Override
  public Fluid interactingFluid() {
    return interactingFluid;
  }

  @Override
  public void assumeOccurred(Simulation simulation) {

  }

  @Override
  public void tickComplete(boolean hasMovement, boolean hasRotation, boolean isRealClientTick) {

  }

  @Override
  public long currentTick() {
    return 0;
  }

  @Override
  public long activeSequence() {
    return 0;
  }

  @Override
  public void setActiveSequence(long activeSequence) {

  }

  @Override
  public List<TickAmbiguousUpdate> allTickAmbiguousUpdates() {
    return new ArrayList<>();
  }

  @Override
  public void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket) {

  }

  @Override
  public SimulationEnvironment immutableView() {
    return this;
  }
}
