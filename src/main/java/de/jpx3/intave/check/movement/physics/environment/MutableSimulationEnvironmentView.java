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
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.tick.piston.PistonSlimeMovement;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator.Status;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics.resolveFriction;
import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

final class MutableSimulationEnvironmentView implements SimulationEnvironment {
  private final SimulationEnvironment delegate;
  private List<EnvironmentMutation> deferredMutations;

  private Pose pose;
  private boolean poseOverridden;
  private boolean positionOverridden;
  private double positionX, positionY, positionZ;
  private boolean verifiedLastPositionOverridden;
  private double verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ;
  private String verifiedLastPositionReason;
  private boolean lastPositionOverridden;
  private double lastPositionX, lastPositionY, lastPositionZ;
  private boolean motionOverridden;
  private double motionX, motionY, motionZ;
  private boolean baseMotionOverridden;
  private double baseMotionX, baseMotionY, baseMotionZ;
  private boolean motionXResetOverridden, motionXReset;
  private boolean motionZResetOverridden, motionZReset;
  private boolean motionMultiplierOverridden;
  private Vector motionMultiplier;
  private boolean rotationOverridden;
  private float rotationYaw, yawSine, yawCosine, rotationPitch;
  private boolean lastRotationOverridden;
  private float lastRotationYaw, lastRotationPitch;
  private Vector lookVector;
  private boolean boundingBoxOverridden;
  private BoundingBox boundingBox;
  private boolean jumpMotionOverridden;
  private double jumpMotion;
  private boolean hasJumpedInTickOverridden;
  private boolean hasJumpedInTick;
  private boolean inWaterOverridden, inWater;
  private boolean inLavaOverridden, inLava;
  private boolean lavaDepthOverridden;
  private double lavaDepth;
  private boolean inWebOverridden, inWeb;
  private boolean onGroundOverridden, onGround;
  private boolean lastOnGroundOverridden, lastOnGround;
  private boolean sneakingOverridden, sneaking;
  private boolean crouchingInputSlowdownOverridden, crouchingInputSlowdown;
  private boolean itemUseSpeedMultiplierOverridden;
  private float itemUseSpeedMultiplier;
  private boolean lastSprintingOverridden, lastSprinting;
  private boolean swimmingOverridden, swimming;
  private boolean flyingOverridden, flying;
  private boolean flyingDisablePendingOverridden, flyingDisablePending;
  private boolean legacyItemUseSlotSwitchPendingOverridden;
  private boolean legacyItemUseSlotSwitchPending;
  private boolean legacyItemUseReleaseUncertainOverridden;
  private boolean legacyItemUseReleaseUncertain;
  private boolean legacyItemUseForcedReleaseAppliedOverridden;
  private boolean legacyItemUseForcedReleaseApplied;
  private boolean collidedHorizontallyOverridden, collidedHorizontally;
  private boolean collidedVerticallyOverridden, collidedVertically;
  private boolean fallDistanceOverridden;
  private double fallDistance;
  private boolean pushedByEntityOverridden, pushedByEntity;
  private boolean configurationOverridden;
  private boolean simulationResultOverridden;
  private MovementConfiguration configuration;
  private SimulationResult simulationResult;
  private boolean interactingFluidOverridden;
  private Fluid interactingFluid;
  private boolean eyesInWaterOverridden;
  private boolean eyesInWater;
  private boolean inVehicleOverridden, inVehicle;
  private boolean enforceBoatStepOverridden, enforceBoatStep;
  private boolean boatStatusOverridden, previousBoatStatusOverridden;
  private Status boatStatus, previousBoatStatus;
  private boolean boatGlideOverridden, boatWaterLevelOverridden;
  private float boatGlide;
  private double boatWaterLevel;
  private boolean physicsPacketRelinkFlyVLOverridden;
  private int physicsPacketRelinkFlyVL;
  private WorldBorder worldBorder;
  private boolean worldBorderOverridden;
  private long currentTick = 0;
  private boolean currentTickOverridden;
  private long activeSequence = 0;
  private boolean activeSequenceOverridden;
  private boolean widthOverridden, heightOverridden;
  private float width, height;
  private double widthRounded, heightRounded;
  private boolean simulatorOverridden;
  private Simulator simulator;
  private boolean stepHeightOverridden;
  private float stepHeight;
  private boolean postTickMotionCandidatesOverridden;
  private boolean pistonSlimeMovementsOverridden;
  private boolean shulkerBoxesOverridden;
  private BlockPosition mainSupportingBlockPos;
  private boolean mainSupportingBlockPosOverridden;
  private boolean onGroundNoBlocks;
  private boolean onGroundNoBlocksOverridden;
  private Material frictionMaterial = Material.AIR, previousFrictionMaterial = Material.AIR;
  private Material collideMaterial = Material.AIR, previousCollideMaterial = Material.AIR;
  private boolean frictionMaterialOverridden, collideMaterialOverridden,
    previousFrictionMaterialOverridden, previousCollideMaterialOverridden;
  private List<PostTickSimulation> postTickSimulations;
  private List<PistonSlimeMovement> pistonSlimeMovements;
  private Map<BlockPosition, ShulkerBox> shulkerBoxes;
  private boolean sleepingOverridden;
  private boolean sleeping;

  private EnumMap<MoveMetric, Integer> activeTrackerOverrides;
  private EnumMap<MoveMetric, Integer> pastTrackerOverrides;

  MutableSimulationEnvironmentView(SimulationEnvironment delegate) {
    this.delegate = delegate;
  }

  private void defer(EnvironmentMutation mutation) {
    if (deferredMutations == null) {
      deferredMutations = new ArrayList<>(4);
    }
    deferredMutations.add(mutation);
  }

