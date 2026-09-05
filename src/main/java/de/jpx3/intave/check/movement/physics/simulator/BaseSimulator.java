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

package de.jpx3.intave.check.movement.physics.simulator;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.inside.BlockInsideChecks;
import de.jpx3.intave.block.inside.EntityMovement;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.EXTERNAL_VELOCITY;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.IN_LAVA;
import static de.jpx3.intave.share.ClientMath.clamp_double;
import static de.jpx3.intave.share.ClientMath.floor;

class BaseSimulator extends Simulator {
  @Override
  public Motion simulatePreTick(
    User user, Motion baseMotion,
    SimulationEnvironment environment
  ) {
    baseMotion = baseMotion.copy();
    updateAquatics(user, baseMotion, environment, false);
    moveOutOfBlocks(user, baseMotion, environment);
    handleSneakInWater(user, baseMotion, environment);
    simulateMotionClamp(user, baseMotion, environment);
    return baseMotion;
  }

  private void moveOutOfBlocks(User user, Motion motion, SimulationEnvironment environment) {
    BoundingBox boundingBox = environment.boundingBox();
    Position position = environment.lastPosition();
    double scaledWidth = boundingBox.width() * 0.35d;
    double positionY = boundingBox.minY + 0.5;
    Position positionA = Position.of(position.getX() - scaledWidth, positionY, position.getZ() + scaledWidth);
    Position positionB = Position.of(position.getX() - scaledWidth, positionY, position.getZ() - scaledWidth);
    Position positionC = Position.of(position.getX() + scaledWidth, positionY, position.getZ() - scaledWidth);
    Position positionD = Position.of(position.getX() + scaledWidth, positionY, position.getZ() + scaledWidth);
    pushOutOfBlocks(user, motion, environment, positionA);
    pushOutOfBlocks(user, motion, environment, positionB);
    pushOutOfBlocks(user, motion, environment, positionC);
    pushOutOfBlocks(user, motion, environment, positionD);
  }

  private void pushOutOfBlocks(User user, Motion motion, SimulationEnvironment environment, Position position) {
    BlockPosition blockPosition = position.toBlockPosition();
    if (suffocates(user, environment, blockPosition)) {
      double d0 = position.getX() - blockPosition.getX();
      double d1 = position.getZ() - blockPosition.getZ();
      Direction direction = null;
      double minPush = 9999.0;
      if (!suffocates(user, environment, blockPosition.west()) && d0 < minPush) {
        minPush = d0;
        direction = Direction.WEST;
      }
      if (!suffocates(user, environment, blockPosition.east()) && 1.0 - d0 < minPush) {
        minPush = 1.0 - d0;
        direction = Direction.EAST;
      }
      if (!suffocates(user, environment, blockPosition.north()) && d1 < minPush) {
        minPush = d1;
        direction = Direction.NORTH;
      }
      if (!suffocates(user, environment, blockPosition.south()) && 1.0 - d1 < minPush) {
	      minPush = 1.0 - d1;
	      direction = Direction.SOUTH;
      }
      if (direction != null) {
        switch (direction) {
          case WEST:
            motion.motionX = -0.1;
            break;
          case EAST:
            motion.motionX = 0.1;
            break;
          case NORTH:
            motion.motionZ = -0.1;
            break;
          case SOUTH:
            motion.motionZ = 0.1;
            break;
        }
      }
    }
  }

  private boolean suffocates(User user, SimulationEnvironment environment, BlockPosition position) {
    BoundingBox boundingBox = environment.boundingBox();
    MutableBlockPosition mutable = position.mutable();
    for (int y = (int) Math.floor(boundingBox.minY); y < Math.ceil(boundingBox.maxY); y++) {
      mutable.setY(y);
      Material type = VolatileBlockAccess.typeAccess(user, mutable);
      BlockShape collisionShape = VolatileBlockAccess.collisionShapeAccess(user, mutable);
      if (BlockProperties.of(type).suffocates() && collisionShape.isCubic()) {
        return true;
      }
    }
    return false;
  }

  private void handleSneakInWater(User user, Motion motion, SimulationEnvironment environment) {
    ProtocolMetadata protocol = user.meta().protocol();
    boolean affectedByFluids = protocol.protocolVersion() < ProtocolMetadata.VER_1_17 || !user.meta().abilities().flying();
    boolean sneakingAndInWater = environment.isSneaking() && environment.inWater();
    if (protocol.aquaticUpdate() && affectedByFluids && sneakingAndInWater) {
      motion.motionY -= 0.04F;
    }
  }

