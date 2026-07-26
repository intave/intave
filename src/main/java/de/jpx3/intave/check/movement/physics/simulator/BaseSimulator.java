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

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.EXTERNAL_VELOCITY;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.IN_LAVA;
import static de.jpx3.intave.share.ClientMath.clamp_double;
import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;

class BaseSimulator extends Simulator {
  @Override
  public Motion simulatePreTick(
    User user, Motion baseMotion,
    SimulationEnvironment environment
  ) {
    baseMotion = baseMotion.copy();
    moveOutOfBlocks(user, baseMotion, environment);
    handleSneakInWater(user, baseMotion, environment);
    updateAquatics(user, baseMotion, environment);
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
    if (protocol.aquaticUpdate() && environment.isSneaking() && environment.inWater()) {
      motion.motionY -= 0.04F;
    }
  }

  private void updateAquatics(User user, Motion baseMotion, SimulationEnvironment environment) {
    updateInWater(user, baseMotion, environment);
    updateInLava(environment);
    environment.updateEyesInWater();
  }

  private void updateInWater(User user, Motion baseMotion, SimulationEnvironment environment) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    BoundingBox boundingBox = environment.boundingBox();
    if (!clientData.aquaticUpdate()) {
      boundingBox = boundingBox.grow(0.0D, -0.4000000059604645D, 0.0D);
    }
    boundingBox = boundingBox.shrink(0.001D);
    environment.setInWater(user.waterflow().applyFlowTo(user, environment, baseMotion, boundingBox));
  }

  private void updateInLava(SimulationEnvironment environment) {
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
	  boolean swimming = environment.shouldHaveSwimmingPose();
    boolean crouching = pose == Pose.CROUCHING;
    boolean waterUpdate = protocol.aquaticUpdate();

    motion = motion.copy();

    if (crouching || (!protocol.beeUpdate() && environment.isSneaking())) {
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

    if (jumped) {
      boolean allowJumpInLiquid = false;
      if (
        protocol.aquaticUpdate() && inWater &&
        environment.onGround() && environment.pose().height(user, environment) >= 0.4
      ) {
        Position lastPosition = environment.lastPosition();
        double fluidDepth = user.waterflow().fluidDepthAt(
          user, BoundingBox.fromPosition(user, environment, lastPosition)
        );
        boolean fluidStateEmpty = !Fluids.fluidPresentAt(user, lastPosition);
        allowJumpInLiquid = fluidStateEmpty || fluidDepth <= 0.4;
      }
      if (inWater && !allowJumpInLiquid) {
        motion.motionY += 0.04F;
      } else if (inLava) {
        // #handleJumpLava
        motion.motionY += 0.04F;
      } else if (environment.lastOnGround()) {
        motion.motionY = user.protocolVersion() >= 768 ?
          Math.max(environment.jumpMotion(), environment.baseMotionY()) :
          environment.jumpMotion();
        if (sprinting) {
          motion.motionX -= yawSine * 0.2F;
          motion.motionZ += yawCosine * 0.2F;
        }
      }
    }
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
    Player player = user.player();
    float friction = 0.02F;
    float depthStrider = Enchantments.resolveDepthStriderModifier(player);
    if (depthStrider > 3.0F) {
      depthStrider = 3.0F;
    }
    if (!environment.lastOnGround()) {
      depthStrider *= 0.5F;
    }
    if (depthStrider > 0.0F) {
      friction += (environment.aiMoveSpeed(sprinting) - friction) * depthStrider / 3.0F;
    }
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
//    SimulationResult beforeMoveCollider = environment.result();
//    Motion actualMotion = beforeMoveCollider == null ? null : beforeMoveCollider.actualMotion();
//    if (actualMotion != null) {
//      motion.motionX = actualMotion.motionX();
//      motion.motionZ = actualMotion.motionZ();
//    }
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    Pose pose = environment.pose();

    if (environment.motionMultiplier() != null) {
      motion.setNull();
      environment.resetMotionMultiplier();
    }

    boolean elytraFlying = pose == Pose.FALL_FLYING;
    boolean inWater = environment.inWater();
    boolean inLava = environment.inLava();
	  double gravity = environment.gravity();
    double slipperiness;

    if (environment.lastOnGround()) {
      double blockPositionX = floor(environment.verifiedLastPositionX());
      double blockPositionY = floor(environment.verifiedLastPositionY() - environment.frictionPosSubtraction());
      double blockPositionZ = floor(environment.verifiedLastPositionZ());
      slipperiness = MovementCharacteristics.currentSlipperiness(user, player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
    } else {
      slipperiness = 0.91f;
    }

    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, position);
    environment.setBoundingBox(boundingBox);

    if (environment.inWeb()) {
      motion.setNull();
      environment.resetInWeb();
    }

    if (environment.motionXReset()) {
      motion.setMotionX(0.0);
    }
    if (environment.motionZReset()) {
      motion.setMotionZ(0.0);
    }

    SimulationResult result = environment.simulationResult();
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
    simulateMovementOfCollidedBlocksAfter(user, environment, motion, boundingBox);

    if (inWater) {
      simulateWaterAfter(user, environment, configuration, motion, gravity);
    } else if (inLava) {
      simulateLavaAfter(user, environment, motion);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, environment, configuration, motion, gravity, slipperiness);
    }

    if (user.meta().protocol().newBlockEntityIntersectionLogic()) {
      simulateApplyEffectsFromBlocks(user, environment, motion, boundingBox);
    }

    if (clientData.combatUpdate()
      && MinecraftVersions.VER1_9_0.atOrAbove() /* todo: add scoreboard check */) {
      performGlobalEntityPush(user, environment, motion, boundingBox);
    }

    return motion;
  }

  private void updateFallStateAfter(
    User user, SimulationEnvironment environment,
    Motion motion, boolean onGround
  ) {
    if (!environment.inWater()) {
      updateAquatics(user, motion, environment);
    }
    if (onGround) {
      environment.resetFallDistance();
    } else if (motion.motionY() < 0.0D) {
      environment.addFallDistance(-motion.motionY());
    }
  }

  private void simulateMovementOfCollidedBlocksAfter(
    User user, SimulationEnvironment environment,
    Motion motion, BoundingBox entityBoundingBox
  ) {
    Player player = user.player();
    World world = player.getWorld();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();

    double positionX = environment.positionX();
    double positionY = environment.positionY();
    double positionZ = environment.positionZ();

    Material block = environment.collideMaterial();

    if (IntaveControl.DEBUG_MOVEMENT_BLOCK_FALLEN_UPON) {
      if (block != null) {
        String name = block.name();
        Synchronizer.synchronize(() -> {
          player.sendMessage("Block fallen upon: " + name);
        });
      }
    }

    BlockPhysics.fallenUpon(user, block);

    // onLanded
    if (environment.collidedVertically()) {
      Motion collisionVector = BlockPhysics.blockLanded(
        user, block, motion.motionX, environment.baseMotionY(), motion.motionZ
      );
      if (collisionVector != null) {
        motion.setTo(collisionVector);
      } else {
        motion.motionY = 0.0;
      }
    }

//    environment.checkSupportingBlock();

    // EntityCollidedWithBlock
    if (environment.onGround() && !environment.isSneaking()) {
      Motion collisionVector =
        BlockPhysics.stepOn(user, block, environment, motion.motionX, motion.motionY, motion.motionZ);
      if (collisionVector != null) {
        motion.setTo(collisionVector);
      }
    }

    // Block collisions

    environment.aquaticUpdateLavaReset();

//    if (!user.meta().protocol().newBlockEntityIntersectionLogic()) {
    double limit = 1.0E-7D;
    int blockPositionStartX = floor(entityBoundingBox.minX + limit);
      int blockPositionStartY = floor(entityBoundingBox.minY + limit);
      int blockPositionStartZ = floor(entityBoundingBox.minZ + limit);
      int blockPositionEndX = floor(entityBoundingBox.maxX - limit);
      int blockPositionEndY = floor(entityBoundingBox.maxY - limit);
      int blockPositionEndZ = floor(entityBoundingBox.maxZ - limit);

      Location blockCollisionFrom = new Location(world, positionX, positionY, positionZ);
      for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
        for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
          for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
            Location location = new Location(world, x, y, z);
            Material material = VolatileBlockAccess.typeAccess(user, world, x, y, z);
            Motion collisionMotion = BlockPhysics.entityInside(
              user, material,
              environment,
              location, blockCollisionFrom,
              motion.motionX, motion.motionY, motion.motionZ
            );
            if (collisionMotion != null) {
              motion.setTo(collisionMotion);
            }
          }
        }
      }