  @Override
  public Pose pose() {
    return poseOverridden ? pose : delegate.pose();
  }

  @Override
  public void setPose(Pose pose) {
    poseOverridden = true;
    this.pose = pose;
    updateSize();
    if (user() != null) {
      boundingBoxOverridden = true;
      boundingBox = BoundingBox.fromPosition(user(), this, position());
    }
    defer(environment -> environment.setPose(pose));
  }

  @Override
  public Vector lookVector() {
    Vector vector = rotationOverridden ? lookVector : delegate.lookVector();
    return vector == null ? null : vector.clone();
  }

  @Override
  public void updateMovement(
    double newPositionX, double newPositionY, double newPositionZ,
    float newRotationYaw, float newRotationPitch,
    boolean hasMovement, boolean hasRotation
  ) {
    setLastPositionOverride(positionX(), positionY(), positionZ());
    if (hasMovement) {
      setPositionOverride(newPositionX, newPositionY, newPositionZ);
    }
    setLastRotationOverride(rotationYaw(), rotationPitch());
    if (hasRotation) {
      setRotationOverride(newRotationYaw, newRotationPitch);
    }
    if (hasMovement || hasRotation) {
      setMotionOverride(
        positionX() - verifiedLastPositionX(),
        positionY() - verifiedLastPositionY(),
        positionZ() - verifiedLastPositionZ()
      );
    }
    defer(environment -> environment.updateMovement(
      newPositionX, newPositionY, newPositionZ,
      newRotationYaw, newRotationPitch,
      hasMovement, hasRotation
    ));
  }

  @Override
  public void setPosition(double x, double y, double z) {
    setLastPosition(positionX(), positionY(), positionZ());
    setPositionOverride(x, y, z);
    setBoundingBox(BoundingBox.fromPosition(user(), this, x, y, z));
    defer(environment -> environment.setPosition(x, y, z));
  }

  @Override
  public void setRotation(float newRotationYaw, float newRotationPitch) {
    setRotationOverride(newRotationYaw, newRotationPitch);
    defer(environment -> environment.setRotation(newRotationYaw, newRotationPitch));
  }

  @Override
  public User user() {
    return delegate.user();
  }

  @Override
  public double positionX() {
    return positionOverridden ? positionX : delegate.positionX();
  }

  @Override
  public double positionY() {
    return positionOverridden ? positionY : delegate.positionY();
  }

  @Override
  public double positionZ() {
    return positionOverridden ? positionZ : delegate.positionZ();
  }

  @Override
  public double verifiedLastPositionX() {
    return verifiedLastPositionOverridden ? verifiedLastPositionX : delegate.verifiedLastPositionX();
  }

  @Override
  public double verifiedLastPositionY() {
    return verifiedLastPositionOverridden ? verifiedLastPositionY : delegate.verifiedLastPositionY();
  }

  @Override
  public double verifiedLastPositionZ() {
    return verifiedLastPositionOverridden ? verifiedLastPositionZ : delegate.verifiedLastPositionZ();
  }

  @Override
  public void setVerifiedLastPosition(Position position, String reason) {
    verifiedLastPositionOverridden = true;
    verifiedLastPositionX = position.getX();
    verifiedLastPositionY = position.getY();
    verifiedLastPositionZ = position.getZ();
    verifiedLastPositionReason = reason;
    defer(environment -> environment.setVerifiedLastPosition(position, reason));
  }

  @Override
  public double lastPositionX() {
    return lastPositionOverridden ? lastPositionX : delegate.lastPositionX();
  }

  @Override
  public double lastPositionY() {
    return lastPositionOverridden ? lastPositionY : delegate.lastPositionY();
  }

  @Override
  public double lastPositionZ() {
    return lastPositionOverridden ? lastPositionZ : delegate.lastPositionZ();
  }

  @Override
  public float lastRotationYaw() {
    return lastRotationOverridden ? lastRotationYaw : delegate.lastRotationYaw();
  }

  @Override
  public float lastRotationPitch() {
    return lastRotationOverridden ? lastRotationPitch : delegate.lastRotationPitch();
  }

  @Override
  public void setLastRotation(float lastRotationYaw, float lastRotationPitch) {
    setLastRotationOverride(lastRotationYaw, lastRotationPitch);
    defer(environment -> environment.setLastRotation(lastRotationYaw, lastRotationPitch));
  }

  @Override
  public void setLastPosition(double x, double y, double z) {
    setLastPositionOverride(x, y, z);
    defer(environment -> environment.setLastPosition(x, y, z));
  }

  @Override
  public void setBoundingBox(BoundingBox boundingBox) {
    boundingBoxOverridden = true;
    this.boundingBox = boundingBox;
    defer(environment -> environment.setBoundingBox(boundingBox));
  }

  @Override
  public BoundingBox boundingBox() {
    return boundingBoxOverridden ? boundingBox : delegate.boundingBox();
  }

  @Override
  public double offsetMotionX() {
    return motionOverridden ? motionX : delegate.offsetMotionX();
  }

  @Override
  public double offsetMotionY() {
    return motionOverridden ? motionY : delegate.offsetMotionY();
  }

  @Override
  public double offsetMotionZ() {
    return motionOverridden ? motionZ : delegate.offsetMotionZ();
  }

  @Override
  public List<PostTickSimulation> postTickMotionCandidates() {
    return postTickMotionCandidatesOverridden ? Collections.unmodifiableList(postTickSimulations) : delegate.postTickMotionCandidates();
  }

