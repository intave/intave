package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.modifier.PowderSnowCollisionModifier;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

import static de.jpx3.intave.share.ClientMath.clamp_double;
import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;

@Relocate
class BaseSimulator extends Simulator {
  @Override
  public Simulation simulate(
    User user,
    Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    // guessed movement configuration
    float forward = configuration.forward() * 0.98f;
    float strafe = configuration.strafe() * 0.98f;
    boolean handActive = configuration.isHandActive();
    boolean jumped = configuration.isJumping();
    boolean sprinting = configuration.isSprinting();
    int reduceTicks = configuration.reduceTicks();

    // static movement configuration
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
    Pose pose = environment.pose();

    float yawSine = environment.yawSine();
    float yawCosine = environment.yawCosine();
    double positionX = environment.verifiedPositionX();
    double positionY = environment.verifiedPositionY();
    double positionZ = environment.verifiedPositionZ();
    boolean inWater = environment.inWater();
    boolean inLava = environment.inLava();
    boolean elytraFlying = pose == Pose.FALL_FLYING;
    boolean swimming = pose == Pose.SWIMMING;
    boolean crouching = pose == Pose.CROUCHING;
    boolean waterUpdate = protocol.waterUpdate();

    if (crouching || (!protocol.beeUpdate() && environment.isSneaking())) {
      double sneakingModifier = clamp_double(0.3 + Enchantments.resolveSwiftSpeedModifier(user.player()) * 0.15f, 0.0f, 1.0f);
      forward = (float) ((double) forward * sneakingModifier);
      strafe = (float) ((double) strafe * sneakingModifier);
    }
    if (handActive) {
      forward *= 0.2f;
      strafe *= 0.2f;
    }
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

    if (jumped) {
      boolean allowJumpInLiquid = false;
      if (
        protocol.waterUpdate() && inWater &&
        environment.onGround() && environment.pose().height(user) >= 0.4
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
      } else {
        motion.motionY = environment.jumpMotion();
        if (/*movementData.sprintingAllowed()*/ sprinting) {
          motion.motionX -= yawSine * 0.2F;
          motion.motionZ += yawCosine * 0.2F;
        }
      }
    }
    if (waterUpdate && swimming) {
      double d3 = environment.lookVector().getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
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
      performDefaultMoveSimulationOfState(user, motion, environment, forward, strafe, yawSine, yawCosine);
    }
    if (!inWater && !elytraFlying && !inLava) {
      tryRelinkFlyingPosition(user, motion, environment);
    }
    ColliderResult collisionResult = Colliders.collision(user, environment, motion, environment.inWeb(), positionX, positionY, positionZ);
    notePossibleFlyingPacket(user, collisionResult);
    return Simulation.of(user, configuration, collisionResult);
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
    float moveForward,
    float moveStrafe,
    float yawSine,
    float yawCosine) {
    performRelativeMoveSimulationOfState(context, environment.friction(), yawSine, yawCosine, moveForward, moveStrafe);

    boolean onLadder = MovementCharacteristics.onClimbable(
      user,
      environment.verifiedPositionX(),
      environment.verifiedPositionY(),
      environment.verifiedPositionZ()
    );

    if (onLadder) {
      float axisLimit = 0.15F;
      context.motionX = ClientMath.clamp_double(context.motionX, -axisLimit, axisLimit);
      context.motionY = ClientMath.clamp_double(context.motionY, -axisLimit, Integer.MAX_VALUE); // no positive limit
      context.motionZ = ClientMath.clamp_double(context.motionZ, -axisLimit, axisLimit);
//      if (context.motionY < -0.15D) {
//        context.motionY = -0.15D;
//      }
      Material type = VolatileBlockAccess.typeAccess(
        user, user.player().getWorld(),
        floor(environment.verifiedPositionX()),
        floor(environment.verifiedPositionY()),
        floor(environment.verifiedPositionZ())
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

  @IdoNotBelongHere
  private void tryRelinkFlyingPosition(
    User user, Motion context, SimulationEnvironment environment) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();

    double positionX = environment.verifiedPositionX();
    double positionY = environment.verifiedPositionY();
    double positionZ = environment.verifiedPositionZ();

    boolean onGround;
    double slipperiness = environment.lastOnGround()
      ? MovementCharacteristics.currentSlipperiness(user, player.getWorld(), positionX, positionY, positionZ)
      : 0.91f;
    double resetMotion = environment.resetMotion();
    double jumpUpwardsMotion = environment.jumpMotion();

    int interpolations = 0;
    double interpolateX = context.motionX;
    double interpolateY = context.motionY;
    double interpolateZ = context.motionZ;

    for (; interpolations <= 2; interpolations++) {
      SimpleColliderResult colliderResult =
        Colliders.simplifiedCollision(
          player, environment, positionX, positionY, positionZ, interpolateX, interpolateY, interpolateZ);

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionZ();
      positionZ += colliderResult.motionY();

      double diffX = positionX - environment.verifiedPositionX();
      double diffY = positionY - environment.verifiedPositionY();
      double diffZ = positionZ - environment.verifiedPositionZ();
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround
        && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - environment.motionY()) < 0.00001
        && jumpLessThanExpected;

      if (!flyingPacket(diffX, diffY, diffZ) && !jump) {
        break;
      } else if (jump
        && flyingPacket(diffX * 0.05, 0.0, diffZ * 0.05)
        && !movementData.denyJump()
      ) {
        context.motionY = jumpUpwardsMotion;
        movementData.artificialFallDistance = 0f;
        movementData.physicsPacketRelinkFlyVL = 0;
        break;
      } else if (environment.motionY() < 0) {
        double nextPredictedX = interpolateX * slipperiness;
        double nextPredictedY = (interpolateY - 0.08) * 0.98f;
        double nextPredictedZ = interpolateZ * slipperiness;

        if (Math.abs(interpolateX) < resetMotion) {
          interpolateX = 0;
        }
        if (Math.abs(interpolateY) < resetMotion) {
          interpolateY = 0;
        }
        if (Math.abs(interpolateZ) < resetMotion) {
          interpolateZ = 0;
        }

        applyCollidedMotionsToContext(
          player,
          environment,
          context,
          positionX,
          positionY,
          positionZ,
          nextPredictedX,
          nextPredictedY,
          nextPredictedZ
        );
      }

      interpolateX *= slipperiness;
      interpolateY -= environment.gravity();
      interpolateY *= 0.98f;
      interpolateZ *= slipperiness;
      if (Math.abs(interpolateX) < resetMotion) {
        interpolateX = 0;
      }
      if (Math.abs(interpolateY) < resetMotion) {
        interpolateY = 0;
      }
      if (Math.abs(interpolateZ) < resetMotion) {
        interpolateZ = 0;
      }
    }
    if (interpolations != 0) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  void applyCollidedMotionsToContext(
    Player player,
    SimulationEnvironment environment,
    Motion motion,
    double positionX,
    double positionY,
    double positionZ,
    double motionX,
    double motionY,
    double motionZ
  ) {
    SimpleColliderResult colliderResult =
      Colliders.simplifiedCollision(
        player, environment, positionX, positionY, positionZ, motionX, motionY, motionZ
      );
    motion.motionX = colliderResult.motionX();
    motion.motionY = colliderResult.motionY();
    motion.motionZ = colliderResult.motionZ();
  }

  @IdoNotBelongHere
  public void notePossibleFlyingPacket(User user, ColliderResult collisionResult) {
    MovementMetadata movementData = user.meta().movement();
    Motion context = collisionResult.motion();
    if (flyingPacket(context.motionX, context.motionY, context.motionZ)) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  private static final double FLYING_DISTANCE = 0.03 * 0.03;

  boolean flyingPacket(double diffX, double diffY, double diffZ) {
    double distance = diffX * diffX + diffY * diffY + diffZ * diffZ;
    return distance <= FLYING_DISTANCE;
  }

  private static final Material POWDER_SNOW = MaterialSearch.materialThatIsNamed("POWDER_SNOW");

  @Override
  public void prepareNextTick(
    User user,
    SimulationEnvironment environment,
    double positionX,
    double positionY,
    double positionZ,
    double motionX,
    double motionY,
    double motionZ
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ProtocolMetadata clientData = meta.protocol();
    Motion motion = environment.motionProcessorContext();
    motion.setTo(motionX, motionY, motionZ);
    Pose pose = environment.pose();

    if (environment.motionMultiplier() != null) {
      motion.setNull();
      environment.resetMotionMultiplier();
    }

    boolean elytraFlying = pose == Pose.FALL_FLYING;
    boolean inWater = environment.inWater();
    boolean inLava = environment.inLava();
    boolean collidedHorizontally = environment.collidedHorizontally();
    double gravity = environment.gravity();
    double slipperiness;

    if (environment.lastOnGround()) {
      double blockPositionX = floor(environment.verifiedPositionX());
      double blockPositionY = floor(environment.verifiedPositionY() - environment.frictionPosSubtraction());
      double blockPositionZ = floor(environment.verifiedPositionZ());
      slipperiness = MovementCharacteristics.currentSlipperiness(user, player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
    } else {
      slipperiness = 0.91f;
    }

    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, positionX, positionY, positionZ);
    environment.setBoundingBox(boundingBox);

    if (environment.inWeb()) {
      motion.setNull();
      environment.resetInWeb();
    }

    if (environment.motionXReset()) {
      motion.motionX = 0.0;
    }
    if (environment.motionZReset()) {
      motion.motionZ = 0.0;
    }

    // Update supporting block if on-ground
    MovementMetadata movementData = user.meta().movement();
    if (user.meta().protocol().trailsAndTailsUpdate()) {
      if (movementData.onGround) {
        movementData.checkSupportingBlock(motion);
      } else {
        movementData.mainSupportingBlockPos = null;
      }
      movementData.compileSpecialBlocks();
    }
    simulateMovementOfCollidedBlocks(user, environment, motion, boundingBox);
    updateFallState(user, motionY, environment.onGround());

    if (inWater) {
      simulateWaterAfter(user, environment, motion, gravity);
    } else if (inLava) {
      simulateLavaAfter(user, environment, motion, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, environment, motion, gravity, slipperiness);
    }

    if (clientData.combatUpdate()
      && MinecraftVersions.VER1_9_0.atOrAbove() /* todo: add scoreboard check */) {
      performGlobalEntityPush(user, environment, motion, boundingBox);
    }

    if (!violationLevelData.isInActiveTeleportBundle) {
      if (violationLevelData.ignorePostTickMotionReset) {
        violationLevelData.ignorePostTickMotionReset = false;
        //        player.sendMessage("Ignore physics motion reset " + motion);
      } else {
        environment.setBaseMotion(motion);
      }
    }

    environment.increaseFlyingPacketTicks();
    environment.increaseEntityUseTicks();
    environment.increasePlayerAttackTicks();
    environment.increasePushedByWaterFlowTicks();

    if (environment.onGround()) {
      environment.resetPhysicsPacketRelinkFlyVL();
    }

    Material type = VolatileBlockAccess.typeAccess(user, player.getWorld(), positionX, positionY, positionZ);
    boolean climbingInPowderSnow = POWDER_SNOW != null && type == POWDER_SNOW && PowderSnowCollisionModifier.canWalkOnPowderSnow(player);
    if (climbingInPowderSnow) {
      environment.resetPowderSnowTicks();
    } else {
      environment.increasePowderSnowTicks();
    }
    environment.increaseEdgeSneakTickGrants();
  }

  private void updateFallState(User user, double motionY, boolean onGround) {
    MovementMetadata movementData = user.meta().movement();
    if (!movementData.inWater) {
      physics().updateAquatics(user);
    }
    if (onGround) {
//      if (movementData.artificialFallDistance > 3) {
//        Synchronizer.synchronize(() -> {
//          user.player().sendMessage(movementData.inWater() + "/" + Collision.rasterizedLiquidPresentSearch(user, movementData.boundingBox())+ " " + movementData.inLava() + " " + movementData.inWeb() + " " + movementData.onGround());
//        });
//      }
      physics().applyFallDamageUpdate(user);
      movementData.artificialFallDistance = 0;
    } else if (motionY < 0.0D) {
      movementData.artificialFallDistance += -motionY;
    }
  }

  private void simulateMovementOfCollidedBlocks(
    User user, SimulationEnvironment environment, Motion motion, BoundingBox entityBoundingBox
  ) {
    Player player = user.player();
    World world = player.getWorld();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();

    double positionX = environment.positionX();
    double positionY = environment.positionY();
    double positionZ = environment.positionZ();

//    int blockCollisionPosX = floor(positionX);
//    int blockCollisionPosY = floor(positionY - 0.2f);
//    int blockCollisionPosZ = floor(positionZ);

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
      Motion collisionVector =
        BlockPhysics.blockLanded(
          user, block, motion.motionX, environment.baseMotionY(), motion.motionZ);
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
        BlockPhysics.entityCollision(user, block, motion.motionX, motion.motionY, motion.motionZ);
      if (collisionVector != null) {
        motion.setTo(collisionVector);
      }
    }

    // Block collisions

//    movementData.aquaticUpdateInLava = false;
    environment.aquaticUpdateLavaReset();

    int blockPositionStartX = floor(entityBoundingBox.minX + 0.001);
    int blockPositionStartY = floor(entityBoundingBox.minY + 0.001);
    int blockPositionStartZ = floor(entityBoundingBox.minZ + 0.001);
    int blockPositionEndX = floor(entityBoundingBox.maxX - 0.001);
    int blockPositionEndY = floor(entityBoundingBox.maxY - 0.001);
    int blockPositionEndZ = floor(entityBoundingBox.maxZ - 0.001);

    Location blockCollisionFrom = new Location(world, positionX, positionY, positionZ);
    for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
      for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
        for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
          Location location = new Location(world, x, y, z);
          Material material = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          Motion collisionMotion = BlockPhysics.entityCollision(
            user, material,
            location, blockCollisionFrom,
            motion.motionX, motion.motionY, motion.motionZ
          );
          if (collisionMotion != null) {
            motion.setTo(collisionMotion);
          }
        }
      }
    }

