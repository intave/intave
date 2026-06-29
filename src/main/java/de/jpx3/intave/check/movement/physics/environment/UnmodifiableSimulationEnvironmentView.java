package de.jpx3.intave.check.movement.physics.environment;

import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.check.movement.physics.MoveMetric;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import org.bukkit.Material;
import org.bukkit.util.Vector;

public final class UnmodifiableSimulationEnvironmentView implements SimulationEnvironment {
	private final SimulationEnvironment delegate;

	public UnmodifiableSimulationEnvironmentView(
		SimulationEnvironment delegate
	) {
		this.delegate = delegate;
	}

	public static UnmodifiableSimulationEnvironmentView of(SimulationEnvironment delegate) {
		return new UnmodifiableSimulationEnvironmentView(delegate);
	}

	@Override
	public Pose pose() {
		return delegate.pose();
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
	public Motion motion() {
		return delegate.motion();
	}

	@Override
	public double motionX() {
		return delegate.motionX();
	}

	@Override
	public double motionY() {
		return delegate.motionY();
	}

	@Override
	public double motionZ() {
		return delegate.motionZ();
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
	public boolean motionZReset() {
		return delegate.motionZReset();
	}

	@Override
	public Vector motionMultiplier() {
		return delegate.motionMultiplier();
	}

	@Override
	public void resetMotionMultiplier() {
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
	public float friction() {
		return delegate.friction();
	}

	@Override
	public double stepHeight() {
		return delegate.stepHeight();
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
	public double gravity() {
		return delegate.gravity();
	}

	@Override
	public float blockSpeedFactor() {
		return delegate.blockSpeedFactor();
	}

	@Override
	public boolean isSneaking() {
		return delegate.isSneaking();
	}

	@Override
	public boolean isSprinting() {
		return delegate.isSprinting();
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
	public boolean collidedHorizontally() {
		return delegate.collidedHorizontally();
	}

	@Override
	public boolean collidedVertically() {
		return delegate.collidedVertically();
	}

	@Override
	public void checkSupportingBlock(Motion motion) {
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
	public boolean isInVehicle() {
		return delegate.isInVehicle();
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
	public void setBeforeMoveColliderResult(ColliderResult result) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public ColliderResult beforeMoveColliderResult() {
		return delegate.beforeMoveColliderResult();
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
	public void resetPhysicsPacketRelinkFlyVL() {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
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
	public float width() {
		return delegate.width();
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
	public void tickComplete(boolean hasMovement, boolean hasRotation) {
		throw new UnsupportedOperationException("Cannot modify unmodifiable view");
	}

	@Override
	public SimulationEnvironment unmodifiable() {
		return this;
	}
}