  @Override
  public void setPostTickMotionCandidates(@NotNull List<PostTickSimulation> postTickSimulations) {
    postTickMotionCandidatesOverridden = true;
    List<PostTickSimulation> candidatesCopy = new ArrayList<>(postTickSimulations);
    this.postTickSimulations = candidatesCopy;
    defer(environment -> environment.setPostTickMotionCandidates(candidatesCopy));
  }

  @Override
  public List<PistonSlimeMovement> pistonSlimeMovements() {
    if (!pistonSlimeMovementsOverridden) {
      return delegate.pistonSlimeMovements();
    }
    return pistonSlimeMovements.isEmpty()
      ? Collections.emptyList()
      : Collections.unmodifiableList(pistonSlimeMovements);
  }

  @Override
  public void setPistonSlimeMovements(@NotNull List<PistonSlimeMovement> pistonSlimeMovements) {
    pistonSlimeMovementsOverridden = true;
    List<PistonSlimeMovement> movementsCopy = new ArrayList<>(pistonSlimeMovements);
    this.pistonSlimeMovements = movementsCopy;
    defer(environment -> environment.setPistonSlimeMovements(movementsCopy));
  }

  @Override
  public Map<BlockPosition, ShulkerBox> shulkerBoxes() {
    if (!shulkerBoxesOverridden) {
      return delegate.shulkerBoxes();
    }
    return shulkerBoxes.isEmpty()
      ? Collections.emptyMap()
      : Collections.unmodifiableMap(shulkerBoxes);
  }

  @Override
  public void setShulkerBoxes(@NotNull Map<BlockPosition, ShulkerBox> shulkerBoxes) {
    shulkerBoxesOverridden = true;
    Map<BlockPosition, ShulkerBox> boxesCopy = new LinkedHashMap<>(shulkerBoxes);
    this.shulkerBoxes = boxesCopy;
    defer(environment -> environment.setShulkerBoxes(boxesCopy));
  }

  @Override
  public double baseMotionX() {
    return baseMotionOverridden ? baseMotionX : delegate.baseMotionX();
  }

  @Override
  public double baseMotionY() {
    return baseMotionOverridden ? baseMotionY : delegate.baseMotionY();
  }

  @Override
  public double baseMotionZ() {
    return baseMotionOverridden ? baseMotionZ : delegate.baseMotionZ();
  }

  @Override
  public void setBaseMotion(double baseMotionX, double baseMotionY, double baseMotionZ) {
    baseMotionOverridden = true;
    this.baseMotionX = baseMotionX;
    this.baseMotionY = baseMotionY;
    this.baseMotionZ = baseMotionZ;
    defer(environment -> environment.setBaseMotion(baseMotionX, baseMotionY, baseMotionZ));
  }

  @Override
  public boolean motionXReset() {
    return motionXResetOverridden ? motionXReset : delegate.motionXReset();
  }

  @Override
  public void setMotionResetX(boolean reset) {
    motionXResetOverridden = true;
    motionXReset = reset;
    defer(environment -> environment.setMotionResetX(reset));
  }

  @Override
  public boolean motionZReset() {
    return motionZResetOverridden ? motionZReset : delegate.motionZReset();
  }

  @Override
  public void setMotionResetZ(boolean reset) {
    motionZResetOverridden = true;
    motionZReset = reset;
    defer(environment -> environment.setMotionResetZ(reset));
  }

  @Override
  public Vector motionMultiplier() {
    Vector vector = motionMultiplierOverridden ? motionMultiplier : delegate.motionMultiplier();
    return vector == null ? null : vector.clone();
  }

  @Override
  public void setMotionMultiplier(Vector motionMultiplier) {
    Vector copy = motionMultiplier.clone();
    motionMultiplierOverridden = true;
    this.motionMultiplier = copy;
    fallDistanceOverridden = true;
    fallDistance = 0.0;
    defer(environment -> environment.setMotionMultiplier(copy.clone()));
  }

  @Override
  public void resetMotionMultiplier() {
    motionMultiplierOverridden = true;
    motionMultiplier = null;
    defer(SimulationEnvironment::resetMotionMultiplier);
  }

  @Override
  public WorldBorder border() {
    return worldBorderOverridden ? worldBorder : delegate.border();
  }

  @Override
  public void setWorldBorder(@NotNull WorldBorder worldBorder) {
    worldBorderOverridden = true;
    this.worldBorder = worldBorder;
    defer(environment -> environment.setWorldBorder(worldBorder));
  }

  @Override
  public float rotationYaw() {
    return rotationOverridden ? rotationYaw : delegate.rotationYaw();
  }

  @Override
  public float yawSine() {
    return rotationOverridden ? yawSine : delegate.yawSine();
  }

  @Override
  public float yawCosine() {
    return rotationOverridden ? yawCosine : delegate.yawCosine();
  }

  @Override
  public float rotationPitch() {
    return rotationOverridden ? rotationPitch : delegate.rotationPitch();
  }

  @Override
  public float aiMoveSpeed(boolean sprinting) {
    return delegate.aiMoveSpeed(sprinting);
  }

  @Override
  public boolean shouldHaveFallFlyingPose() {
    // not yet modifiable
    return delegate.shouldHaveFallFlyingPose();
  }

  @Override
  public float friction(boolean sprinting) {
    // A simulated landing changes the next tick's ground acceleration inside this branch.
    if (!lastOnGroundOverridden || !lastOnGround || delegate.lastOnGround()) {
      return delegate.friction(sprinting);
    }
    return resolveFriction(
      user(), this, sprinting,
      verifiedLastPositionX(), verifiedLastPositionY(), verifiedLastPositionZ()
    );
  }

  @Override
  public double stepHeight() {
    return stepHeightOverridden ? stepHeight : delegate.stepHeight();
  }

  @Override
  public void setStepHeight(float stepHeight) {
    stepHeightOverridden = true;
    this.stepHeight = stepHeight;
  }