  private void updateAquatics(
    User user, Motion baseMotion,
    SimulationEnvironment environment,
    boolean afterMove
  ) {
    updateInWater(user, baseMotion, environment);
    updateInLava(user, baseMotion, environment, afterMove);
    if (!afterMove) {
      environment.updateEyesInWater();
    } else if (user.meta().protocol().refreshesFluidStateAfterMove()) {
      environment.updateEyesInWaterAfterMove();
    }
  }

  private void updateInWater(User user, Motion baseMotion, SimulationEnvironment environment) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    BoundingBox boundingBox = environment.boundingBox();
    if (!clientData.aquaticUpdate()) {
      boundingBox = boundingBox.grow(0.0D, -0.4000000059604645D, 0.0D);
      boundingBox = boundingBox.shrink(0.001D);
    }
    environment.setInWater(user.fluidflow().applyWaterFlowTo(user, environment, baseMotion, boundingBox));
  }

  private void updateInLava(
    User user, Motion baseMotion,
    SimulationEnvironment environment,
    boolean afterMove
  ) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.fluidHeightBasedLavaMovement()
      && (!afterMove || protocol.refreshesFluidStateAfterMove())) {
      environment.aquaticUpdateLavaReset();
      user.fluidflow().applyLavaFlowTo(
        user, environment, baseMotion, environment.boundingBox()
      );
    }
    if (environment.inLava()) {
      environment.activeTick(IN_LAVA);
    }
  }

  private void simulateMotionClamp(
    User user, Motion baseMotion,
    SimulationEnvironment environment
  ) {
    double resetMotion = environment.resetMotion();

    if (user.meta().protocol().newMotionClampLogic()) {
      if (baseMotion.horizontalLengthSqr() < 0.000009) {
        baseMotion.motionX = 0;
        baseMotion.motionZ = 0;
      }
    } else {
      if (Math.abs(baseMotion.motionX) < resetMotion) {
        baseMotion.motionX = 0.0;
//        user.sendMessage("Motion X "+(baseMotion.motionX)+" clamped to 0.0 due to reset motion threshold: " + resetMotion);
      }
      if (Math.abs(baseMotion.motionZ) < resetMotion) {
        baseMotion.motionZ = 0.0;
//        user.sendMessage("Motion Z "+(baseMotion.motionZ)+" clamped to 0.0 due to reset motion threshold: " + resetMotion);
      }
    }

    if (Math.abs(baseMotion.motionY) < resetMotion) {
      baseMotion.motionY = 0.0;
    }
  }

  protected final void simulateJump(
    User user,
    Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    if (!configuration.isJumping()) {
      return;
    }

    ProtocolMetadata protocol = user.meta().protocol();
    boolean inWater = environment.inWater();
    boolean allowJumpInLiquid = false;
    if (
      protocol.aquaticUpdate() && inWater &&
      environment.onGround() && environment.pose().height(user, environment) >= 0.4
    ) {
      Position lastPosition = environment.lastPosition();
      double fluidDepth = user.fluidflow().fluidDepthAt(
        user, BoundingBox.fromPosition(user, environment, lastPosition)
      );
      allowJumpInLiquid = fluidDepth <= 0.4;
    }
    boolean allowGroundJumpInLava = false;
    if (
      protocol.fluidHeightBasedLavaMovement()
        && environment.inLava()
        && environment.lastOnGround()
    ) {
      double fluidJumpThreshold = environment.eyeHeight() < 0.4D ? 0.0D : 0.4D;
      allowGroundJumpInLava = !(environment.lavaDepth() > fluidJumpThreshold);
    }
    if (inWater && !allowJumpInLiquid) {
      motion.motionY += 0.04F;
    } else if (environment.inLava() && !allowGroundJumpInLava) {
      // #handleJumpLava
      motion.motionY += 0.04F;
    } else if (environment.lastOnGround()) {
      motion.motionY = user.protocolVersion() >= 768 ?
        Math.max(environment.jumpMotion(), environment.baseMotionY()) :
        environment.jumpMotion();
      if (configuration.isSprinting()) {
        motion.motionX -= environment.yawSine() * 0.2F;
        motion.motionZ += environment.yawCosine() * 0.2F;
      }
    }
  }

  @Override
  public Simulation simulateTick(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    Timings.CHECK_PHYSICS_SIMULATOR.start();
    Timings.CHECK_PHYSICS_SIMULATOR_BASE.start();
    // guessed movement configuration
    float forward = configuration.forward() * 0.98f;
    float strafe = configuration.strafe() * 0.98f;
    boolean handActive = configuration.isHandActive();
    boolean jumped = configuration.isJumping();
    boolean sprinting = configuration.isSprinting();
    int reduceTicks = configuration.reduceTicks();
    boolean reduceBefore = configuration.reduceBefore();

    // static movement configuration
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
    Pose pose = environment.pose();

    float yawSine = environment.yawSine();
    float yawCosine = environment.yawCosine();
    double positionX = environment.verifiedLastPositionX();
    double positionY = environment.verifiedLastPositionY();
    double positionZ = environment.verifiedLastPositionZ();
    boolean inWater = environment.inWater();
    boolean inLava = environment.inLava();
    boolean swimming = environment.isSwimming();
    boolean crouching;
    if (protocol.beeUpdate()) {
      crouching = !meta.abilities().flying()
        && !swimming
        && !environment.isInVehicle()
        && environment.isPoseClear(Pose.CROUCHING)
        && (environment.lastSneaking()
          || !environment.shouldBeInSleepingPose()
          && !environment.isPoseClear(Pose.STANDING));
    } else if (protocol.alternativeSneak()) {
      crouching = pose == Pose.CROUCHING || environment.isSneaking();
    } else {
      crouching = environment.isSneaking();
    }
    crouching = environment.resolveCrouchingInputSlowdown(crouching);
    boolean visuallyCrawling = protocol.applyModernCollider()
      && !inWater
      && (pose == Pose.SWIMMING
        || (!environment.shouldHaveFallFlyingPose() && pose == Pose.FALL_FLYING));
    boolean waterUpdate = protocol.aquaticUpdate();

    motion = motion.copy();

    if (crouching || visuallyCrawling) {
      double sneakingSpeed = user.meta().abilities().attributeValue("player.sneaking_speed");
      if (Double.isNaN(sneakingSpeed)) {
        sneakingSpeed = 0.3 + Enchantments.resolveSwiftSpeedModifier(user.player()) * 0.15f;
      }
      double sneakingModifier = clamp_double(sneakingSpeed, 0.0f, 1.0f);
      forward = (float) ((double) forward * sneakingModifier);
      strafe = (float) ((double) strafe * sneakingModifier);
    }
    if (handActive) {
      forward *= 0.2f;
      strafe *= 0.2f;
    }

    if (reduceBefore) {
      for (int i = 0; i < reduceTicks; i++) {
        motion.motionX *= 0.6;
        motion.motionZ *= 0.6;
      }
      if (reduceTicks > 0) {
        // perform motion clamping (reducing inaccuracy prefetched)
        double resetMotion = environment.resetMotion();
        if (Math.abs(motion.motionX) < resetMotion) {
          motion.motionX = 0.0;
        }
        if (Math.abs(motion.motionY) < resetMotion) {
          motion.motionY = 0.0;
        }
        if (Math.abs(motion.motionZ) < resetMotion) {
          motion.motionZ = 0.0;
        }
      }
    }

    simulateJump(user, motion, environment, configuration);
    if (waterUpdate && swimming) {
      double d3 = environment.lookVector().getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
      // please verify if this is not actually the last position
      boolean liquidPresent = Fluids.fluidPresentAt(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || liquidPresent) {
        motion.motionY += (d3 - motion.motionY) * d4;
      }
    }
    if (inWater) {
      performSimulationInWaterOfState(user, motion, environment, sprinting, forward, strafe, yawSine, yawCosine);
    } else if (inLava) {
      performLavaSimulationOfState(motion, forward, strafe, yawSine, yawCosine);
    } else {
      performDefaultMoveSimulationOfState(user, motion, environment, forward, strafe, yawSine, yawCosine, sprinting);
    }

    Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER.start();
    SimulationResult collisionResult = Colliders.collision(user, environment, motion, environment.inWeb(), positionX, positionY, positionZ);
    Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER.stop();
    Timings.CHECK_PHYSICS_SIMULATOR_BASE.stop();
    Timings.CHECK_PHYSICS_SIMULATOR.stop();
    return Simulation.of(user, configuration, environment, collisionResult);
  }

  private void performSimulationInWaterOfState(
    User user, Motion context,
    SimulationEnvironment environment,
    boolean sprinting,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float friction = interpolatedWaterFriction(
      user, environment, 0.02F, environment.aiMoveSpeed(sprinting)
    );
    performRelativeMoveSimulationOfState(
      context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performLavaSimulationOfState(
    Motion context,
    float moveForward,
    float moveStrafe,
    float yawSine,
    float yawCosine
  ) {
    float friction = 0.02f;
    performRelativeMoveSimulationOfState(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performDefaultMoveSimulationOfState(
    User user,
    Motion context,
    SimulationEnvironment environment,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine,
    boolean sprinting
  ) {
    performRelativeMoveSimulationOfState(context, environment.friction(sprinting), yawSine, yawCosine, moveForward, moveStrafe);

    boolean onClimbable = MovementCharacteristics.onClimbable(
      user,
      environment.verifiedLastPositionX(),
      environment.verifiedLastPositionY(),
      environment.verifiedLastPositionZ()
    );

    if (onClimbable) {
      float axisLimit = 0.15F;
      context.motionX = ClientMath.clamp_double(context.motionX, -axisLimit, axisLimit);
      context.motionZ = ClientMath.clamp_double(context.motionZ, -axisLimit, axisLimit);
      if (context.motionY < -axisLimit) {
        context.motionY = -axisLimit;
      }

      Material type = VolatileBlockAccess.typeAccess(
        user, user.player().getWorld(),
        floor(environment.verifiedLastPositionX()),
        floor(environment.verifiedLastPositionY()),
        floor(environment.verifiedLastPositionZ())
      );
      if (environment.isSneaking() && context.motionY < 0.0D && BlockProperties.of(type).climbableSneakLimit()) {
        context.motionY = 0.0D;
      }
    }
  }

  // moveRelative
  private void performRelativeMoveSimulationOfState(
    Motion motion,
    float friction,
    float yawSine,
    float yawCosine,
    float moveForward,
    float moveStrafe
  ) {
    float f = moveStrafe * moveStrafe + moveForward * moveForward;
    if (f >= 0.0001f) {
      f = (float) Math.sqrt(f);
      f = friction / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      motion.motionX += moveStrafe * yawCosine - moveForward * yawSine;
      motion.motionZ += moveForward * yawCosine + moveStrafe * yawSine;
    }
  }

  @Override
  public Motion simulateAfterTick(
    User user,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Position position, Motion motion
  ) {
    motion = motion.copy();
    SimulationResult result = environment.simulationResult();
    Motion actualMoveMotion = result == null ? null : result.actualMotion();
    double motionYBeforeMove = actualMoveMotion == null ? motion.motionY : actualMoveMotion.motionY;
    boolean fallingBeforeMove = motionYBeforeMove <= 0.0D;
//    SimulationResult beforeMoveCollider = environment.result();
//    Motion actualMotion = beforeMoveCollider == null ? null : beforeMoveCollider.actualMotion();
//    if (actualMotion != null) {
//      motion.motionX = actualMotion.motionX();
//      motion.motionZ = actualMotion.motionZ();
//    }
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();

	  if (environment.motionMultiplier() != null) {
      motion.setNull();
      environment.resetMotionMultiplier();
    }

    boolean elytraFlying = environment.shouldHaveFallFlyingPose();
    boolean inWater = environment.inWater();
    boolean inLava = environment.inLava();
    double lavaDepthBeforeMove = environment.lavaDepth();
	  double gravity = environment.gravity();
    double slipperiness;
    boolean movementAttributes = protocol.supportsMovementAttributes();
    float airDragModifier = movementAttributes ? (float) meta.abilities().airDragModifier() : 1.0F;
    float airDrag = movementAttributes ? MovementCharacteristics.computeModifiedFriction(0.91F, airDragModifier) : 0.91F;

    if (environment.lastOnGround()) {
      double blockPositionX = floor(environment.verifiedLastPositionX());
      double blockPositionY = floor(environment.verifiedLastPositionY() - environment.frictionPosSubtraction());
      double blockPositionZ = floor(environment.verifiedLastPositionZ());
      float blockFriction = MovementCharacteristics.currentBlockFriction(
        user, environment, player.getWorld(), blockPositionX, blockPositionY, blockPositionZ
      );
      if (movementAttributes) {
        blockFriction = MovementCharacteristics.computeModifiedFriction(
          blockFriction,
          (float) meta.abilities().frictionModifier()
        );
      }
      slipperiness = blockFriction * airDrag;
    } else {
      slipperiness = airDrag;
    }

    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, position);
    environment.setBoundingBox(boundingBox);

    if (environment.inWeb()) {
      motion.setNull();
      environment.resetInWeb();
    }

    if (result != null && result.offsetMotionDiffersFromActualMotionInXZ()) {
      Motion actualMotion = result.actualMotion();
      if (actualMotion != null && configuration.overrideEndMotionToActualMotion()) {
        motion.setTo(actualMotion);
      }
    }

    // Update supporting block if on-ground
    if (user.meta().protocol().trailsAndTailsUpdate() && result != null) {
      Motion actualMotion = result.actualMotion();

      if (actualMotion != null && Math.abs(actualMotion.motionY()) > 0) {
        boolean verticalCollision = result.offsetMotionDiffersFromActualMotionInY();
        boolean verticalCollisionBelow = verticalCollision && actualMotion.motionY < 0.0;
        environment.checkSupportingBlock(verticalCollisionBelow, motion);
      }
      environment.compileSpecialBlocks();
    }

    updateFallStateAfter(user, environment, motion, environment.onGround());

    if (movementAttributes) {
      restituteMovementAfterCollisions(user, environment, result, motion, gravity, airDragModifier);
    } else {
      if (environment.motionXReset()) {
        motion.setMotionX(0.0);
      }
      if (environment.motionZReset()) {
        motion.setMotionZ(0.0);
      }
    }

    simulateMovementOfCollidedBlocksAfter(user, environment, configuration, motion);

    if (inWater) {
      simulateWaterAfter(user, environment, configuration, motion, gravity);
    } else if (inLava) {
      simulateLavaAfter(
        user, environment, configuration, motion, gravity,
        fallingBeforeMove, lavaDepthBeforeMove
      );
    } else if (!elytraFlying) {
      simulateNormalAfter(
        user, environment, configuration, motion, gravity,
        slipperiness, movementAttributes, airDragModifier
      );
    }

    if (user.meta().protocol().newBlockEntityIntersectionLogic()) {
      environment.aquaticUpdateLavaReset();
      applyEffectsFromBlocks(user, environment, configuration, motion);
    }

    if (protocol.combatUpdate()
      && MinecraftVersions.VER1_9_0.atOrAbove() /* todo: add scoreboard check */) {
      performGlobalEntityPush(user, environment, motion, boundingBox);
    }

    if (protocol.fireworkBoostTicksAfterPlayer()
      && environment.shouldHaveFallFlyingPose()) {
      applyAttachedFireworkBoosts(
        motion,
        environment.lookVector(),
        environment.activeFireworkRockets()
      );
    }

    return motion;
  }

  private void applyAttachedFireworkBoosts(
    Motion motion, Vector lookVector, int rocketCount
  ) {
    for (int rocket = 0; rocket < rocketCount; rocket++) {
      motion.motionX += lookVector.getX() * 0.1D
        + (lookVector.getX() * 1.5D - motion.motionX) * 0.5D;
      motion.motionY += lookVector.getY() * 0.1D
        + (lookVector.getY() * 1.5D - motion.motionY) * 0.5D;
      motion.motionZ += lookVector.getZ() * 0.1D
        + (lookVector.getZ() * 1.5D - motion.motionZ) * 0.5D;
    }
  }

  private void updateFallStateAfter(
    User user, SimulationEnvironment environment,
    Motion motion, boolean onGround
  ) {
    if (!environment.inWater()) {
      updateAquatics(user, motion, environment, true);
    }
    if (onGround) {
      environment.resetFallDistance();
    } else if (motion.motionY() < 0.0D) {
      environment.addFallDistance(-motion.motionY());
    }
  }

  private void simulateMovementOfCollidedBlocksAfter(
    User user, SimulationEnvironment environment,
    MovementConfiguration configuration, Motion motion
  ) {
    Player player = user.player();
	  MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();

    Material block = environment.collideMaterial();

    BlockPhysics.fallenUpon(user, block);

    // onLanded
    if (!clientData.supportsMovementAttributes()
      && environment.collidedVertically()) {
      Motion collisionVector = BlockPhysics.blockLanded(
        user, environment, block, motion.motionX, environment.baseMotionY(), motion.motionZ
      );
      if (collisionVector != null) {
        motion.setTo(collisionVector);
      } else {
        motion.motionY = 0.0;
      }
    }

    // Block collisions

    if (!clientData.newBlockEntityIntersectionLogic()) {
      environment.aquaticUpdateLavaReset();
      applyEffectsFromBlocks(user, environment, configuration, motion);
    }

    if (clientData.beeUpdate()
      && !meta.abilities().flying()
      && !environment.shouldHaveFallFlyingPose()) {
      int soulSandModifier = Enchantments.resolveSoulSpeedModifier(player);
      boolean movementEfficiencyAttribute = clientData.supportsMovementEfficiencyAttribute()
        && meta.abilities().hasAttribute("generic.movement_efficiency");
      if (movementEfficiencyAttribute || soulSandModifier == 0 || !environment.blockOnPositionSoulSpeedAffected()) {
        float speedFactor = environment.blockSpeedFactor();
        motion.motionX *= speedFactor;
        motion.motionZ *= speedFactor;
      }
    }
  }

  private final static Material HONEY_BLOCK = MaterialSearch.materialThatIsNamed("HONEY_BLOCK");

  private void restituteMovementAfterCollisions(
    User user,
    SimulationEnvironment environment,
    SimulationResult result,
    Motion motion,
    double gravity,
    float airDragModifier
  ) {
    if (result == null) {
      return;
    }

    Motion collisionInput = result.intermittentResult() == null
      ? result.actualMotion()
      : result.intermittentResult();

    if (collisionInput == null || result.offsetMotion() == null) {
      return;
    }

    Motion clippedMovement = result.offsetMotion();
    boolean xCollision = !similar(collisionInput.motionX, clippedMovement.motionX);
    boolean zCollision = !similar(collisionInput.motionZ, clippedMovement.motionZ);
    boolean horizontalCollision = xCollision || zCollision;
    boolean movedVertically = Math.abs(collisionInput.motionY) > 0.0D;
    if (!(movedVertically && result.collidedVertically()) && !horizontalCollision) {
      return;
    }

    boolean suppressingBounce = environment.isSneaking();
    double restitution = suppressingBounce ? 0.0D : user.meta().abilities().bounciness();
    double currentMotionX = collisionInput.motionX;
    double currentMotionY = collisionInput.motionY;
    double currentMotionZ = collisionInput.motionZ;
    if (xCollision) {
      motion.motionX = -currentMotionX * restitution;
    }
    if (zCollision) {
      motion.motionZ = -currentMotionZ * restitution;
    }

    if (result.collidedVertically()) {
      if (result.onGround()) {
        Material effectBlock = environment.collideMaterial();
        restitution = !(-currentMotionY < gravity)
          && !suppressingBounce
          && (HONEY_BLOCK != null && !HONEY_BLOCK.equals(effectBlock))
          ? Math.max(restitution, BlockProperties.of(effectBlock).bounceRestitution())
          : 0.0D;
      }

      double gravityCompensation;
      double effectiveDrag;
      if (restitution > 0.0D) {
        double portionWithMovement = result.offsetMotion().motionY / currentMotionY;
        gravityCompensation = portionWithMovement * gravity;
        float airDrag = MovementCharacteristics.computeModifiedFriction(0.98F, airDragModifier);
        effectiveDrag = 1.0D + portionWithMovement * ((double) airDrag - 1.0D);
      } else {
        gravityCompensation = 0.0D;
        effectiveDrag = 1.0D;
      }

      motion.motionY = (gravityCompensation - currentMotionY) * effectiveDrag * restitution;
    }
  }

  private static boolean similar(double first, double second) {
    return Math.abs(second - first) < 1.0E-5F;
  }

  private void applyStepOnMechanics(
    User user, SimulationEnvironment environment,
    Motion motion
  ) {
    // EntityCollidedWithBlock
    if (environment.onGround()/* && !environment.isSneaking()*/) {
      Motion newMotion = BlockPhysics.stepOn(
        user, environment.collideMaterial(), environment,
        motion.motionX, motion.motionY, motion.motionZ
      );
      if (newMotion != null) {
        motion.setTo(newMotion);
      }
    }
  }

  private void applyEffectsFromBlocks(
    User user, SimulationEnvironment environment,
    MovementConfiguration configuration, Motion motion
  ) {
    if (environment.onGround()) {
      applyStepOnMechanics(user, environment, motion);
    }
    SimulationResult result = environment.simulationResult();
    Position from = environment.lastPosition();
    Position to = environment.position();
    Motion intermittentResult = result.intermittentResult();
    List<EntityMovement> movements = Collections.singletonList(EntityMovement.of(from, to, intermittentResult));
    BlockInsideChecks.select(
      user, user.blockInsideChecks(),
      configuration
    ).checkInsideBlocks(
      user, environment, motion, movements
    );
  }

  private void simulateWaterAfter(
    User user,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Motion motion,
    double gravity
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
	  float motionXZMultiplier;
    if (protocol.aquaticUpdate()) {
      motionXZMultiplier = /*environment.pose() == Pose.SWIMMING || */configuration.isSprinting() ? 0.9f : 0.8f;
    } else {
      motionXZMultiplier = 0.8f;
    }
    motionXZMultiplier = interpolatedWaterFriction(
      user, environment, motionXZMultiplier, 0.54600006F
    );
    if (Effects.dolphinEffectActive(player)) {
      motionXZMultiplier = 0.96F;
    }
    motion.motionX *= motionXZMultiplier;
    motion.motionY *= 0.8f;
    motion.motionZ *= motionXZMultiplier;
    if (!protocol.aquaticUpdate()) {
      motion.motionY -= 0.02D;
    }
    if (protocol.aquaticUpdate() && !configuration.isSprinting()) {
      if (motion.motionY <= 0.0D
        && Math.abs(motion.motionY - 0.005D) >= 0.003D
        && Math.abs(motion.motionY - gravity / 16.0D) < 0.003D
      ) {
        motion.motionY = -0.003D;
      } else {
        motion.motionY -= gravity / 16.0D;
      }
    }

    if (environment.collidedHorizontally()) {
      double liquidMotionY;
      if (user.meta().protocol().aquaticUpdate()) {
        liquidMotionY = motion.motionY + 0.6f - environment.positionY() + environment.verifiedLastPositionY();
      } else {
        liquidMotionY = motion.motionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        user, environment, environment.boundingBox(), motion.motionX, liquidMotionY, motion.motionZ
      );
      if (offsetPositionInLiquid) {
        motion.motionY = 0.30000001192092896D;
      }
    }
  }

  private float interpolatedWaterFriction(
    User user,
    SimulationEnvironment environment,
    float currentValue,
    float efficientValue
  ) {
    MetadataBundle meta = user.meta();
    if (meta.protocol().supportsWaterMovementEfficiencyAttribute()
      && meta.abilities().hasAttribute("generic.water_movement_efficiency")) {
      float efficiency = (float) meta.abilities().waterMovementEfficiency();
      if (!environment.lastOnGround()) {
        efficiency *= 0.5F;
      }
      return efficiency > 0.0F
        ? currentValue + (efficientValue - currentValue) * efficiency
        : currentValue;
    }

    float depthStrider = Enchantments.resolveDepthStriderModifier(user.player());
    if (depthStrider > 3.0F) {
      depthStrider = 3.0F;
    }
    if (!environment.lastOnGround()) {
      depthStrider *= 0.5F;
    }
    return depthStrider > 0.0F
      ? currentValue + (efficientValue - currentValue) * depthStrider / 3.0F
      : currentValue;
  }

  private void simulateLavaAfter(
    User user,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Motion motion,
    double gravity,
    boolean fallingBeforeMove,
    double lavaDepthBeforeMove
  ) {
    ProtocolMetadata protocol = user.meta().protocol();

    if (protocol.fluidHeightBasedLavaMovement()) {
      double lavaDepth = protocol.refreshesFluidStateAfterMove()
        ? environment.lavaDepth()
        : lavaDepthBeforeMove;
      double lavaJumpThreshold = environment.eyeHeight() < 0.4D ? 0.0D : 0.4D;
      if (lavaDepth <= lavaJumpThreshold) {
        motion.motionX *= 0.5D;
        motion.motionY *= 0.8F;
        motion.motionZ *= 0.5D;

        if (gravity != 0.0D && !configuration.isSprinting()) {
          if (fallingBeforeMove
            && Math.abs(motion.motionY - 0.005D) >= 0.003D
            && Math.abs(motion.motionY - gravity / 16.0D) < 0.003D) {
            motion.motionY = -0.003D;
          } else {
            motion.motionY -= gravity / 16.0D;
          }
        }
      } else {
        motion.multiply(0.5D);
      }
    } else {
      motion.multiply(0.5D);
    }

    motion.motionY -= protocol.aquaticUpdate() ? gravity / 4.0D : 0.02D;

    if (environment.collidedHorizontally()) {
      double liquidMotionY;
      if (protocol.aquaticUpdate()) {
        liquidMotionY = motion.motionY + 0.6f - environment.positionY() + environment.verifiedLastPositionY();
      } else {
        liquidMotionY = motion.motionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        user, environment, environment.boundingBox(), motion.motionX, liquidMotionY, motion.motionZ
      );
      if (offsetPositionInLiquid) {
        motion.motionY = 0.30000001192092896D;
      }
    }
  }

  private void simulateNormalAfter(
    User user,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Motion motion,
    double gravity,
    double slipperiness,
    boolean movementAttributes,
    float airDragModifier
  ) {
    Player player = user.player();

    boolean climbable = MovementCharacteristics.onClimbable(
      user,
      environment.positionX(),
      environment.positionY(),
      environment.positionZ()
    );
    if (climbable) {
      // calling isJumping will cause a recompute
      if (environment.collidedHorizontally() || configuration.isJumping()) {
//        motion.motionY = Math.max(motion.motionY, 0.2D);
        motion.motionY = 0.2;
      }
    }

    if (Effects.levitationEffectActive(player)) {
      int levitationAmplifier = Effects.effectAmplifier(player, Effects.EFFECT_LEVITATION);
      motion.motionY += (0.05D * (double) (levitationAmplifier + 1) - motion.motionY) * 0.2D;
      environment.resetFallDistance();
    } else {
      // todo isChunkLoaded support
      motion.motionY -= gravity;
    }
    motion.motionX *= slipperiness;
    motion.motionY *= movementAttributes
      ? MovementCharacteristics.computeModifiedFriction(0.98F, airDragModifier)
      : 0.98F;
    motion.motionZ *= slipperiness;
  }

  private void performGlobalEntityPush(User user, SimulationEnvironment environment, Motion context, BoundingBox boundingBox) {
    Collection<Entity> entities = user.meta().connection().tracedEntities();
    environment.setPushedByEntity(false);
    for (Entity entity : entities) {
      if (
        !entity.tracingEnabled() ||
        (!entity.hasTypeData() || entity.typeData().isArmorStand())
      ) {
        continue;
      }
      BoundingBox entityBoundingBox = entity.boundingBox();
      if (entityBoundingBox.intersectsWith(boundingBox)) {
        applyEntityPush(environment, context, entity);
      }
      if (entityBoundingBox.growHorizontally(0.2).intersectsWith(boundingBox)) {
//        applyEntityPush(environment, context, entity);
        environment.setPushedByEntity(true);
      }
    }
  }

  private void applyEntityPush(SimulationEnvironment environment, Motion motionVector, Entity entity) {
    double xDistance = environment.positionX() - entity.position.posX;
    double zDistance = environment.positionZ() - entity.position.posZ;
    double biggerDistance = ClientMath.abs_max(xDistance, zDistance);
    if (biggerDistance >= (double) 0.01F) {
      biggerDistance = ClientMath.sqrt_double(biggerDistance);
      xDistance = xDistance / biggerDistance;
      zDistance = zDistance / biggerDistance;
      double pushFactor = 1.0D / biggerDistance;
      if (pushFactor > 1.0D) {
        pushFactor = 1.0D;
      }
      xDistance = xDistance * pushFactor;
      zDistance = zDistance * pushFactor;
      xDistance *= 0.05F;
      zDistance *= 0.05F;
      if (!environment.isInVehicle()) {
        environment.setPushedByEntity(true);
        motionVector.motionX += xDistance;
        motionVector.motionZ += zDistance;
      }
    }
  }

  @Override
  public void setback(User user, SimulationEnvironment environment, double predictedX, double predictedY, double predictedZ) {
    ViolationMetadata violationMetadata = user.meta().violationLevel();
    int setbackTicks = (environment.ticksPast(EXTERNAL_VELOCITY) <= 8) ? 8 : ((violationMetadata.physicsVL > 50) ? 3 : 2);
    Modules.mitigate()
      .movement()
      .emulationSetBack(
        user.player(), Motion.of(
          predictedX, predictedY, predictedZ
        ), setbackTicks, (environment.ticksPast(EXTERNAL_VELOCITY) > 16)
      );
  }

  @Override
  public float stepHeight(User user) {
    if (!user.meta().protocol().supportsStepHeightAttribute()) {
      return super.stepHeight(user);
    }
    return (float) user.meta().abilities().stepHeight();
  }
}
