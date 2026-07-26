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
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.module.tracker.entity.Entity;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public final class ImmutableSimulationEnvironmentCopy implements SimulationEnvironment {
	private final User user;
	private final Pose pose;
	private final Vector lookVector;
	private final double positionX, positionY, positionZ;
	private final double verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ;
	private final double lastPositionX, lastPositionY, lastPositionZ;
	private final float lastRotationYaw, lastRotationPitch;
	private final BoundingBox boundingBox;
	private final double motionX, motionY, motionZ;
	private final double baseMotionX, baseMotionY, baseMotionZ;
	private final boolean motionXReset, motionZReset;
	private final List<Motion> postTickMotionCandidates;
	private final Vector motionMultiplier;
	private final float rotationYaw, yawSine, yawCosine, rotationPitch;
	private final float aiMoveSpeed, sprintAiMoveSpeed;
	private final float friction, sprintFriction;
	private final double stepHeight, resetMotion, jumpMotion, gravity;
	private final float jumpMovementFactor;
	private final boolean hasJumpedInTick;
	private final boolean sneaking, sprinting, hasSprintSpeed, sprintingAllowed;
	private final boolean lastSprinting;
	private final boolean inWater, inLava, inWeb;
	private final boolean onGround, lastOnGround, collidedHorizontally, collidedVertically;
	private final boolean collidedWithBoat;
	private final double frictionPosSubtraction;
	private final float frictionMultiplier;
	private final boolean receivesFlyingPackets;
	private final boolean useClientFlyingPacketTicks;
	private final Material collideMaterial, frictionMaterial, previousCollideMaterial, previousFrictionMaterial;
	private final boolean blockOnPositionSoulSpeedAffected;
	private final double fallDistance;
	private final boolean inVehicle;
	private final Entity vehicle;
	private final Simulator simulator;
	private final boolean pushedByEntity;
	private final SimulationResult beforeMoveCollider;
	private final MovementConfiguration lastMovementConfiguration;
	private final int reduceTicks;
	private final long currentTick;
	private final long currentSequence;
	private final List<TickAmbiguousUpdate> possibleMovementUpdates;
	private final boolean denyJump;
	private final float height, width, eyeHeight;
	private final double heightRounded, widthRounded;
	private final Fluid interactingFluid;
	private final WorldBorder worldBorder;
	private final int fireworkRocketsPower;
	private final int shulkerXToleranceRemaining, shulkerYToleranceRemaining, shulkerZToleranceRemaining;
	private final int lowestShulkerY, highestShulkerY;
	private final int pistonMotionToleranceRemaining;
	private final double pistonVerticalAllowance, pistonHorizontalAllowance;
	private final BoundingBox pistonCollisionArea;
	private final boolean physicsUnpredictableVelocityExpected;
	private final boolean enforceBoatStep;
	private final Status boatStatus, previousBoatStatus;
	private final float boatGlide;
	private final double boatWaterLevel;
	private final int physicsPacketRelinkFlyVL;
	private final boolean lastSneaking, currentlyInBlock;
	private final int highestLocalRiptideLevel;
	private final boolean onGroundWithRiptide;
	private final double baseMoveSpeed;
	private final boolean areEyesInWater;
	private final boolean clientElytraFlying;
	private final boolean sleeping;
	private final BlockPosition mainSupportingBlockPos;
	private final boolean onGroundNoBlocks;
	private final EnumMap<MoveMetric, Integer> activeTracker;
	private final EnumMap<MoveMetric, Integer> pastTracker;

	private ImmutableSimulationEnvironmentCopy(SimulationEnvironment source) {
		this.user = source.user();
		this.pose = source.pose();
		this.lookVector = copyVector(source.lookVector());
		this.positionX = source.positionX();
		this.positionY = source.positionY();
		this.positionZ = source.positionZ();
		this.verifiedLastPositionX = source.verifiedLastPositionX();
		this.verifiedLastPositionY = source.verifiedLastPositionY();
		this.verifiedLastPositionZ = source.verifiedLastPositionZ();
		this.lastPositionX = source.lastPositionX();
		this.lastPositionY = source.lastPositionY();
		this.lastPositionZ = source.lastPositionZ();
		this.lastRotationYaw = source.lastRotationYaw();
		this.lastRotationPitch = source.lastRotationPitch();
		this.boundingBox = copyBoundingBox(source.boundingBox());
		this.motionX = source.offsetMotionX();
		this.motionY = source.offsetMotionY();
		this.motionZ = source.offsetMotionZ();
		this.baseMotionX = source.baseMotionX();
		this.baseMotionY = source.baseMotionY();
		this.baseMotionZ = source.baseMotionZ();
		this.motionXReset = source.motionXReset();
		this.motionZReset = source.motionZReset();
		this.motionMultiplier = copyVector(source.motionMultiplier());
		this.rotationYaw = source.rotationYaw();
		this.yawSine = source.yawSine();
		this.yawCosine = source.yawCosine();
		this.rotationPitch = source.rotationPitch();
		this.aiMoveSpeed = source.aiMoveSpeed(false);
		this.sprintAiMoveSpeed = source.aiMoveSpeed(true);
		this.friction = source.friction(false);
		this.sprintFriction = source.friction(true);
		this.stepHeight = source.stepHeight();
		this.resetMotion = source.resetMotion();
		this.jumpMotion = source.jumpMotion();
		this.hasJumpedInTick = source.isJumping();
		this.gravity = source.gravity();
		this.jumpMovementFactor = source.jumpMovementFactor();
		this.sneaking = source.isSneaking();
		this.sprinting = source.isSprinting();
		this.lastSprinting = source.lastSprinting();
		this.areEyesInWater = source.areEyesInWater();
		this.hasSprintSpeed = source.hasSprintSpeed();
		this.sprintingAllowed = source.sprintingAllowed();
		this.inWater = source.inWater();
		this.inLava = source.inLava();
		this.inWeb = source.inWeb();
		this.onGround = source.onGround();
		this.lastOnGround = source.lastOnGround();
		this.collidedHorizontally = source.collidedHorizontally();
		this.collidedVertically = source.collidedVertically();
		this.collidedWithBoat = source.collidedWithBoat();
		this.frictionPosSubtraction = source.frictionPosSubtraction();
		this.frictionMultiplier = source.frictionMultiplier();
		this.receivesFlyingPackets = source.receivedFlyingPacketIn(Integer.MAX_VALUE);
		this.useClientFlyingPacketTicks = useClientFlyingPacketTicks(source, receivesFlyingPackets);
		this.collideMaterial = source.collideMaterial();
		this.frictionMaterial = source.frictionMaterial();
		this.previousCollideMaterial = source.previousCollideMaterial();
		this.previousFrictionMaterial = source.previousFrictionMaterial();
		this.blockOnPositionSoulSpeedAffected = source.blockOnPositionSoulSpeedAffected();
		this.fallDistance = source.fallDistance();
		this.inVehicle = source.isInVehicle();
		this.vehicle = source.vehicle();
		this.simulator = source.simulator();
		this.pushedByEntity = source.pushedByEntity();
		this.beforeMoveCollider = copySimulationResult(source.simulationResult());
		this.lastMovementConfiguration = source.lastMovementConfiguration();
		this.reduceTicks = source.reduceTicks();
		this.denyJump = source.denyJump();
		this.height = source.height();
		this.width = source.width();
		this.heightRounded = source.heightRounded();
		this.widthRounded = source.widthRounded();
		this.eyeHeight = source.eyeHeight();
		this.interactingFluid = source.interactingFluid();
		this.currentTick = source.currentTick();
		this.currentSequence = source.activeSequence();
		this.possibleMovementUpdates = new ArrayList<>(source.allTickAmbiguousUpdates());
		this.worldBorder = source.border();
		this.fireworkRocketsPower = source.fireworkRocketsPower();
		this.shulkerXToleranceRemaining = source.shulkerXToleranceRemaining();
		this.shulkerYToleranceRemaining = source.shulkerYToleranceRemaining();
		this.shulkerZToleranceRemaining = source.shulkerZToleranceRemaining();
		this.lowestShulkerY = source.lowestShulkerY();
		this.highestShulkerY = source.highestShulkerY();
		this.pistonMotionToleranceRemaining = source.pistonMotionToleranceRemaining();
		this.pistonVerticalAllowance = source.pistonVerticalAllowance();
		this.pistonHorizontalAllowance = source.pistonHorizontalAllowance();
		this.pistonCollisionArea = copyBoundingBox(source.pistonCollisionArea());
		this.physicsUnpredictableVelocityExpected = source.physicsUnpredictableVelocityExpected();
		this.enforceBoatStep = source.enforceBoatStep();
		this.boatStatus = source.boatStatus();
		this.previousBoatStatus = source.previousBoatStatus();
		this.boatGlide = source.boatGlide();
		this.boatWaterLevel = source.boatWaterLevel();
		this.physicsPacketRelinkFlyVL = source.physicsPacketRelinkFlyVL();
		this.lastSneaking = source.lastSneaking();
		this.currentlyInBlock = source.currentlyInBlock();
		this.highestLocalRiptideLevel = source.highestLocalRiptideLevel();
		this.onGroundWithRiptide = source.onGroundWithRiptide();
		this.baseMoveSpeed = source.baseMoveSpeed();
		this.clientElytraFlying = source.shouldHaveFallFlyingPose();
		this.sleeping = source.isSleeping();
		this.postTickMotionCandidates = new ArrayList<>(source.postTickMotionCandidates());
		this.mainSupportingBlockPos = source.mainSupportingBlockPos();
		this.onGroundNoBlocks = source.onGroundNoBlocks();
		this.activeTracker = new EnumMap<>(MoveMetric.class);
		this.pastTracker = new EnumMap<>(MoveMetric.class);
		for (MoveMetric metric : MoveMetric.values()) {
			activeTracker.put(metric, source.ticks(metric));
			pastTracker.put(metric, source.ticksPast(metric));
		}
	}

	public static ImmutableSimulationEnvironmentCopy of(SimulationEnvironment source) {
		if (source instanceof ImmutableSimulationEnvironmentCopy) {
			return (ImmutableSimulationEnvironmentCopy) source;
		}
		return new ImmutableSimulationEnvironmentCopy(source);
	}

	@Override
	public Pose pose() {
		return pose;
	}

	@Override
	public void setPose(Pose pose) {
		throw immutableCopyException();
	}

	@Override
	public Vector lookVector() {
		return copyVector(lookVector);
	}

	@Override
	public void updateMovement(
		double newPositionX, double newPositionY, double newPositionZ,
		float newRotationYaw, float newRotationPitch,
		boolean hasMovement, boolean hasRotation
	) {
		throw immutableCopyException();
	}

	@Override
	public void setPosition(double x, double y, double z) {
		throw immutableCopyException();
	}

	@Override
	public void setRotation(float newRotationYaw, float newRotationPitch) {
		throw immutableCopyException();
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
		return verifiedLastPositionX;
	}

	@Override
	public double verifiedLastPositionY() {
		return verifiedLastPositionY;
	}

	@Override
	public double verifiedLastPositionZ() {
		return verifiedLastPositionZ;
	}

	@Override
	public void setVerifiedLastPosition(Position position, String reason) {
		throw immutableCopyException();
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
		return lastRotationYaw;
	}

	@Override
	public float lastRotationPitch() {
		return lastRotationPitch;
	}

	@Override
	public void setLastRotation(float lastRotationYaw, float lastRotationPitch) {
		throw immutableCopyException();
	}

	@Override
	public void setLastPosition(double x, double y, double z) {
		throw immutableCopyException();
	}

	@Override
	public void setBoundingBox(BoundingBox boundingBox) {
		throw immutableCopyException();
	}

	@Override
	public BoundingBox boundingBox() {
		return copyBoundingBox(boundingBox);
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
		return postTickMotionCandidates.isEmpty() ? Collections.emptyList() : new ArrayList<>(postTickMotionCandidates);
	}

	@Override
	public void setPostTickMotionCandidates(@NotNull List<Motion> postTickMotionCandidates) {
		throw immutableCopyException();
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
		throw immutableCopyException();
	}

	@Override
	public boolean motionXReset() {
		return motionXReset;
	}

	@Override
	public void setMotionResetX(boolean reset) {
		throw immutableCopyException();
	}

	@Override
	public boolean motionZReset() {
		return motionZReset;
	}

	@Override
	public void setMotionResetZ(boolean reset) {
		throw immutableCopyException();
	}

	@Override
	public Vector motionMultiplier() {
		return copyVector(motionMultiplier);
	}

	@Override
	public void setMotionMultiplier(Vector motionMultiplier) {
		throw immutableCopyException();
	}

	@Override
	public void resetMotionMultiplier() {
		throw immutableCopyException();
	}

	@Override
	public WorldBorder border() {
		return worldBorder;
	}

	@Override
	public void setWorldBorder(@NotNull WorldBorder worldBorder) {
		throw immutableCopyException();
	}

	@Override
	public float rotationYaw() {
		return rotationYaw;
	}

	@Override
	public float yawSine() {
		return yawSine;
	}

	@Override
	public float yawCosine() {
		return yawCosine;
	}

	@Override
	public float rotationPitch() {
		return rotationPitch;
	}

	@Override
	public float aiMoveSpeed(boolean sprinting) {
		return sprinting ? sprintAiMoveSpeed : aiMoveSpeed;
	}

	@Override
	public float friction(boolean sprinting) {
		return sprinting ? sprintFriction : friction;
	}

	@Override
	public double stepHeight() {
		return stepHeight;
	}

	@Override
	public void setStepHeight(float stepHeight) {
		throw immutableCopyException();
	}

	@Override
	public double resetMotion() {
		return resetMotion;
	}

	@Override
	public double jumpMotion() {
		return jumpMotion;
	}

	@Override
	public void setJumpMotion(double jumpMotion) {
		throw immutableCopyException();
	}

	@Override
	public boolean isJumping() {
		return hasJumpedInTick;
	}

	@Override
	public double gravity() {
		return gravity;
	}

	@Override
	public float jumpMovementFactor() {
		return jumpMovementFactor;
	}

	@Override
	public boolean isSneaking() {
		return sneaking;
	}

	@Override
	public void setSneaking(boolean sneaking) {
		throw immutableCopyException();
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
	public void setLastSprinting(boolean lastSprinting) {
		throw immutableCopyException();
	}

	@Override
	public boolean isSleeping() {
		return sleeping;
	}

	@Override
	public void setSleeping(boolean sleeping) {
		throw immutableCopyException();
	}

	@Override
	public boolean hasSprintSpeed() {
		return hasSprintSpeed;
	}

	@Override
	public boolean sprintingAllowed() {
		return sprintingAllowed;
	}

	@Override
	public boolean inWater() {
		return inWater;
	}

	@Override
	public void setInWater(boolean inWater) {
		throw immutableCopyException();
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
		throw immutableCopyException();
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
	public void setLastOnGround(boolean lastOnGround) {
		throw immutableCopyException();
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
	public BlockPosition mainSupportingBlockPos() {
		return mainSupportingBlockPos;
	}

	@Override
	public void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos) {
		throw immutableCopyException();
	}

	@Override
	public boolean onGroundNoBlocks() {
		return onGroundNoBlocks;
	}

	@Override
	public void setOnGroundNoBlocks(boolean onGroundNoBlocks) {
		throw immutableCopyException();
	}

	@Override
	public void compileSpecialBlocks() {
		throw immutableCopyException();
	}

	@Override
	public boolean collidedWithBoat() {
		return collidedWithBoat;
	}

	@Override
	public double frictionPosSubtraction() {
		return frictionPosSubtraction;
	}

	@Override
	public float frictionMultiplier() {
		return frictionMultiplier;
	}

	@Override
	public boolean receivedFlyingPacketIn(int ticks) {
		if (!receivesFlyingPackets) {
			return false;
		}
		MoveMetric metric = useClientFlyingPacketTicks
			? MoveMetric.FLYING_PACKET_CLIENT
			: MoveMetric.FLYING_PACKET_ACCURATE;
		return ticksPast(metric) <= ticks;
	}

	@Override
	public Material collideMaterial() {
		return collideMaterial;
	}

	@Override
	public Material frictionMaterial() {
		return frictionMaterial;
	}

	@Override
	public Material previousCollideMaterial() {
		return previousCollideMaterial;
	}

	@Override
	public Material previousFrictionMaterial() {
		return previousFrictionMaterial;
	}

	@Override
	public void setCollideMaterial(Material collideMaterial) {
		throw immutableCopyException();
	}

	@Override
	public void setFrictionMaterial(Material frictionMaterial) {
		throw immutableCopyException();
	}

	@Override
	public void setPreviousCollideMaterial(Material previousCollideMaterial) {
		throw immutableCopyException();
	}

	@Override
	public void setPreviousFrictionMaterial(Material previousFrictionMaterial) {
		throw immutableCopyException();
	}

	@Override
	public boolean blockOnPositionSoulSpeedAffected() {
		return blockOnPositionSoulSpeedAffected;
	}

	@Override
	public double fallDistance() {
		return fallDistance;
	}

	@Override
	public void resetFallDistance() {
		throw immutableCopyException();
	}

	@Override
	public void addFallDistance(double fallDistance) {
		throw immutableCopyException();
	}

	@Override
	public boolean isInVehicle() {
		return inVehicle;
	}

	@Override
	public Entity vehicle() {
		return vehicle;
	}

	@Override
	public Simulator simulator() {
		return simulator;
	}

	@Override
	public void setSimulator(Simulator simulator) {
		throw immutableCopyException();
	}

	@Override
	public void dismountRidingEntity(String boatSetback) {
		throw immutableCopyException();
	}

	@Override
	public void setPushedByEntity(boolean pushedByEntity) {
		throw immutableCopyException();
	}

	@Override
	public boolean pushedByEntity() {
		return pushedByEntity;
	}

	@Override
	public void setSimulationResult(SimulationResult result) {
		throw immutableCopyException();
	}

	@Override
	public SimulationResult simulationResult() {
		return copySimulationResult(beforeMoveCollider);
	}

	@Override
	public MovementConfiguration lastMovementConfiguration() {
		return lastMovementConfiguration;
	}

	@Override
	public void setLastMovementConfiguration(MovementConfiguration configuration) {
		throw immutableCopyException();
	}

	@Override
	public int ticks(MoveMetric metric) {
		return activeTracker.getOrDefault(metric, metric.activeDefault());
	}

	@Override
	public int ticksPast(MoveMetric metric) {
		return pastTracker.getOrDefault(metric, metric.pastDefault());
	}

	@Override
	public void activeTick(MoveMetric metric) {
		throw immutableCopyException();
	}

	@Override
	public void inactiveTick(MoveMetric metric) {
		throw immutableCopyException();
	}

	@Deprecated
	@Override
	public int reduceTicks() {
		return reduceTicks;
	}

	@Deprecated
	@Override
	public boolean denyJump() {
		return denyJump;
	}

	@Override
	public void setEyesInWater(boolean eyesInWater) {
		throw immutableCopyException();
	}

	@Override
	public boolean areEyesInWater() {
		return areEyesInWater;
	}

	@Override
	public void setInteractingFluid(Fluid interactingFluid) {
		throw immutableCopyException();
	}

	@Override
	public boolean shouldHaveFallFlyingPose() {
		return clientElytraFlying;
	}

	@Override
	public void resetPhysicsPacketRelinkFlyVL() {
		throw immutableCopyException();
	}

	@Override
	public int physicsPacketRelinkFlyVL() {
		return physicsPacketRelinkFlyVL;
	}

	@Override
	public void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
		throw immutableCopyException();
	}

	@Override
	public double baseMoveSpeed() {
		return baseMoveSpeed;
	}

	@Override
	public int fireworkRocketsPower() {
		return fireworkRocketsPower;
	}

	@Override
	public int shulkerXToleranceRemaining() {
		return shulkerXToleranceRemaining;
	}

	@Override
	public int shulkerYToleranceRemaining() {
		return shulkerYToleranceRemaining;
	}

	@Override
	public int shulkerZToleranceRemaining() {
		return shulkerZToleranceRemaining;
	}

	@Override
	public int lowestShulkerY() {
		return lowestShulkerY;
	}

	@Override
	public int highestShulkerY() {
		return highestShulkerY;
	}

	@Override
	public int pistonMotionToleranceRemaining() {
		return pistonMotionToleranceRemaining;
	}

	@Override
	public double pistonVerticalAllowance() {
		return pistonVerticalAllowance;
	}

	@Override
	public double pistonHorizontalAllowance() {
		return pistonHorizontalAllowance;
	}

	@Override
	public BoundingBox pistonCollisionArea() {
		return copyBoundingBox(pistonCollisionArea);
	}

	@Override
	public boolean physicsUnpredictableVelocityExpected() {
		return physicsUnpredictableVelocityExpected;
	}

	@Override
	public boolean enforceBoatStep() {
		return enforceBoatStep;
	}

	@Override
	public void setEnforceBoatStep(boolean enforceBoatStep) {
		throw immutableCopyException();
	}

	@Override
	public Status boatStatus() {
		return boatStatus;
	}

	@Override
	public void setBoatStatus(Status boatStatus) {
		throw immutableCopyException();
	}

	@Override
	public Status previousBoatStatus() {
		return previousBoatStatus;
	}

	@Override
	public void setPreviousBoatStatus(Status previousBoatStatus) {
		throw immutableCopyException();
	}

	@Override
	public float boatGlide() {
		return boatGlide;
	}

	@Override
	public void setBoatGlide(float boatGlide) {
		throw immutableCopyException();
	}

	@Override
	public double boatWaterLevel() {
		return boatWaterLevel;
	}

	@Override
	public void setBoatWaterLevel(double boatWaterLevel) {
		throw immutableCopyException();
	}

	@Override
	public boolean lastSneaking() {
		return lastSneaking;
	}

	@Override
	public boolean currentlyInBlock() {
		return currentlyInBlock;
	}

	@Override
	public int highestLocalRiptideLevel() {
		return highestLocalRiptideLevel;
	}

	@Override
	public boolean onGroundWithRiptide() {
		return onGroundWithRiptide;
	}

	@Override
	public void updateEyesInWater() {
		throw immutableCopyException();
	}

	@Override
	public void aquaticUpdateLavaReset() {
		throw immutableCopyException();
	}

	@Override
	public float height() {
		return height;
	}

	@Override
	public void setHeight(float height) {
		throw immutableCopyException();
	}

	@Override
	public float width() {
		return width;
	}

	@Override
	public void setWidth(float width) {
		throw immutableCopyException();
	}

	@Override
	public double heightRounded() {
		return heightRounded;
	}

	@Override
	public double widthRounded() {
		return widthRounded;
	}

	@Override
	public float eyeHeight() {
		return eyeHeight;
	}

	@Override
	public Fluid interactingFluid() {
		return interactingFluid;
	}

	@Override
	public void assumeOccurred(Simulation simulation) {
		throw immutableCopyException();
	}

	@Override
	public void tickComplete(boolean hasMovement, boolean hasRotation, boolean isRealClientTick) {
		throw immutableCopyException();
	}

	@Override
	public long currentTick() {
		return currentTick;
	}

	@Override
	public long activeSequence() {
		return currentSequence;
	}

	@Override
	public void setActiveSequence(long activeSequence) {
		throw immutableCopyException();
	}

	@Override
	public List<TickAmbiguousUpdate> allTickAmbiguousUpdates() {
		return new ArrayList<>(possibleMovementUpdates);
	}

	@Override
	public void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket) {
		throw immutableCopyException();
	}

	@Override
	public SimulationEnvironment immutableView() {
		return this;
	}

	@Override
	public SimulationEnvironment immutableCopy() {
		return this;
	}

	@Override
	public void commitTo(SimulationEnvironment other) {
		if (other == this) {
			return;
		}
		other.setVerifiedLastPosition(
			new Position(verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ),
			"immutable-copy"
		);
		other.updateMovement(
			positionX, positionY, positionZ,
			rotationYaw, rotationPitch,
			true, true
		);
		other.setLastPosition(lastPositionX, lastPositionY, lastPositionZ);
		other.setLastRotation(lastRotationYaw, lastRotationPitch);
		other.setBoundingBox(copyBoundingBox(boundingBox));
		other.setWorldBorder(worldBorder);
		other.setBaseMotion(baseMotionX, baseMotionY, baseMotionZ);
		if (motionMultiplier == null) {
			other.resetMotionMultiplier();
		} else {
			other.setMotionMultiplier(copyVector(motionMultiplier));
		}
		other.setJumpMotion(jumpMotion);
		other.setInWater(inWater);
		if (!inLava) {
			other.aquaticUpdateLavaReset();
		}
		if (!inWeb) {
			other.resetInWeb();
		}
		other.setLastOnGround(lastOnGround);
		applyFallDistanceTo(other);
		other.setPushedByEntity(pushedByEntity);
		other.setSimulationResult(copySimulationResult(beforeMoveCollider));
		other.setActiveSequence(currentSequence);
		other.setMotionResetX(motionXReset);
		other.setMotionResetZ(motionZReset);
		other.setEnforceBoatStep(enforceBoatStep);
		other.setBoatStatus(boatStatus);
		other.setPreviousBoatStatus(previousBoatStatus);
		other.setBoatGlide(boatGlide);
		other.setBoatWaterLevel(boatWaterLevel);
		other.setPhysicsPacketRelinkFlyVL(physicsPacketRelinkFlyVL);
		applyMetricsTo(other);
	}

	private void applyFallDistanceTo(SimulationEnvironment other) {
		double currentFallDistance = other.fallDistance();
		if (fallDistance == 0.0) {
			other.resetFallDistance();
		} else if (fallDistance != currentFallDistance) {
			other.addFallDistance(fallDistance - currentFallDistance);
		}
	}

	private void applyMetricsTo(SimulationEnvironment other) {
		for (MoveMetric metric : MoveMetric.values()) {
			applyMetricTo(other, metric);
		}
	}

	private void applyMetricTo(SimulationEnvironment other, MoveMetric metric) {
		int activeTicks = ticks(metric);
		int pastTicks = ticksPast(metric);
		if (other.ticks(metric) == activeTicks && other.ticksPast(metric) == pastTicks) {
			return;
		}
		if (activeTicks > 0 && pastTicks == 0) {
			other.inactiveTick(metric);
			for (int i = 0; i < activeTicks; i++) {
				other.activeTick(metric);
			}
		} else if (activeTicks == 0 && pastTicks > 0) {
			other.activeTick(metric);
			for (int i = 0; i < pastTicks; i++) {
				other.inactiveTick(metric);
			}
		}
	}

	private static boolean useClientFlyingPacketTicks(
		SimulationEnvironment source,
		boolean receivesFlyingPackets
	) {
		if (!receivesFlyingPackets) {
			return false;
		}
		int ticksSinceAccurateFlyingPacket = source.ticksPast(MoveMetric.FLYING_PACKET_ACCURATE);
		int ticksSinceClientFlyingPacket = source.ticksPast(MoveMetric.FLYING_PACKET_CLIENT);
		if (ticksSinceClientFlyingPacket < ticksSinceAccurateFlyingPacket) {
			return source.receivedFlyingPacketIn(ticksSinceClientFlyingPacket);
		}
		return !source.receivedFlyingPacketIn(ticksSinceAccurateFlyingPacket);
	}

	private static Vector copyVector(Vector vector) {
		return vector == null ? null : vector.clone();
	}

	private static BoundingBox copyBoundingBox(BoundingBox box) {
		if (box == null) {
			return null;
		}
		return box.copy();
	}

	private static SimulationResult copySimulationResult(SimulationResult result) {
		if (result == null || !result.isValid()) {
			return SimulationResult.invalid();
		}
		return result.copy();
	}

	private static UnsupportedOperationException immutableCopyException() {
		return new UnsupportedOperationException("This environment copy is immutable");
	}
}