//    }

    if (clientData.protocolVersion() >= VER_1_14 && environment.pose() != Pose.FALL_FLYING) {
      int soulSandModifier = Enchantments.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0 || !environment.blockOnPositionSoulSpeedAffected()) {
        float speedFactor = environment.blockSpeedFactor();
        motion.motionX *= speedFactor;
        motion.motionZ *= speedFactor;
      }
    }
  }

  private void simulateApplyEffectsFromBlocks(
    User user, SimulationEnvironment environment, Motion motion, BoundingBox boundingBox
  ) {
    Position from = environment.verifiedLastPosition();
    Position to = environment.position();
    Motion move = from.motionTo(to);

    SimulationResult simulationResult = environment.simulationResult();
    if (simulationResult == null || simulationResult.isValid()) {
      return;
    }
    Motion crazyMotion = simulationResult.intermittentResult();

    LongSet visitedBlocks = new LongOpenHashSet();

		int i = 16;
    if (crazyMotion != null && move.lengthSquared() > 0.0) {
      for (Direction.Axis axis : Direction.axisStepOrder(crazyMotion)) {
        double motionPartial = crazyMotion.partialMotionIn(axis);
        if (motionPartial != 0.0) {
	        Position positionPartial = from.relative(axis.positive(), motionPartial);
	        i -= checkInsideBlocks(user, environment, from, positionPartial, visitedBlocks, i);
					from = positionPartial;
        }
      }
    } else {
			i -= checkInsideBlocks(user, environment, from, to, visitedBlocks, i);
    }
		if (i <= 0) {
			checkInsideBlocks(user, environment, from, to, visitedBlocks, 1);
		}
  }

  private int checkInsideBlocks(
    User user,
		SimulationEnvironment environment,
    Position from, Position to,
    LongSet visitedBlocks,
    int limit
  ) {
	  BoundingBox box = BoundingBox.fromPosition(user, environment, to).shrink(0.00001f);
		boolean furtherThanOneBlock = from.distanceSquared(to) > (0.9999900000002526 * 0.9999900000002526);

    return 0;
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
    float depthStriderMultiplier = Math.min(3.0f, Enchantments.resolveDepthStriderModifier(player));
    if (!environment.lastOnGround()) { // wrong?
      depthStriderMultiplier *= 0.5F;
    }
    if (depthStriderMultiplier > 0.0F) {
      motionXZMultiplier += (0.54600006F - motionXZMultiplier) * depthStriderMultiplier / 3.0F;
    }
    if (Effects.dolphinEffectActive(player)) {
      motionXZMultiplier = 0.96F;
    }
    motion.motionX *= motionXZMultiplier;
    motion.motionY *= 0.8f;
    motion.motionZ *= motionXZMultiplier;
    if (!protocol.aquaticUpdate()) {
      motion.motionY -= 0.02D;
    }
    // todo check if it is not movementconfig sprinting
    if (protocol.aquaticUpdate() && environment.pose() != Pose.SWIMMING && !configuration.isSprinting()) {
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
        user, environment.boundingBox(), motion.motionX, liquidMotionY, motion.motionZ
      );
      if (offsetPositionInLiquid) {
        motion.motionY = 0.30000001192092896D;
      }
    }
  }

  private void simulateLavaAfter(
    User user, SimulationEnvironment environment,
    Motion context
  ) {
    context.motionX *= 0.5D;
    context.motionY *= 0.5D;
    context.motionZ *= 0.5D;
    context.motionY -= 0.02D;

    if (environment.collidedHorizontally()) {
      double liquidMotionY;
      if (user.meta().protocol().aquaticUpdate()) {
        liquidMotionY = context.motionY + 0.6f - environment.positionY() + environment.verifiedLastPositionY();
      } else {
        liquidMotionY = context.motionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        user, environment.boundingBox(), context.motionX, liquidMotionY, context.motionZ
      );
      if (offsetPositionInLiquid) {
        context.motionY = 0.30000001192092896D;
      }
    }
  }

  private void simulateNormalAfter(
    User user,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Motion motion, double gravity, double slipperiness
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
    motion.motionY *= 0.98f;
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
      if (entity.boundingBox().intersectsWith(boundingBox)) {
        applyEntityPush(environment, context, entity);
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
}
