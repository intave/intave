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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator.Status;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;

public interface SimulationEnvironment {
  User user();

  default Position position() {
    return new Position(positionX(), positionY(), positionZ());
  }
  double positionX();
  double positionY();
  double positionZ();

  default BlockPosition blockPosition() {
    return new BlockPosition(floor(positionX()), floor(positionY()), floor(positionZ()));
  }

  Vector lookVector();

  default Position lastPosition() {
    return new Position(lastPositionX(), lastPositionY(), lastPositionZ());
  }
  double lastPositionX();
  double lastPositionY();
  double lastPositionZ();

  default Rotation lastRotation() {
    return new Rotation(lastRotationYaw(), lastRotationPitch());
  }
  float lastRotationYaw();
  float lastRotationPitch();

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
      hasMovement, hasRotation
    );
  }

  void setPosition(double x, double y, double z);

  default void setPosition(@Nullable Position position) {
    if (position != null) {
      setPosition(position.getX(), position.getY(), position.getZ());
    }
  }

  void setRotation(float newRotationYaw, float newRotationPitch);

  default void setRotation(@Nullable Rotation newRotation) {
    if (newRotation != null) {
      setRotation(newRotation.yaw(), newRotation.pitch());
    }
  }
  default Position verifiedLastPosition() {
    return new Position(verifiedLastPositionX(), verifiedLastPositionY(), verifiedLastPositionZ());
  }
  double verifiedLastPositionX();
  double verifiedLastPositionY();

  double verifiedLastPositionZ();

  void setVerifiedLastPosition(Position position, String reason);

  default void setLastRotation(Rotation rotation) {
    setLastRotation(rotation.yaw(), rotation.pitch());
  }

  void setLastRotation(float lastRotationYaw, float lastRotationPitch);

  default void setLastPosition(Position position) {
    setLastPosition(position.getX(), position.getY(), position.getZ());
  }

  void setLastPosition(double x, double y, double z);

  void setBoundingBox(BoundingBox boundingBox);
  BoundingBox boundingBox();

  Pose pose();
  void setPose(Pose pose);

  default void updatePose() {
    User user = user();
    Pose suggestedPose;
    if (shouldHaveSwimmingPose()) {
      suggestedPose = Pose.SWIMMING;
    } else if (shouldBeInSleepingPose()) {
      suggestedPose = Pose.SLEEPING;
    } else if (shouldHaveFallFlyingPose()) {
      suggestedPose = Pose.FALL_FLYING;
    } else if (shouldHaveSneakingPose()) {
      suggestedPose = Pose.CROUCHING;
    } else {
      suggestedPose = Pose.STANDING;
    }
    Pose actualPose;
    if (user.protocolVersion() >= VER_1_14) {
      if (!isPoseClear(Pose.SWIMMING)) {
        return;
      }
      if (!isPoseClear(suggestedPose)) {
        if (isPoseClear(Pose.CROUCHING)) {
          actualPose = Pose.CROUCHING;
        } else {
          actualPose = Pose.SWIMMING;
        }
      } else {
        actualPose = suggestedPose;
      }
    } else {
      actualPose = suggestedPose;
    }
    setPose(actualPose);
  }

  default boolean isPoseClear(Pose pose) {
    User user = user();
    return Collision.nonePresent(
      user, this, pose.boundingBoxOf(user, this).shrink(0.0000001)
    );
  }

  default Motion sentOffsetMotion() {
    return new Motion(offsetMotionX(), offsetMotionY(), offsetMotionZ());
  }
  double offsetMotionX();
  double offsetMotionY();
  double offsetMotionZ();

  List<Motion> postTickMotionCandidates();
  void setPostTickMotionCandidates(@NotNull List<Motion> postTickMotionCandidates);

  default void clearPostTickMotionCandidates() {
    setPostTickMotionCandidates(Collections.emptyList());
  }

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
  void setMotionMultiplier(Vector motionMultiplier);
  void resetMotionMultiplier();

  WorldBorder border();
  void setWorldBorder(@NotNull WorldBorder worldBorder);

  default Rotation rotation() {
    return new Rotation(rotationYaw(), rotationPitch());
  }

  float rotationYaw();
  float yawSine();
  float yawCosine();

  float rotationPitch();

  float aiMoveSpeed(boolean sprinting);

  boolean shouldHaveFallFlyingPose();

  float friction(boolean sprinting);
  double stepHeight();
  void setStepHeight(float stepHeight);
  double resetMotion();

  double jumpMotion();
  void setJumpMotion(double jumpMotion);
  boolean isJumping();

  double gravity();

  // Entity.getBlockSpeedFactor @ 1.19
  default float blockSpeedFactor() {
    User user = user();
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.trailsAndTailsUpdate()) {
      Material material = VolatileBlockAccess.typeAccess(user, blockPosition());
      float f = BlockProperties.speedFactorOf(material);

      if (!MaterialMagic.isWater(material) && material != BlockTypeAccess.BUBBLE_COLUMN) {
        return f == 1.0 ? BlockProperties.speedFactorOf(frictionMaterial()) : f;
      } else {
        return f;
      }
    } else {
      return BlockProperties.speedFactorOf(frictionMaterial());
    }
  }

  float jumpMovementFactor();

  boolean isSprinting();
  boolean isSneaking();
  void setSneaking(boolean sneaking);
  boolean lastSprinting();
  void setLastSprinting(boolean lastSprinting);

  boolean isSleeping();
  void setSleeping(boolean sleeping);

  boolean hasSprintSpeed();
  boolean sprintingAllowed();
  boolean inWater();
  void setInWater(boolean inWater);
  boolean inLava();
  boolean inWeb();
  void resetInWeb();
  boolean onGround();

  boolean lastOnGround();
  void setLastOnGround(boolean lastOnGround);
  boolean collidedHorizontally();
  boolean collidedVertically();

  // don't override
  default void checkSupportingBlock(boolean onGround, Motion motion) {
    User user = user();
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.trailsAndTailsUpdate()) {
      if (onGround) {
        BoundingBox boundingBox = BoundingBox.fromPosition(user, this, positionX(), positionY(), positionZ());
        BoundingBox testArea = new BoundingBox(
          boundingBox.minX, boundingBox.minY - 0.000001, boundingBox.minZ,
          boundingBox.maxX, boundingBox.minY, boundingBox.maxZ
        );
        BlockPosition supportingBlock = findSupportingBlock(user, testArea);
        BlockPosition result;
        if (supportingBlock != null || onGroundNoBlocks()) {
          result = supportingBlock;
	      } else {
	        BoundingBox thirdBoundingBox = testArea.move(-motion.motionX, 0.0, -motion.motionZ);
          supportingBlock = findSupportingBlock(user, thirdBoundingBox);
          result = supportingBlock;
	      }
	      setMainSupportingBlockPos(result);
        setOnGroundNoBlocks(supportingBlock == null);
      } else {
        setMainSupportingBlockPos(null);
        setOnGroundNoBlocks(false);
      }
    }
  }

  BlockPosition mainSupportingBlockPos();
  void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos);
  boolean onGroundNoBlocks();
  void setOnGroundNoBlocks(boolean onGroundNoBlocks);

  // do not override
  default void compileSpecialBlocks() {
    setPreviousCollideMaterial(collideMaterial());
    setPreviousFrictionMaterial(frictionMaterial());
    setCollideMaterial(compileCollideBlock());
    setFrictionMaterial(compileFrictionBlock());
  }

  // do not override
  default Material compileCollideBlock() {
    return compileBlockBelow(0.2f);
  }

  // do not override
  default Material compileFrictionBlock() {
    return compileBlockBelow(frictionPosSubtraction());
  }

  // formally Entity#getOnPos
  // do not override
  default Material compileBlockBelow(double reduction) {
    User user = user();
    int blockCollisionPosX = floor(positionX());
    int blockCollisionPosY = floor(positionY() - reduction);
    int blockCollisionPosZ = floor(positionZ());
    BlockPosition mainSupportingBlockPos = mainSupportingBlockPos();
    if (mainSupportingBlockPos != null) {
      // 1.20
      Material blockType = VolatileBlockAccess.typeAccess(
        user, mainSupportingBlockPos
      );
      if (reduction > 0.00001f) {
        String typeName = blockType.name();
        if (reduction <= 0.5D && typeName.contains("FENCE")) {
          return blockType;
        }
        if (typeName.contains("FENCE") || typeName.contains("WALL")) {
          return blockType;
        }
        return VolatileBlockAccess.typeAccess(
          user,
          mainSupportingBlockPos.getBlockX(),
          blockCollisionPosY,
          mainSupportingBlockPos.getBlockZ()
        );
      } else {
        return blockType;
      }
    } else {
      // 1.8 - 1.19
      Material blockType = VolatileBlockAccess.typeAccess(
        user, positionX(), positionY() - reduction, positionZ()
      );
      ProtocolMetadata clientData = user.meta().protocol();
      if (blockType == Material.AIR && !clientData.trailsAndTailsUpdate()) {
        Material blockBelow = VolatileBlockAccess.typeAccess(user, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);
        if (blockBelow.name().contains("FENCE") || blockBelow.name().contains("WALL")) {
          blockType = blockBelow;
        }
      }
      return blockType;
    }
  }

  boolean collidedWithBoat();
  double frictionPosSubtraction();

  float frictionMultiplier();
  boolean receivedFlyingPacketIn(int ticks);

  Material collideMaterial();
  Material frictionMaterial();
  Material previousCollideMaterial();
  Material previousFrictionMaterial();

  void setCollideMaterial(Material collideMaterial);
  void setFrictionMaterial(Material frictionMaterial);
  void setPreviousCollideMaterial(Material previousCollideMaterial);
  void setPreviousFrictionMaterial(Material previousFrictionMaterial);

  boolean blockOnPositionSoulSpeedAffected();

  double fallDistance();
  void resetFallDistance();
  void addFallDistance(double fallDistance);

  boolean isInVehicle();

  void dismountRidingEntity(String boatSetback);

  default Entity vehicle() {
    return null;
  }

  Simulator simulator();
  void setSimulator(Simulator simulator);

  void setPushedByEntity(boolean pushedByEntity);
  boolean pushedByEntity();

  void setSimulationResult(SimulationResult result);
  SimulationResult simulationResult();

  void setLastMovementConfiguration(MovementConfiguration configuration);
  MovementConfiguration lastMovementConfiguration();

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

  @Deprecated
  int reduceTicks();

  @Deprecated
  boolean denyJump();

  void setEyesInWater(boolean eyesInWater);
  boolean areEyesInWater();
  void setInteractingFluid(Fluid interactingFluid);
  Fluid interactingFluid();

  default void updateEyesInWater() {
    double yPos = positionY() + eyeHeight() - (double) 0.11111f;
    this.setEyesInWater(interactingFluid() != null && interactingFluid().isOfWater());
    this.setInteractingFluid(null);

    Fluid fluid = VolatileBlockAccess.fluidAccess(user(), positionX(), yPos, positionZ());
    if (fluid.isOfWater()) {
      double d1 = (float) floor(yPos) + 1.0f;
      if (d1 > yPos) {
        setInteractingFluid(fluid);
      }
    }
  }

  void resetPhysicsPacketRelinkFlyVL();

  default int physicsPacketRelinkFlyVL() {
    return 0;
  }

  default void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
    throw new UnsupportedOperationException("setPhysicsPacketRelinkFlyVL is not supported for this SimulationEnvironment");
  }

  default boolean motionResetX() {
    return motionXReset();
  }

  default void setMotionResetX(boolean reset) {
    throw new UnsupportedOperationException("setMotionResetX is not supported for this SimulationEnvironment");
  }

  default boolean motionResetZ() {
    return motionZReset();
  }

  default void setMotionResetZ(boolean reset) {
    throw new UnsupportedOperationException("setMotionResetZ is not supported for this SimulationEnvironment");
  }

  default double baseMoveSpeed() {
    return 0.271;
  }

  default int fireworkRocketsPower() {
    return 1;
  }

  default int shulkerXToleranceRemaining() {
    return 0;
  }

  default int shulkerYToleranceRemaining() {
    return 0;
  }

  default int shulkerZToleranceRemaining() {
    return 0;
  }

  default int lowestShulkerY() {
    return Integer.MAX_VALUE;
  }

  default int highestShulkerY() {
    return Integer.MIN_VALUE;
  }

  default int pistonMotionToleranceRemaining() {
    return 0;
  }

  default double pistonVerticalAllowance() {
    return 0.0;
  }

  default double pistonHorizontalAllowance() {
    return 0.0;
  }

  default BoundingBox pistonCollisionArea() {
    return null;
  }

  default boolean physicsUnpredictableVelocityExpected() {
    return false;
  }

  default boolean enforceBoatStep() {
    return false;
  }

  default void setEnforceBoatStep(boolean enforceBoatStep) {
    throw new UnsupportedOperationException("setEnforceBoatStep is not supported for this SimulationEnvironment");
  }

  Status boatStatus();
  void setBoatStatus(Status boatStatus);

  Status previousBoatStatus();
  void setPreviousBoatStatus(Status previousBoatStatus);

  float boatGlide();
  void setBoatGlide(float boatGlide);

  double boatWaterLevel();
  void setBoatWaterLevel(double boatWaterLevel);

  default boolean lastSneaking() {
    return false;
  }

  default boolean currentlyInBlock() {
    return false;
  }

  default int highestLocalRiptideLevel() {
    return 0;
  }

  default boolean onGroundWithRiptide() {
    return false;
  }

  void aquaticUpdateLavaReset();

  float height();
  void setHeight(float height);
  double heightRounded();

  float width();
  void setWidth(float width);
  double widthRounded();

  default void updateSize() {
    User user = user();
    Pose pose = pose();
    setWidth(pose.width(user, this));
    setHeight(pose.height(user, this));
  }

  float eyeHeight();

  void assumeOccurred(Simulation simulation);
  void tickComplete(boolean hasMovement, boolean hasRotation, boolean isRealClientTick);

  long currentTick();
  long activeSequence();
  void setActiveSequence(long activeSequence);

  List<TickAmbiguousUpdate> allTickAmbiguousUpdates();

  default List<TickAmbiguousUpdate> possibleTickAmbiguousUpdates() {
    List<TickAmbiguousUpdate> updates = new ArrayList<>();
    for (TickAmbiguousUpdate update : allTickAmbiguousUpdates()) {
      if (update.possible(this)) {
        updates.add(update);
      }
    }
    return updates;
  }

  void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket);

  default boolean tryMoveReinterpretation(Simulation simulation, double flyingLimit) {
    if (!simulation.resultsInFlyingPacket(this, flyingLimit)) {
      return false;
    }
    reinterpretMovePacket(simulation);
    return true;
  }

  default void reinterpretMovePacket(Simulation simulation) {
    Position verifiedLastPosition = verifiedLastPosition();
    Rotation lastRotation = lastRotation();
    Position subversivePosition = verifiedLastPosition.add(simulation.offsetMotion());

    updateMovement(subversivePosition, null);
    setLastPosition(verifiedLastPosition);
    setLastRotation(lastRotation);
    activeTick(MoveMetric.FLYING_PACKET_ACCURATE);
    setTreatThisFlyPacketAsMovePacket(true);
  }

  default boolean shouldBeInSleepingPose() {
    return isSleeping();
  }

  // isSwimming()
  default boolean shouldHaveSwimmingPose() {
    ProtocolMetadata protocol = user().meta().protocol();
    if (!protocol.swimmingMechanics()) {
      return false;
    }
    boolean sprinting = lastSprinting();
    boolean swimming = pose() == Pose.SWIMMING;
    if (swimming) {
      return sprinting && inWater();
    } else {
      return sprinting && ((pose() == Pose.FALL_FLYING && inWater()) || areEyesInWater());
    }
  }

  default boolean shouldHaveSneakingPose() {
    MetadataBundle meta = user().meta();
    ProtocolMetadata protocol = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
    boolean sneakingAllowed = isSneaking() && !inventoryData.inventoryOpen();
    boolean actualSneaking;
    if (protocol.delayedSneak()) {
      actualSneaking = lastSneaking();
    } else if (protocol.alternativeSneak()) {
      actualSneaking = lastSneaking() || sneakingAllowed;
    } else {
      actualSneaking = sneakingAllowed;
    }
    return actualSneaking;
  }

  @Nullable
  default BlockPosition findSupportingBlock(
    User user, BoundingBox box
  ) {
    BlockPosition block = null;
    int blockX = 0, blockY = 0, blockZ = 0;
    double distance = Double.MAX_VALUE;

    int startX = ClientMath.floor(box.minX - 0.0000001) - 1;
    int endX = ClientMath.floor(box.maxX + 0.0000001) + 1;
    int startY = ClientMath.floor(box.minY - 0.0000001) - 1;
    int endY = ClientMath.floor(box.maxY + 0.0000001) + 1;
    int startZ = ClientMath.floor(box.minZ - 0.0000001) - 1;
    int endZ = ClientMath.floor(box.maxZ + 0.0000001) + 1;

    double positionX = positionX();
    double positionY = positionY();
    double positionZ = positionZ();

    CubeIterator iterator = new CubeIterator(startX, startY, startZ, endX, endY, endZ);
    while (iterator.advance()) {
      int x = iterator.nextX();
      int y = iterator.nextY();
      int z = iterator.nextZ();
      int type = iterator.nextType();
      if (type == CubeIterator.TYPE_CORNER) {
        continue;
      }
      BlockShape shape = user.blockCache().collisionShapeAt(x, y, z);
      if (shape.isEmpty()) {
        continue;
      } else if (shape.isCubic() && !box.intersectsWith(BoundingBox.fromBounds(x, y, z, x + 1, y + 1, z + 1))) {
        continue;
      } else if (!shape.isCubic() && !shape.intersectsWith(box)) {
        continue;
      }
      double distanceToCenter = distanceToCenter(x, y, z, positionX, positionY, positionZ);
      int comparison = compare(x, y, z, blockX, blockY, blockZ);
      if (distanceToCenter < distance || (distanceToCenter == distance && comparison < 0)) {
        blockX = x;
        blockY = y;
        blockZ = z;
        block = BlockPosition.of(blockX, blockY, blockZ);
        distance = distanceToCenter;
      }
    }
    return block;
  }

  static int compare(
    int alphaX, int alphaY, int alphaZ,
    int betaX, int betaY, int betaZ
  ) {
    if (alphaY == betaY) {
      return alphaZ == betaZ ? alphaX - betaX : alphaZ - betaZ;
    } else {
      return alphaY - betaY;
    }
  }

  static double distanceToCenter(
    int blockX, int blockY, int blockZ,
    double entityX, double entityY, double entityZ
  ) {
    double d0 = blockX + 0.5 - entityX;
    double d1 = blockY + 0.5 - entityY;
    double d2 = blockZ + 0.5 - entityZ;
    return d0 * d0 + d1 * d1 + d2 * d2;
  }

  default SimulationEnvironment immutableView() {
    return ImmutableSimulationEnvironmentView.of(this);
  }

  default SimulationEnvironment immutableCopy() {
    return ImmutableSimulationEnvironmentCopy.of(this);
  }

  default SimulationEnvironment mutableView() {
    return MutableSimulationEnvironmentView.of(this);
  }

  default void commitTo(SimulationEnvironment other) {
    throw new UnsupportedOperationException("commitTo is not supported for this SimulationEnvironment");
  }

  default int depth() {
    return 0;
  }

  static SimulationEnvironment invalid() {
    return null;
  }
}