    if (clientData.protocolVersion() >= VER_1_14 && environment.pose() != Pose.FALL_FLYING) {
      int soulSandModifier = Enchantments.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0 || !environment.blockOnPositionSoulSpeedAffected()) {
        Material type = environment.frictionMaterial();
        float speedFactor = BlockProperties.of(type).speedFactor();
        motion.motionX *= speedFactor;
        motion.motionZ *= speedFactor;
      }
    }
  }

  private void simulateWaterAfter(
    User user,
    SimulationEnvironment environment,
    Motion motion,
    double gravity
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    double positionY = environment.positionY();
    float motionXZMultiplier;
    if (clientData.waterUpdate()) {
      motionXZMultiplier = environment.isSprinting() ? 0.9f : 0.8f;
    } else {
      motionXZMultiplier = 0.8f;
    }
    float depthStriderMultiplier = Math.min(3.0f, Enchantments.resolveDepthStriderModifier(player));
    if (!environment.lastOnGround()) {
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
    if (!clientData.waterUpdate()) {
      motion.motionY -= 0.02D;
    }
    if (clientData.waterUpdate() && !environment.isSprinting()) {
      if (motion.motionY <= 0.0D
        && Math.abs(motion.motionY - 0.005D) >= 0.003D
        && Math.abs(motion.motionY - gravity / 16.0D) < 0.003D) {
        motion.motionY = -0.003D;
      } else {
        motion.motionY -= gravity / 16.0D;
      }
    }
  }

  private void simulateLavaAfter(
    User user,
    SimulationEnvironment environment,
    Motion context,
    BoundingBox boundingBox,
    boolean collidedHorizontally
  ) {
    double positionY = environment.positionY();
    context.motionX *= 0.5D;
    context.motionY *= 0.5D;
    context.motionZ *= 0.5D;
    context.motionY -= 0.02D;
    boolean offsetPositionInLiquid =
      MovementCharacteristics.isOffsetPositionInLiquid(
        user.player(),
        boundingBox,
        context.motionX,
        context.motionY + 0.6f - positionY + environment.verifiedPositionY(),
        context.motionZ
      );
    if (collidedHorizontally && !offsetPositionInLiquid) {
//      context.motionY = 0.3000001192092896D;
    }
  }

  private void simulateNormalAfter(
    User user,
    SimulationEnvironment environment,
    Motion motion, double gravity, double slipperiness
  ) {
    Player player = user.player();
    if (Effects.levitationEffectActive(player)) {
      int levitationAmplifier = Effects.effectAmplifier(player, Effects.EFFECT_LEVITATION);
      motion.motionY += (0.05D * (double) (levitationAmplifier + 1) - motion.motionY) * 0.2D;
      environment.resetFallDistance();
    } else {
      motion.motionY -= gravity;
    }
    motion.motionX *= slipperiness;
    motion.motionY *= 0.98f;
    motion.motionZ *= slipperiness;
//    motion.multiply(slipperiness, 0.98f, slipperiness);
//    motion.multiplyXZByFactor(slipperiness).multiplyYByFactor(0.98f);
  }

  private void performGlobalEntityPush(User user, SimulationEnvironment environment, Motion context, BoundingBox boundingBox) {
    Collection<Entity> entities = user.meta().connection().tracedEntities(); // .values();
    MovementMetadata movementData = user.meta().movement();
    movementData.pushedByEntity = false;
    for (Entity entity : entities) {
      if (
        !entity.tracingEnabled() ||
        !entity.clientSynchronized ||
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
//    MovementMetadata movementData = user.meta().movement();
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
//    MovementMetadata movement = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();
    //    System.out.println("Past external velocity: " + movement.pastExternalVelocity);
    Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
    int setbackTicks = (environment.pastExternalVelocity() <= 8) ? 8 : ((violationMetadata.physicsVL > 50) ? 3 : 2);
    Modules.mitigate()
      .movement()
      .emulationSetBack(
        user.player(), emulationMotion, setbackTicks, (environment.pastExternalVelocity() > 16)
      );
  }
}