  @Override
  public double resetMotion() {
    return delegate.resetMotion();
  }

  @Override
  public double jumpMotion() {
    return jumpMotionOverridden ? jumpMotion : delegate.jumpMotion();
  }

  @Override
  public void setJumpMotion(double jumpMotion) {
    jumpMotionOverridden = true;
    this.jumpMotion = jumpMotion;
    defer(environment -> environment.setJumpMotion(jumpMotion));
  }

  @Override
  public boolean isJumping() {
    return hasJumpedInTickOverridden ? hasJumpedInTick : delegate.isJumping();
  }

  @Override
  public double gravity() {
    return delegate.gravity();
  }

  @Override
  public float rawJumpMovementFactor() {
    return delegate.rawJumpMovementFactor();
  }

  @Override
  public boolean isSneaking() {
    return sneakingOverridden ? sneaking : delegate.isSneaking();
  }

  @Override
  public void setSneaking(boolean sneaking) {
    sneakingOverridden = true;
    this.sneaking = sneaking;
    defer(environment -> environment.setSneaking(sneaking));
  }

  @Override
  public boolean isSprinting() {
    return delegate.isSprinting();
  }

  @Override
  public boolean lastSprinting() {
    return lastSprintingOverridden ? lastSprinting : delegate.lastSprinting();
  }

  @Override
  public void setLastSprinting(boolean lastSprinting) {
    lastSprintingOverridden = true;
    this.lastSprinting = lastSprinting;
    defer(environment -> environment.setLastSprinting(lastSprinting));
  }

  @Override
  public boolean isSwimming() {
    return swimmingOverridden ? swimming : delegate.isSwimming();
  }

  @Override
  public void setSwimming(boolean swimming) {
    swimmingOverridden = true;
    this.swimming = swimming;
    defer(environment -> environment.setSwimming(swimming));
  }

  @Override
  public boolean flying() {
    return flyingOverridden ? flying : delegate.flying();
  }

  @Override
  public void setFlying(boolean flying) {
    flyingOverridden = true;
    this.flying = flying;
    defer(environment -> environment.setFlying(flying));
  }

  @Override
  public boolean flyingDisablePending() {
    return flyingDisablePendingOverridden
      ? flyingDisablePending
      : delegate.flyingDisablePending();
  }

  @Override
  public void setFlyingDisablePending(boolean pending) {
    flyingDisablePendingOverridden = true;
    flyingDisablePending = pending;
    defer(environment -> environment.setFlyingDisablePending(pending));
  }

  @Override
  public boolean legacyItemUseSlotSwitchPending() {
    return legacyItemUseSlotSwitchPendingOverridden
      ? legacyItemUseSlotSwitchPending
      : delegate.legacyItemUseSlotSwitchPending();
  }

  @Override
  public void setLegacyItemUseSlotSwitchPending(boolean pending) {
    legacyItemUseSlotSwitchPendingOverridden = true;
    legacyItemUseSlotSwitchPending = pending;
    defer(environment -> environment.setLegacyItemUseSlotSwitchPending(pending));
  }

  @Override
  public boolean legacyItemUseReleaseUncertain() {
    return legacyItemUseReleaseUncertainOverridden
      ? legacyItemUseReleaseUncertain
      : delegate.legacyItemUseReleaseUncertain();
  }

  @Override
  public void setLegacyItemUseReleaseUncertain(boolean uncertain) {
    legacyItemUseReleaseUncertainOverridden = true;
    legacyItemUseReleaseUncertain = uncertain;
    defer(environment -> environment.setLegacyItemUseReleaseUncertain(uncertain));
  }

  @Override
  public boolean legacyItemUseForcedReleaseApplied() {
    return legacyItemUseForcedReleaseAppliedOverridden
      ? legacyItemUseForcedReleaseApplied
      : delegate.legacyItemUseForcedReleaseApplied();
  }

  @Override
  public void setLegacyItemUseForcedReleaseApplied(boolean applied) {
    legacyItemUseForcedReleaseAppliedOverridden = true;
    legacyItemUseForcedReleaseApplied = applied;
    defer(environment -> environment.setLegacyItemUseForcedReleaseApplied(applied));
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
  public boolean isSleeping() {
    return sleepingOverridden ? sleeping : delegate.isSleeping();
  }

  @Override
  public void setSleeping(boolean sleeping) {
    sleepingOverridden = true;
    this.sleeping = sleeping;
    defer(environment -> environment.setSleeping(sleeping));
  }

  @Override
  public boolean inWater() {
    return inWaterOverridden ? inWater : delegate.inWater();
  }

  @Override
  public void setInWater(boolean inWater) {
    inWaterOverridden = true;
    this.inWater = inWater;
    if (inWater) {
      fallDistanceOverridden = true;
      fallDistance = 0.0;
    }
    defer(environment -> environment.setInWater(inWater));
  }

  @Override
  public boolean inLava() {
    return inLavaOverridden ? inLava : delegate.inLava();
  }

  @Override
  public void setInLava(boolean inLava) {
    inLavaOverridden = true;
    this.inLava = inLava;
    if (!inLava) {
      lavaDepthOverridden = true;
      lavaDepth = 0.0;
    }
    defer(environment -> environment.setInLava(inLava));
  }

  @Override
  public double lavaDepth() {
    return lavaDepthOverridden ? lavaDepth : delegate.lavaDepth();
  }

  @Override
  public void setLavaDepth(double lavaDepth) {
    lavaDepthOverridden = true;
    this.lavaDepth = Math.max(0.0, lavaDepth);
    if (this.lavaDepth > 0.0) {
      inLavaOverridden = true;
      inLava = true;
    }
    defer(environment -> environment.setLavaDepth(lavaDepth));
  }

  @Override
  public boolean inWeb() {
    return inWebOverridden ? inWeb : delegate.inWeb();
  }

  @Override
  public void setInWeb(boolean inWeb) {
    inWebOverridden = true;
    this.inWeb = inWeb;
    defer(environment -> environment.setInWeb(inWeb));
  }

  @Override
  public void resetInWeb() {
    setInWeb(false);
  }

  @Override
  public boolean onGround() {
    return onGroundOverridden ? onGround : delegate.onGround();
  }

  @Override
  public boolean lastOnGround() {
    return lastOnGroundOverridden ? lastOnGround : delegate.lastOnGround();
  }

  @Override
  public void setLastOnGround(boolean lastOnGround) {
    lastOnGroundOverridden = true;
    this.lastOnGround = lastOnGround;
  }

  @Override
  public boolean collidedHorizontally() {
    return collidedHorizontallyOverridden ? collidedHorizontally : delegate.collidedHorizontally();
  }

  @Override
  public boolean collidedVertically() {
    return collidedVerticallyOverridden ? collidedVertically : delegate.collidedVertically();
  }

  @Override
  public BlockPosition mainSupportingBlockPos() {
    return mainSupportingBlockPosOverridden ? this.mainSupportingBlockPos : delegate.mainSupportingBlockPos();
  }

  @Override
  public void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos) {
    mainSupportingBlockPosOverridden = true;
    this.mainSupportingBlockPos = mainSupportingBlockPos;
    defer(environment -> environment.setMainSupportingBlockPos(mainSupportingBlockPos));
  }

