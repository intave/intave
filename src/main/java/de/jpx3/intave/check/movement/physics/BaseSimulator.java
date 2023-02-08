package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.PowderSnowCollisionModifier;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.fluid.LegacyWaterflow;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.Enchantments;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

import static de.jpx3.intave.share.ClientMathHelper.clamp_double;
import static de.jpx3.intave.share.ClientMathHelper.floor;
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
    float forward = configuration.forward();
    float strafe = configuration.strafe();
    boolean handActive = configuration.isHandActive();
    boolean attackReduce = configuration.isReducing();
    boolean jumped = configuration.isJumping();
    boolean sprinting = configuration.isSprinting();

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
    boolean waterUpdate = protocol.waterUpdate();

    forward = ((int) forward) * 0.98f;
    strafe = ((int) strafe) * 0.98f;

    if ((pose == Pose.CROUCHING) || (!protocol.beeUpdate() && environment.isSneaking())) {
      double sneakingModifier = clamp_double(0.3 + Enchantments.resolveSwiftSpeedModifier(user.player()) * 0.15f, 0.0f, 1.0f);
      forward = (float) ((double) forward * sneakingModifier);
      strafe = (float) ((double) strafe * sneakingModifier);
    }
    if (handActive) {
      forward *= 0.2f;
      strafe *= 0.2f;
    }
    if (attackReduce) {
      motion.motionX *= 0.6;
      motion.motionZ *= 0.6;
    }
    if (jumped) {
      boolean allowJumpInWater = false;
      if (protocol.waterUpdate() && inWater && environment.onGround()) {
        Position lastPosition = environment.lastPosition();
        Material material = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), lastPosition);
        float heightPercentage = LegacyWaterflow.resolveLiquidHeightPercentage(levelOfLiquidAt(user, lastPosition));
        heightPercentage += environment.positionY() % 1;
        allowJumpInWater = !MaterialMagic.isWater(material) || heightPercentage > 0.5;
      }
      if (inWater && !allowJumpInWater) {
        motion.motionY += 0.04F;
      } else if (inLava) {
        // #handleJumpLava
        motion.motionY += 0.03999999910593033D;
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
      boolean fluidStateEmpty =
        Fluids.fluidStateEmpty(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || !fluidStateEmpty) {
        motion.motionY += (d3 - motion.motionY) * d4;
      }
    }
    if (inWater) {
      performSimulationInWaterOfState(user, motion, environment, sprinting, forward, strafe, yawSine, yawCosine);
    } else if (inLava) {
      performLavaSimulationOfState(user, motion, forward, strafe, yawSine, yawCosine);
    } else {
      performDefaultMoveSimulationOfState(user, motion, environment, forward, strafe, yawSine, yawCosine);
    }
    if (!inWater && !elytraFlying && !inLava) {
      tryRelinkFlyingPosition(user, motion, environment);
    }
    ColliderResult collisionResult = Colliders.collision(user, motion, environment.inWeb(), positionX, positionY, positionZ);
    notePossibleFlyingPacket(user, collisionResult);
    return Simulation.of(user, configuration, collisionResult);
  }

  private static int levelOfLiquidAt(User user, Position position) {
    Material material = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position);
    if (MaterialMagic.isLiquid(material)) {
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, position);
      return variant == null ? -1 : variant.propertyOf("level");
    } else {
      return -1;
    }
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
    User user,
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
    if (MovementCharacteristics.isOnLadder(
      user,
      environment.verifiedPositionX(),
      environment.verifiedPositionY(),
      environment.verifiedPositionZ())) {
      float f6 = 0.15F;
      context.motionX = ClientMathHelper.clamp_double(context.motionX, -f6, f6);
      context.motionZ = ClientMathHelper.clamp_double(context.motionZ, -f6, f6);
      if (context.motionY < -0.15D) {
        context.motionY = -0.15D;
      }
      if (environment.isSneaking() && context.motionY < 0.0D) {
        context.motionY = 0.0D;
      }
    }
  }

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
          player, positionX, positionY, positionZ, interpolateX, interpolateY, interpolateZ);

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionZ();
      positionZ += colliderResult.motionY();

      double diffX = positionX - environment.verifiedPositionX();
      double diffY = positionY - environment.verifiedPositionY();
      double diffZ = positionZ - environment.verifiedPositionZ();
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump =
        onGround
          && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - environment.motionY())
          < 1e-5
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
          context,
          positionX,
          positionY,
          positionZ,
          nextPredictedX,
          nextPredictedY,
          nextPredictedZ);
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
    Motion motion,
    double positionX,
    double positionY,
    double positionZ,
    double motionX,
    double motionY,
    double motionZ) {
    SimpleColliderResult colliderResult =
      Colliders.simplifiedCollision(
        player, positionX, positionY, positionZ, motionX, motionY, motionZ);
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

  private static final double FLYING_DISTANCE = 0.0009;

  boolean flyingPacket(double diffX, double diffY, double diffZ) {
    double distance = diffX * diffX + diffY * diffY + diffZ * diffZ;
    return distance <= FLYING_DISTANCE;
  }

  private static final Material POWDER_SNOW = MaterialSearch.materialThatIsNamed("POWDER_SNOW");

  @Override
  public void prepareNextTick(
    User user,
    double positionX,
    double positionY,
    double positionZ,
    double motionX,
    double motionY,
    double motionZ
  ) {
    Player player = user.player();
    World world = player.getWorld();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    Motion motionVector = movementData.motionProcessorContext;
    motionVector.reset(motionX, motionY, motionZ);
    Pose pose = movementData.pose();

    if (movementData.motionMultiplier() != null) {
      motionVector.reset(0, 0, 0);
      movementData.resetMotionMultiplier();
    }

    boolean elytraFlying = pose == Pose.FALL_FLYING;
    boolean inWater = movementData.inWater();
    boolean inLava = movementData.inLava();
    boolean collidedHorizontally = movementData.collidedHorizontally;
    double gravity = movementData.gravity();
    double slipperiness;
    if (movementData.lastOnGround()) {
      double blockPositionX = floor(movementData.verifiedPositionX);
      double blockPositionY =
        floor(movementData.verifiedPositionY - movementData.frictionPosSubtraction());
      double blockPositionZ = floor(movementData.verifiedPositionZ);
      slipperiness =
        MovementCharacteristics.currentSlipperiness(
          user, world, blockPositionX, blockPositionY, blockPositionZ);
    } else {
      slipperiness = 0.91f;
    }

    BoundingBox boundingBox = BoundingBox.fromPosition(user, positionX, positionY, positionZ);
    movementData.setBoundingBox(boundingBox);

    if (movementData.inWeb) {
      motionVector.motionX = 0.0;
      motionVector.motionY = 0.0;
      motionVector.motionZ = 0.0;
      movementData.inWeb = false;
    }

    if (movementData.physicsResetMotionX) {
      motionVector.motionX = 0.0;
    }
    if (movementData.physicsResetMotionZ) {
      motionVector.motionZ = 0.0;
    }

    simulateMovementOfCollidedBlocks(user, motionVector, boundingBox);
    updateFallState(user, motionY, movementData.onGround);

    if (inWater) {
      simulateWaterAfter(user, motionVector, boundingBox, collidedHorizontally, gravity);
    } else if (inLava) {
      simulateLavaAfter(player, user, motionVector, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, motionVector, gravity, slipperiness);
    }

    if (clientData.combatUpdate()
      && MinecraftVersions.VER1_9_0.atOrAbove() /* todo: add scoreboard check */) {
      performGlobalEntityPush(user, motionVector, boundingBox);
    }

    if (!violationLevelData.isInActiveTeleportBundle) {
      if (violationLevelData.ignorePostTickMotionReset) {
        violationLevelData.ignorePostTickMotionReset = false;
        //        player.sendMessage("Ignore physics motion reset " + motionVector);
      } else {
        movementData.baseMotionX = motionVector.motionX;
        movementData.baseMotionY = motionVector.motionY;
        movementData.baseMotionZ = motionVector.motionZ;
      }
    }

    movementData.increaseFlyingPacket();
    movementData.pastEntityUse++;
    if (movementData.pastPlayerAttackPhysics < 100) {
      movementData.pastPlayerAttackPhysics++;
    }
    if (movementData.pastPushedByWaterFlow < 100) {
      movementData.pastPushedByWaterFlow++;
    }

    if (movementData.onGround) {
      movementData.physicsPacketRelinkFlyVL = 0;
    }

    Material type = VolatileBlockAccess.typeAccess(user, player.getWorld(), positionX, positionY, positionZ);
    boolean climbingInPowderSnow = POWDER_SNOW != null && type == POWDER_SNOW && PowderSnowCollisionModifier.canWalkOnPowderSnow(player);
    if (climbingInPowderSnow) {
      movementData.pastInPowderSnow = 0;
    } else if (movementData.pastInPowderSnow < 100) {
      movementData.pastInPowderSnow++;
    }

    if (movementData.pastEdgeSneakTickGrants < 100) {
      movementData.pastEdgeSneakTickGrants++;
    }
  }

  private void updateFallState(User user, double motionY, boolean onGround) {
    MovementMetadata movementData = user.meta().movement();
    if (!movementData.inWater) {
      physics().updateAquatics(user);
    }
    if (onGround) {
      physics().applyFallDamageUpdate(user);
      movementData.artificialFallDistance = 0;
    } else if (motionY < 0.0D) {
      movementData.artificialFallDistance += -motionY;
    }
  }

  private void simulateMovementOfCollidedBlocks(
    User user, Motion motion, BoundingBox entityBoundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();

    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;

    int blockCollisionPosX = floor(positionX);
    int blockCollisionPosY = floor(positionY - 0.2f);
    int blockCollisionPosZ = floor(positionZ);
    Material block =
      VolatileBlockAccess.typeAccess(
        user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);

    if (block == Material.AIR) {
      Material blockBelow =
        VolatileBlockAccess.typeAccess(
          user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);
      if (blockBelow.name().contains("FENCE") || blockBelow.name().contains("WALL")) {
        block = blockBelow;
      }
    }

    BlockPhysics.fallenUpon(user, block);

    // onLanded
    if (movementData.collidedVertically) {
      Motion collisionVector =
        BlockPhysics.blockLanded(
          user, block, motion.motionX, movementData.baseMotionY, motion.motionZ);
      if (collisionVector != null) {
        motion.resetTo(collisionVector);
      } else {
        motion.motionY = 0.0;
      }
    }

    // EntityCollidedWithBlock
    if (movementData.onGround && !movementData.sneaking) {
      Motion collisionVector =
        BlockPhysics.entityCollision(user, block, motion.motionX, motion.motionY, motion.motionZ);
      if (collisionVector != null) {
        motion.resetTo(collisionVector);
      }
    }

    // Block collisions

    movementData.aquaticUpdateInLava = false;

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
            motion.resetTo(collisionMotion);
          }
        }
      }
    }

    if (clientData.protocolVersion() >= VER_1_14 && movementData.pose() != Pose.FALL_FLYING) {
      int soulSandModifier = Enchantments.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0 || !movementData.blockOnPositionSoulSpeedAffected()) {
        Material type = VolatileBlockAccess.typeAccess(
          user, world, positionX, positionY - 0.5000001, positionZ
        );
        float speedFactor = BlockProperties.of(type).speedFactor();
        motion.motionX *= speedFactor;
        motion.motionZ *= speedFactor;
      }
    }
  }

  private void simulateWaterAfter(
    User user,
    Motion motionVector,
    BoundingBox entityBoundingBox,
    boolean collidedHorizontally,
    double gravity) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    double positionY = movementData.positionY;
    float f1;
    if (clientData.waterUpdate()) {
      f1 = movementData.sprinting ? 0.9f : 0.8f;
    } else {
      f1 = 0.8f;
    }
    float f3 = Math.min(3.0f, Enchantments.resolveDepthStriderModifier(player));
    if (!movementData.lastOnGround) {
      f3 *= 0.5F;
    }
    if (f3 > 0.0F) {
      f1 += (0.54600006F - f1) * f3 / 3.0F;
    }
    if (Effects.dolphinEffectActive(player)) {
      f1 = 0.96F;
    }
    motionVector.motionX *= f1;
    motionVector.motionY *= 0.8f;
    motionVector.motionZ *= f1;
    if (!clientData.waterUpdate()) {
      motionVector.motionY -= 0.02D;
    }
    if (clientData.waterUpdate() && !movementData.sprinting) {
      if (motionVector.motionY <= 0.0D
        && Math.abs(motionVector.motionY - 0.005D) >= 0.003D
        && Math.abs(motionVector.motionY - gravity / 16.0D) < 0.003D) {
        motionVector.motionY = -0.003D;
      } else {
        motionVector.motionY -= gravity / 16.0D;
      }
    }
    //    if (movementData.collidedHorizontally &&
    // MovementContextHelper.isOffsetPositionInLiquid(player, entityBoundingBox, x, y, z)) {
    //      motionVector.motionY = 0.3;
    //    }
  }

  private void simulateLavaAfter(
    Player player,
    User user,
    Motion context,
    BoundingBox boundingBox,
    boolean collidedHorizontally
  ) {
    MovementMetadata movementData = user.meta().movement();
    double positionY = movementData.positionY;
    context.motionX *= 0.5D;
    context.motionY *= 0.5D;
    context.motionZ *= 0.5D;
    context.motionY -= 0.02D;
    boolean offsetPositionInLiquid =
      MovementCharacteristics.isOffsetPositionInLiquid(
        player,
        boundingBox,
        context.motionX,
        context.motionY + 0.6f - positionY + movementData.verifiedPositionY,
        context.motionZ
      );
    if (collidedHorizontally && offsetPositionInLiquid) {
      context.motionY = 0.30000001192092896D;
    }
  }

  private void simulateNormalAfter(User user, Motion context, double gravity, double slipperiness) {
    Player player = user.player();
    if (Effects.levitationEffectActive(player)) {
      int levitationAmplifier = Effects.effectAmplifier(player, Effects.EFFECT_LEVITATION);
      context.motionY += (0.05D * (double) (levitationAmplifier + 1) - context.motionY) * 0.2D;
      user.meta().movement().artificialFallDistance = 0f;
    } else {
      context.motionY -= gravity;
    }
    context.motionX *= slipperiness;
    context.motionY *= 0.98f;
    context.motionZ *= slipperiness;
  }

  private void performGlobalEntityPush(User user, Motion context, BoundingBox boundingBox) {
    Collection<Entity> entities = user.meta().connection().tracedEntities(); // .values();
    MovementMetadata movementData = user.meta().movement();
    movementData.pushedByEntity = false;
    for (Entity entity : entities) {
      if (!entity.tracingEnabled() || !entity.clientSynchronized) {
        continue;
      }
      if (entity.entityBoundingBox().intersectsWith(boundingBox)) {
        applyEntityPush(user, context, entity);
      }
    }
  }

  private void applyEntityPush(User user, Motion motionVector, Entity entity) {
    MovementMetadata movementData = user.meta().movement();
    double xDistance = movementData.positionX - entity.position.posX;
    double zDistance = movementData.positionZ - entity.position.posZ;
    double biggerDistance = ClientMathHelper.abs_max(xDistance, zDistance);
    if (biggerDistance >= (double) 0.01F) {
      biggerDistance = ClientMathHelper.sqrt_double(biggerDistance);
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
      if (!movementData.isInVehicle()) {
        movementData.pushedByEntity = true;
        motionVector.motionX += xDistance;
        motionVector.motionZ += zDistance;
      }
    }
  }

  @Override
  public void setback(User user, double predictedX, double predictedY, double predictedZ) {
    MovementMetadata movement = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();
    //    System.out.println("Past external velocity: " + movement.pastExternalVelocity);
    Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
    int setbackTicks = (movement.pastExternalVelocity <= 8) ? 8 : ((violationMetadata.physicsVL > 50) ? 3 : 2);
    Modules.mitigate()
      .movement()
      .emulationSetBack(
        user.player(), emulationMotion, setbackTicks, (movement.pastExternalVelocity > 16)
      );
  }
}
