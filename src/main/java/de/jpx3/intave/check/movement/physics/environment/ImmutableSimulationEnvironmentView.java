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

import java.util.List;

public final class ImmutableSimulationEnvironmentView implements SimulationEnvironment {
	private final SimulationEnvironment delegate;

	public ImmutableSimulationEnvironmentView(
		SimulationEnvironment delegate
	) {
		this.delegate = delegate;
	}

	public static ImmutableSimulationEnvironmentView of(SimulationEnvironment delegate) {
		return new ImmutableSimulationEnvironmentView(delegate);
	}

	@Override
	public Pose pose() {
		return delegate.pose();
	}

	@Override
	public void setPose(Pose pose) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public Vector lookVector() {
		return delegate.lookVector();
	}

	@Override
	public void updateMovement(
		double newPositionX, double newPositionY, double newPositionZ,
		float newRotationYaw, float newRotationPitch,
		boolean hasMovement, boolean hasRotation
	) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public void setPosition(double x, double y, double z) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public void setRotation(float newRotationYaw, float newRotationPitch) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public User user() {
		return delegate.user();
	}

	@Override
	public Position position() {
		return delegate.position();
	}

	@Override
	public double positionX() {
		return delegate.positionX();
	}

	@Override
	public double positionY() {
		return delegate.positionY();
	}

	@Override
	public double positionZ() {
		return delegate.positionZ();
	}

	@Override
	public Position verifiedLastPosition() {
		return delegate.verifiedLastPosition();
	}

	@Override
	public double verifiedLastPositionX() {
		return delegate.verifiedLastPositionX();
	}

	@Override
	public double verifiedLastPositionY() {
		return delegate.verifiedLastPositionY();
	}

	@Override
	public double verifiedLastPositionZ() {
		return delegate.verifiedLastPositionZ();
	}

	@Override
	public void setVerifiedLastPosition(Position position, String reason) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public Position lastPosition() {
		return delegate.lastPosition();
	}

	@Override
	public double lastPositionX() {
		return delegate.lastPositionX();
	}

	@Override
	public double lastPositionY() {
		return delegate.lastPositionY();
	}

	@Override
	public double lastPositionZ() {
		return delegate.lastPositionZ();
	}

	@Override
	public float lastRotationYaw() {
		return delegate.lastRotationYaw();
	}

	@Override
	public float lastRotationPitch() {
		return delegate.lastRotationPitch();
	}

	@Override
	public void setLastRotation(float lastRotationYaw, float lastRotationPitch) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public void setLastPosition(double x, double y, double z) {
		throw new UnsupportedOperationException("This environment view is unmodifiable");
	}

	@Override
	public void setBoundingBox(BoundingBox boundingBox) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public BoundingBox boundingBox() {
		return delegate.boundingBox();
	}

	@Override
	public Motion sentOffsetMotion() {
		return delegate.sentOffsetMotion();
	}

	@Override
	public double offsetMotionX() {
		return delegate.offsetMotionX();
	}

	@Override
	public double offsetMotionY() {
		return delegate.offsetMotionY();
	}

	@Override
	public double offsetMotionZ() {
		return delegate.offsetMotionZ();
	}

	@Override
	public List<Motion> postTickMotionCandidates() {
		return delegate.postTickMotionCandidates();
	}

	@Override
	public void setPostTickMotionCandidates(@NotNull List<Motion> postTickMotionCandidates) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public Motion mutableBaseMotionCopy() {
		return delegate.mutableBaseMotionCopy();
	}

	@Override
	public double baseMotionX() {
		return delegate.baseMotionX();
	}

	@Override
	public double baseMotionY() {
		return delegate.baseMotionY();
	}

	@Override
	public double baseMotionZ() {
		return delegate.baseMotionZ();
	}

	@Override
	public void setBaseMotion(Motion baseMotion) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void setBaseMotion(double baseMotionX, double baseMotionY, double baseMotionZ) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean motionXReset() {
		return delegate.motionXReset();
	}

	@Override
	public void setMotionResetX(boolean reset) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean motionZReset() {
		return delegate.motionZReset();
	}

	@Override
	public void setMotionResetZ(boolean reset) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public Vector motionMultiplier() {
		return delegate.motionMultiplier();
	}

	@Override
	public void setMotionMultiplier(Vector motionMultiplier) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void resetMotionMultiplier() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public WorldBorder border() {
		return delegate.border();
	}

	@Override
	public void setWorldBorder(@NotNull WorldBorder worldBorder) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public float rotationYaw() {
		return delegate.rotationYaw();
	}

	@Override
	public float yawSine() {
		return delegate.yawSine();
	}

	@Override
	public float yawCosine() {
		return delegate.yawCosine();
	}

	@Override
	public float rotationPitch() {
		return delegate.rotationPitch();
	}

	@Override
	public float aiMoveSpeed(boolean sprinting) {
		return delegate.aiMoveSpeed(sprinting);
	}

	@Override
	public boolean shouldHaveFallFlyingPose() {
		return delegate.shouldHaveFallFlyingPose();
	}