  @Override
  public boolean onGroundNoBlocks() {
    return onGroundNoBlocksOverridden ? onGroundNoBlocks : delegate.onGroundNoBlocks();
  }

  @Override
  public void setOnGroundNoBlocks(boolean onGroundNoBlocks) {
    this.onGroundNoBlocks = onGroundNoBlocks;
    this.onGroundNoBlocksOverridden = true;
    defer(environment -> environment.setOnGroundNoBlocks(onGroundNoBlocks));
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
    if (hasMetricOverride(FLYING_PACKET_ACCURATE) || hasMetricOverride(FLYING_PACKET_CLIENT)) {
      return ticksPast(FLYING_PACKET_ACCURATE) <= ticks || ticksPast(FLYING_PACKET_CLIENT) <= ticks;
    }
    return delegate.receivedFlyingPacketIn(ticks);
  }

  @Override
  public Material collideMaterial() {
    return collideMaterialOverridden ? collideMaterial : delegate.collideMaterial();
  }

  @Override
  public Material frictionMaterial() {
    return frictionMaterialOverridden ? frictionMaterial : delegate.frictionMaterial();
  }

  @Override
  public Material previousCollideMaterial() {
    return previousCollideMaterialOverridden ? previousCollideMaterial : delegate.previousCollideMaterial();
  }

  @Override
  public Material previousFrictionMaterial() {
    return previousFrictionMaterialOverridden ? previousFrictionMaterial : delegate.previousFrictionMaterial();
  }

  @Override
  public void setCollideMaterial(Material collideMaterial) {
    collideMaterialOverridden = true;
    this.collideMaterial = collideMaterial;
    defer(environment -> environment.setCollideMaterial(collideMaterial));
  }

  @Override
  public void setFrictionMaterial(Material frictionMaterial) {
    frictionMaterialOverridden = true;
    this.frictionMaterial = frictionMaterial;
    defer(environment -> environment.setFrictionMaterial(frictionMaterial));
  }

  @Override
  public void setPreviousCollideMaterial(Material previousCollideMaterial) {
    previousCollideMaterialOverridden = true;
    this.previousCollideMaterial = previousCollideMaterial;
    defer(environment -> environment.setPreviousCollideMaterial(previousCollideMaterial));
  }

  @Override
  public void setPreviousFrictionMaterial(Material previousFrictionMaterial) {
    previousFrictionMaterialOverridden = true;
    this.previousFrictionMaterial = previousFrictionMaterial;
    defer(environment -> environment.setPreviousFrictionMaterial(previousFrictionMaterial));
  }

  @Override
  public boolean blockOnPositionSoulSpeedAffected() {
    return delegate.blockOnPositionSoulSpeedAffected();
  }

  @Override
  public double fallDistance() {
    return fallDistanceOverridden ? fallDistance : delegate.fallDistance();
  }

  @Override
  public void resetFallDistance() {
    fallDistanceOverridden = true;
    fallDistance = 0.0;
    defer(SimulationEnvironment::resetFallDistance);
  }

  @Override
  public void addFallDistance(double fallDistance) {
    double currentFallDistance = fallDistance();
    fallDistanceOverridden = true;
    this.fallDistance = currentFallDistance + fallDistance;
    defer(environment -> environment.addFallDistance(fallDistance));
  }

  @Override
  public boolean isInVehicle() {
    return inVehicleOverridden ? inVehicle : delegate.isInVehicle();
  }

  @Override
  public Entity vehicle() {
    return delegate.vehicle();
  }

  @Override
  public Simulator simulator() {
    return simulatorOverridden ? simulator : delegate.simulator();
  }

  @Override
  public void setSimulator(Simulator simulator) {
    simulatorOverridden = true;
    this.simulator = simulator;
  }

  @Override
  public void dismountRidingEntity(String boatSetback) {
    inVehicleOverridden = true;
    inVehicle = false;
    defer(environment -> environment.dismountRidingEntity(boatSetback));
  }

  @Override
  public void setPushedByEntity(boolean pushedByEntity) {
    pushedByEntityOverridden = true;
    this.pushedByEntity = pushedByEntity;
    defer(environment -> environment.setPushedByEntity(pushedByEntity));
  }

  @Override
  public boolean pushedByEntity() {
    return pushedByEntityOverridden ? pushedByEntity : delegate.pushedByEntity();
  }

  @Override
  public void setSimulationResult(SimulationResult result) {
    simulationResultOverridden = true;
    simulationResult = result;
    defer(environment -> environment.setSimulationResult(result));
  }

  @Override
  public SimulationResult simulationResult() {
    return simulationResultOverridden ? simulationResult : delegate.simulationResult();
  }

  @Override
  public void setLastMovementConfiguration(MovementConfiguration configuration) {
    configurationOverridden = true;
    this.configuration = configuration;
    defer(environment -> environment.setLastMovementConfiguration(configuration));
  }

  @Override
  public MovementConfiguration lastMovementConfiguration() {
    return configurationOverridden ? configuration : delegate.lastMovementConfiguration();
  }

  @Override
  public int ticks(MoveMetric metric) {
    if (activeTrackerOverrides != null) {
      Integer override = activeTrackerOverrides.get(metric);
      if (override != null) {
        return override;
      }
    }
    return delegate.ticks(metric);
  }

  @Override
  public int ticksPast(MoveMetric metric) {
    if (pastTrackerOverrides != null) {
      Integer override = pastTrackerOverrides.get(metric);
      if (override != null) {
        return override;
      }
    }
    return delegate.ticksPast(metric);
  }

  @Override
  public void activeTick(MoveMetric metric) {
    activeTickOverride(metric);
    defer(environment -> environment.activeTick(metric));
  }

  @Override
  public void inactiveTick(MoveMetric metric) {
    inactiveTickOverride(metric);
    defer(environment -> environment.inactiveTick(metric));
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
    eyesInWaterOverridden = true;
    this.eyesInWater = eyesInWater;
    defer(environment -> environment.setEyesInWater(eyesInWater));
  }

  @Override
  public boolean areEyesInWater() {
    return eyesInWaterOverridden ? eyesInWater : delegate.areEyesInWater();
  }

  @Override
  public void setInteractingFluid(Fluid interactingFluid) {
    interactingFluidOverridden = true;
    this.interactingFluid = interactingFluid;
    defer(environment -> environment.setInteractingFluid(interactingFluid));
  }

  @Override
  public void resetPhysicsPacketRelinkFlyVL() {
    physicsPacketRelinkFlyVLOverridden = true;
    physicsPacketRelinkFlyVL = 0;
    defer(SimulationEnvironment::resetPhysicsPacketRelinkFlyVL);
  }

  @Override
  public int physicsPacketRelinkFlyVL() {
    return physicsPacketRelinkFlyVLOverridden ? physicsPacketRelinkFlyVL : delegate.physicsPacketRelinkFlyVL();
  }

  @Override
  public void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
    physicsPacketRelinkFlyVLOverridden = true;
    this.physicsPacketRelinkFlyVL = physicsPacketRelinkFlyVL;
    defer(environment -> environment.setPhysicsPacketRelinkFlyVL(physicsPacketRelinkFlyVL));
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
  public int activeFireworkRockets() {
    return delegate.activeFireworkRockets();
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
    return enforceBoatStepOverridden ? enforceBoatStep : delegate.enforceBoatStep();
  }

  @Override
  public void setEnforceBoatStep(boolean enforceBoatStep) {
    enforceBoatStepOverridden = true;
    this.enforceBoatStep = enforceBoatStep;
    defer(environment -> environment.setEnforceBoatStep(enforceBoatStep));
  }

  @Override
  public Status boatStatus() {
    return boatStatusOverridden ? boatStatus : delegate.boatStatus();
  }

  @Override
  public void setBoatStatus(Status boatStatus) {
    boatStatusOverridden = true;
    this.boatStatus = boatStatus;
    defer(environment -> environment.setBoatStatus(boatStatus));
  }

  @Override
  public Status previousBoatStatus() {
    return previousBoatStatusOverridden ? previousBoatStatus : delegate.previousBoatStatus();
  }

  @Override
  public void setPreviousBoatStatus(Status previousBoatStatus) {
    previousBoatStatusOverridden = true;
    this.previousBoatStatus = previousBoatStatus;
    defer(environment -> environment.setPreviousBoatStatus(previousBoatStatus));
  }

  @Override
  public float boatGlide() {
    return boatGlideOverridden ? boatGlide : delegate.boatGlide();
  }

  @Override
  public void setBoatGlide(float boatGlide) {
    boatGlideOverridden = true;
    this.boatGlide = boatGlide;
    defer(environment -> environment.setBoatGlide(boatGlide));
  }

  @Override
  public double boatWaterLevel() {
    return boatWaterLevelOverridden ? boatWaterLevel : delegate.boatWaterLevel();
  }

  @Override
  public void setBoatWaterLevel(double boatWaterLevel) {
    boatWaterLevelOverridden = true;
    this.boatWaterLevel = boatWaterLevel;
    defer(environment -> environment.setBoatWaterLevel(boatWaterLevel));
  }

  @Override
  public boolean lastSneaking() {
    return delegate.lastSneaking();
  }

  @Override
  public boolean resolveCrouchingInputSlowdown(boolean fallback) {
    return crouchingInputSlowdownOverridden ? crouchingInputSlowdown : delegate.resolveCrouchingInputSlowdown(fallback);
  }

  @Override
  public void overrideCrouchingInputSlowdown(boolean slowdown) {
    crouchingInputSlowdownOverridden = true;
    crouchingInputSlowdown = slowdown;
  }