	@Override
	public float friction(boolean sprinting) {
		return delegate.friction(sprinting);
	}

	@Override
	public double stepHeight() {
		return delegate.stepHeight();
	}

	@Override
	public void setStepHeight(float stepHeight) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public double resetMotion() {
		return delegate.resetMotion();
	}

	@Override
	public double jumpMotion() {
		return delegate.jumpMotion();
	}

	@Override
	public void setJumpMotion(double jumpMotion) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean isJumping() {
		return delegate.isJumping();
	}

	@Override
	public double gravity() {
		return delegate.gravity();
	}

	@Override
	public float blockSpeedFactor() {
		return delegate.blockSpeedFactor();
	}

	@Override
	public float jumpMovementFactor() {
		return delegate.jumpMovementFactor();
	}

	@Override
	public boolean isSneaking() {
		return delegate.isSneaking();
	}

	@Override
	public void setSneaking(boolean sneaking) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean isSprinting() {
		return delegate.isSprinting();
	}

	@Override
	public boolean lastSprinting() {
		return delegate.lastSprinting();
	}

	@Override
	public void setLastSprinting(boolean lastSprinting) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean isSleeping() {
		return delegate.isSleeping();
	}

	@Override
	public void setSleeping(boolean sleeping) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean hasSprintSpeed() {
		return delegate.hasSprintSpeed();
	}

	@Override
	public boolean sprintingAllowed() {
		return delegate.sprintingAllowed();
	}

	@Override
	public boolean inWater() {
		return delegate.inWater();
	}

	@Override
	public void setInWater(boolean inWater) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean inLava() {
		return delegate.inLava();
	}

	@Override
	public boolean inWeb() {
		return delegate.inWeb();
	}

	@Override
	public void resetInWeb() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean onGround() {
		return delegate.onGround();
	}

	@Override
	public boolean lastOnGround() {
		return delegate.lastOnGround();
	}

	@Override
	public void setLastOnGround(boolean lastOnGround) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean collidedHorizontally() {
		return delegate.collidedHorizontally();
	}

	@Override
	public boolean collidedVertically() {
		return delegate.collidedVertically();
	}

	@Override
	public BlockPosition mainSupportingBlockPos() {
		return delegate.mainSupportingBlockPos();
	}

	@Override
	public void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean onGroundNoBlocks() {
		return delegate.onGroundNoBlocks();
	}

	@Override
	public void setOnGroundNoBlocks(boolean onGroundNoBlocks) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void compileSpecialBlocks() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean collidedWithBoat() {
		return delegate.collidedWithBoat();
	}

	@Override
	public double frictionPosSubtraction() {
		return delegate.frictionPosSubtraction();
	}

	@Override
	public float frictionMultiplier() {
		return delegate.frictionMultiplier();
	}

	@Override
	public boolean receivedFlyingPacketIn(int ticks) {
		return delegate.receivedFlyingPacketIn(ticks);
	}

	@Override
	public Material collideMaterial() {
		return delegate.collideMaterial();
	}

	@Override
	public Material frictionMaterial() {
		return delegate.frictionMaterial();
	}

	@Override
	public Material previousCollideMaterial() {
		return delegate.previousCollideMaterial();
	}

	@Override
	public Material previousFrictionMaterial() {
		return delegate.previousFrictionMaterial();
	}

	@Override
	public void setCollideMaterial(Material collideMaterial) {
		throw new UnsupportedOperationException("Can not modify unmodifiable view");
	}

	@Override
	public void setFrictionMaterial(Material frictionMaterial) {
		throw new UnsupportedOperationException("Can not modify unmodifiable view");
	}

	@Override
	public void setPreviousCollideMaterial(Material previousCollideMaterial) {
		throw new UnsupportedOperationException("Can not modify unmodifiable view");
	}

	@Override
	public void setPreviousFrictionMaterial(Material previousFrictionMaterial) {
		throw new UnsupportedOperationException("Can not modify unmodifiable view");
	}

	@Override
	public boolean blockOnPositionSoulSpeedAffected() {
		return delegate.blockOnPositionSoulSpeedAffected();
	}

	@Override
	public double fallDistance() {
		return delegate.fallDistance();
	}

	@Override
	public void resetFallDistance() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void addFallDistance(double fallDistance) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean isInVehicle() {
		return delegate.isInVehicle();
	}

	@Override
	public Entity vehicle() {
		return delegate.vehicle();
	}

	@Override
	public Simulator simulator() {
		return delegate.simulator();
	}

	@Override
	public void setSimulator(Simulator simulator) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void dismountRidingEntity(String boatSetback) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void setPushedByEntity(boolean pushedByEntity) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean pushedByEntity() {
		return delegate.pushedByEntity();
	}

	@Override
	public void setSimulationResult(SimulationResult result) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public SimulationResult simulationResult() {
		return delegate.simulationResult();
	}