  @Override
  public float itemUseSpeedMultiplier() {
    return itemUseSpeedMultiplierOverridden
      ? itemUseSpeedMultiplier : delegate.itemUseSpeedMultiplier();
  }

  @Override
  public void overrideItemUseSpeedMultiplier(float multiplier) {
    itemUseSpeedMultiplierOverridden = true;
    itemUseSpeedMultiplier = multiplier;
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
    SimulationEnvironment.super.updateEyesInWater();
  }

  @Override
  public void aquaticUpdateLavaReset() {
    inLavaOverridden = true;
    inLava = false;
    lavaDepthOverridden = true;
    lavaDepth = 0.0;
    defer(SimulationEnvironment::aquaticUpdateLavaReset);
  }

  @Override
  public float height() {
    return heightOverridden ? height : delegate.height();
  }

  @Override
  public void setHeight(float height) {
    heightOverridden = true;
    this.height = height;
    this.heightRounded = Math.round(height * 500d) / 1000d;
  }

  @Override
  public float width() {
    return widthOverridden ? width : delegate.width();
  }

  @Override
  public void setWidth(float width) {
    widthOverridden = true;
    this.width = width;
    this.widthRounded = Math.round(width * 500d) / 1000d;
  }

  @Override
  public double heightRounded() {
    return heightOverridden ? heightRounded : delegate.heightRounded();
  }

  @Override
  public double widthRounded() {
    return widthOverridden ? widthRounded : delegate.widthRounded();
  }

  @Override
  public float eyeHeight() {
    return delegate.eyeHeight();
  }

  @Override
  public Fluid interactingFluid() {
    return interactingFluidOverridden ? interactingFluid : delegate.interactingFluid();
  }

  @Override
  public void assumeOccurred(Simulation simulation) {
    applySimulation(simulation);
    defer(environment -> environment.assumeOccurred(simulation));
  }

  @Override
  public void tickComplete(boolean hasMovement, boolean hasRotation, boolean isRealClientTick) {
    activeTickOverride(ALIVE);
    tickOverride(IN_WEB, inWeb());
    tickOverride(IN_WATER, inWater());
    tickOverride(SNEAKING, isSneaking());
    tickOverride(SPRINTING, isSprinting());
    tickOverride(TELEPORT, false);
    inactiveTickOverride(
      STEP,
      ATTACK_REDUCE,
      IN_LAVA,
      VELOCITY,
      EDGE_SNEAKING,
      RECEIVED_VELOCITY_PACKET
    );
    currentTick = currentTick() + 1;
    currentTickOverridden = true;
    inactiveTickOverride(INVENTORY_OPEN);
    if (hasMovement || hasRotation) {
      inactiveTickOverride(MoveMetric.EXTERNAL_VELOCITY);
    }
    if (flyingDisablePending()) {
      setFlying(false);
      setFlyingDisablePending(false);
    }
    updatePose();
    defer(environment -> environment.tickComplete(hasMovement, hasRotation, true));
  }

  @Override
  public long currentTick() {
    if (currentTickOverridden) {
      return currentTick;
    }
    return delegate.currentTick();
  }

  @Override
  public long activeSequence() {
    if (activeSequenceOverridden) {
      return activeSequence;
    }
    return delegate.activeSequence();
  }

  @Override
  public void setActiveSequence(long activeSequence) {
    activeSequenceOverridden = true;
    this.activeSequence = activeSequence;
    defer(environment -> environment.setActiveSequence(activeSequence));
  }

  @Override
  public List<TickAmbiguousUpdate> allTickAmbiguousUpdates() {
    return delegate.allTickAmbiguousUpdates();
  }

  @Override
  public void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket) {
    defer(environment ->
      environment.setTreatThisFlyPacketAsMovePacket(treatThisFlyPacketAsMovePacket)
    );
  }

  private SimulationEnvironment unmodifiableView;

  @Override
  public SimulationEnvironment immutableView() {
    if (unmodifiableView == null) {
      unmodifiableView = ImmutableSimulationEnvironmentView.of(this);
    }
    return unmodifiableView;
  }

  @Override
  public int depth() {
    return delegate.depth() + 1;
  }

  private void applySimulation(Simulation simulation) {
    MovementConfiguration configuration = simulation.configuration();
    SimulationResult collider = simulation.result();
    onGroundOverridden = true;
    onGround = collider.onGround();
    collidedHorizontallyOverridden = true;
    collidedHorizontally = collider.collidedHorizontally();
    collidedVerticallyOverridden = true;
    collidedVertically = collider.collidedVertically();
    motionXResetOverridden = true;
    motionXReset = collider.resetMotionX();
    motionZResetOverridden = true;
    motionZReset = collider.resetMotionZ();
    hasJumpedInTickOverridden = true;
    hasJumpedInTick = configuration.isJumping();

    if (collider.step()) {
      activeTickOverride(STEP);
    }
    if (collider.edgeSneak()) {
      activeTickOverride(EDGE_SNEAKING);
    }
    setLastMovementConfiguration(configuration);
    setLastSimulationResult(collider);
  }

  private void tickOverride(MoveMetric metric, boolean active) {
    if (active) {
      activeTickOverride(metric);
    } else {
      inactiveTickOverride(metric);
    }
  }

  private void activeTickOverride(MoveMetric metric) {
    putActiveTrackerOverride(metric, ticks(metric) + 1);
    putPastTrackerOverride(metric, 0);
  }

  private void inactiveTickOverride(MoveMetric metric) {
    putActiveTrackerOverride(metric, 0);
    putPastTrackerOverride(metric, ticksPast(metric) + 1);
  }

  private void inactiveTickOverride(MoveMetric first, MoveMetric... others) {
    inactiveTickOverride(first);
    for (MoveMetric other : others) {
      inactiveTickOverride(other);
    }
  }

  private boolean hasMetricOverride(MoveMetric metric) {
    return activeTrackerOverrides != null && activeTrackerOverrides.containsKey(metric)
      || pastTrackerOverrides != null && pastTrackerOverrides.containsKey(metric);
  }

  private void putActiveTrackerOverride(MoveMetric metric, int value) {
    if (activeTrackerOverrides == null) {
      activeTrackerOverrides = new EnumMap<>(MoveMetric.class);
    }
    activeTrackerOverrides.put(metric, value);
  }

  private void putPastTrackerOverride(MoveMetric metric, int value) {
    if (pastTrackerOverrides == null) {
      pastTrackerOverrides = new EnumMap<>(MoveMetric.class);
    }
    pastTrackerOverrides.put(metric, value);
  }

  private void setPositionOverride(double positionX, double positionY, double positionZ) {
    positionOverridden = true;
    this.positionX = positionX;
    this.positionY = positionY;
    this.positionZ = positionZ;
  }

  private void setLastPositionOverride(double lastPositionX, double lastPositionY, double lastPositionZ) {
    lastPositionOverridden = true;
    this.lastPositionX = lastPositionX;
    this.lastPositionY = lastPositionY;
    this.lastPositionZ = lastPositionZ;
  }

  private void setMotionOverride(double motionX, double motionY, double motionZ) {
    motionOverridden = true;
    this.motionX = motionX;
    this.motionY = motionY;
    this.motionZ = motionZ;
  }

  private void setRotationOverride(float rotationYaw, float rotationPitch) {
    rotationOverridden = true;
    this.rotationYaw = rotationYaw;
    this.rotationPitch = rotationPitch;
    float rotationYawInRadians = rotationYaw * (float) Math.PI / 180.0F;
    yawSine = sin(rotationYawInRadians);
    yawCosine = cos(rotationYawInRadians);
    lookVector = vectorForRotation(rotationYaw, rotationPitch);
  }

  private void setLastRotationOverride(float lastRotationYaw, float lastRotationPitch) {
    lastRotationOverridden = true;
    this.lastRotationYaw = lastRotationYaw;
    this.lastRotationPitch = lastRotationPitch;
  }

  private void setLastSimulationResult(SimulationResult result) {
    simulationResultOverridden = true;
    simulationResult = result;
  }

  private Vector vectorForRotation(float yaw, float pitch) {
    float f = pitch * ((float) Math.PI / 180F);
    float f1 = -yaw * ((float) Math.PI / 180F);
    float f2 = cos(f1);
    float f3 = sin(f1);
    float f4 = cos(f);
    float f5 = sin(f);
    return new Vector(f3 * f4, -f5, (double) (f2 * f4));
  }

  @Override
  public void commitTo(SimulationEnvironment other) {
    if (other == this) {
      return;
    }
    if (delegate != other && delegate.depth() > 0) {
      delegate.commitTo(other);
    }
    if (deferredMutations != null) {
      for (EnvironmentMutation deferredMutation : deferredMutations) {
        deferredMutation.apply(other);
      }
    }
    if (verifiedLastPositionOverridden) {
      other.setVerifiedLastPosition(
        new Position(verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ),
        verifiedLastPositionReason
      );
    }
    if (lastPositionOverridden) {
      other.setLastPosition(lastPositionX, lastPositionY, lastPositionZ);
    }
    if (lastOnGroundOverridden) {
      other.setLastOnGround(lastOnGround);
    }
    if (boundingBoxOverridden) {
      other.setBoundingBox(boundingBox);
    }
    if (baseMotionOverridden) {
      other.setBaseMotion(baseMotionX, baseMotionY, baseMotionZ);
    }
    if (motionMultiplierOverridden) {
      if (motionMultiplier == null) {
        other.resetMotionMultiplier();
      } else {
        other.setMotionMultiplier(motionMultiplier.clone());
      }
    }
    if (jumpMotionOverridden) {
      other.setJumpMotion(jumpMotion);
    }
    if (inWaterOverridden) {
      other.setInWater(inWater);
    }
    if (inLavaOverridden) {
      other.setInLava(inLava);
    }
    if (lavaDepthOverridden) {
      other.setLavaDepth(lavaDepth);
    }
    if (inWebOverridden) {
      other.setInWeb(inWeb);
    }
    if (fallDistanceOverridden && fallDistance == 0.0) {
      other.resetFallDistance();
    }
    if (pushedByEntityOverridden) {
      other.setPushedByEntity(pushedByEntity);
    }
    if (simulationResultOverridden) {
      other.setSimulationResult(simulationResult);
    }
    if (motionXResetOverridden) {
      other.setMotionResetX(motionXReset);
    }
    if (motionZResetOverridden) {
      other.setMotionResetZ(motionZReset);
    }
    if (enforceBoatStepOverridden) {
      other.setEnforceBoatStep(enforceBoatStep);
    }
    if (physicsPacketRelinkFlyVLOverridden) {
      other.setPhysicsPacketRelinkFlyVL(physicsPacketRelinkFlyVL);
    }
    if (worldBorderOverridden) {
      other.setWorldBorder(worldBorder);
    }
    if (simulatorOverridden) {
      other.setSimulator(simulator);
    }
    if (stepHeightOverridden) {
      other.setStepHeight(stepHeight);
    }
  }

  public static MutableSimulationEnvironmentView of(SimulationEnvironment delegate) {
    return new MutableSimulationEnvironmentView(delegate);
  }

  private interface EnvironmentMutation {
    void apply(SimulationEnvironment environment);
  }
}