	@Override
	public void setLastMovementConfiguration(MovementConfiguration configuration) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public MovementConfiguration lastMovementConfiguration() {
		return delegate.lastMovementConfiguration();
	}

	@Override
	public int ticks(MoveMetric metric) {
		return delegate.ticks(metric);
	}

	@Override
	public int ticksPast(MoveMetric metric) {
		return delegate.ticksPast(metric);
	}

	@Override
	public void activeTick(MoveMetric metric) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void inactiveTick(MoveMetric metric) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public int reduceTicks() {
		return delegate.reduceTicks();
	}

	@Override
	public boolean denyJump() {
		return delegate.denyJump();
	}

	@Override
	public void setEyesInWater(boolean eyesInWater) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean areEyesInWater() {
		return delegate.areEyesInWater();
	}

	@Override
	public void setInteractingFluid(Fluid interactingFluid) {

	}

	@Override
	public void resetPhysicsPacketRelinkFlyVL() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public int physicsPacketRelinkFlyVL() {
		return delegate.physicsPacketRelinkFlyVL();
	}

	@Override
	public void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public double baseMoveSpeed() {
		return delegate.baseMoveSpeed();
	}

	@Override
	public int fireworkRocketsPower() {
		return delegate.fireworkRocketsPower();
	}

	@Override
	public int shulkerXToleranceRemaining() {
		return delegate.shulkerXToleranceRemaining();
	}

	@Override
	public int shulkerYToleranceRemaining() {
		return delegate.shulkerYToleranceRemaining();
	}

	@Override
	public int shulkerZToleranceRemaining() {
		return delegate.shulkerZToleranceRemaining();
	}

	@Override
	public int lowestShulkerY() {
		return delegate.lowestShulkerY();
	}

	@Override
	public int highestShulkerY() {
		return delegate.highestShulkerY();
	}

	@Override
	public int pistonMotionToleranceRemaining() {
		return delegate.pistonMotionToleranceRemaining();
	}

	@Override
	public double pistonVerticalAllowance() {
		return delegate.pistonVerticalAllowance();
	}

	@Override
	public double pistonHorizontalAllowance() {
		return delegate.pistonHorizontalAllowance();
	}

	@Override
	public BoundingBox pistonCollisionArea() {
		return delegate.pistonCollisionArea();
	}

	@Override
	public boolean physicsUnpredictableVelocityExpected() {
		return delegate.physicsUnpredictableVelocityExpected();
	}

	@Override
	public boolean enforceBoatStep() {
		return delegate.enforceBoatStep();
	}

	@Override
	public void setEnforceBoatStep(boolean enforceBoatStep) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public Status boatStatus() {
		return delegate.boatStatus();
	}

	@Override
	public void setBoatStatus(Status boatStatus) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public Status previousBoatStatus() {
		return delegate.previousBoatStatus();
	}

	@Override
	public void setPreviousBoatStatus(Status previousBoatStatus) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public float boatGlide() {
		return delegate.boatGlide();
	}

	@Override
	public void setBoatGlide(float boatGlide) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public double boatWaterLevel() {
		return delegate.boatWaterLevel();
	}

	@Override
	public void setBoatWaterLevel(double boatWaterLevel) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public boolean lastSneaking() {
		return delegate.lastSneaking();
	}

	@Override
	public boolean currentlyInBlock() {
		return delegate.currentlyInBlock();
	}

	@Override
	public int highestLocalRiptideLevel() {
		return delegate.highestLocalRiptideLevel();
	}

	@Override
	public boolean onGroundWithRiptide() {
		return delegate.onGroundWithRiptide();
	}

	@Override
	public void updateEyesInWater() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void aquaticUpdateLavaReset() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public float height() {
		return delegate.height();
	}

	@Override
	public void setHeight(float height) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public float width() {
		return delegate.width();
	}

	@Override
	public void setWidth(float width) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public double heightRounded() {
		return delegate.heightRounded();
	}

	@Override
	public double widthRounded() {
		return delegate.widthRounded();
	}

	@Override
	public float eyeHeight() {
		return delegate.eyeHeight();
	}

	@Override
	public Fluid interactingFluid() {
		return delegate.interactingFluid();
	}

	@Override
	public void assumeOccurred(Simulation simulation) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public void tickComplete(boolean hasMovement, boolean hasRotation, boolean isRealClientTick) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public long currentTick() {
		return delegate.currentTick();
	}

	@Override
	public long activeSequence() {
		return delegate.activeSequence();
	}

	@Override
	public void setActiveSequence(long activeSequence) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public List<TickAmbiguousUpdate> allTickAmbiguousUpdates() {
		return delegate.allTickAmbiguousUpdates();
	}

	@Override
	public void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public SimulationEnvironment mutableView() {
		return delegate.mutableView();
	}

	@Override
	public int depth() {
		return delegate.depth() + 1;
	}

	@Override
	public void commitTo(SimulationEnvironment other) {
		delegate.commitTo(other);
	}

	@Override
	public SimulationEnvironment immutableView() {
		return this;
	}
}
