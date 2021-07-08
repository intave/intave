package de.jpx3.intave.detect.checks.movement.physics.simulator;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.detect.checks.movement.physics.PoseSimulator;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.tools.client.EffectLogic;
import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.tools.client.MovementContext;
import de.jpx3.intave.tools.items.PlayerEnchantmentHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserMetaViolationLevelData;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockphysic.BlockPhysics;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;
import de.jpx3.intave.world.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.world.fluid.Fluids;
import de.jpx3.intave.world.fluid.LegacyWaterflow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

import static de.jpx3.intave.user.UserMetaClientData.VER_1_14;

public class DefaultPoseSimulator extends PoseSimulator {
  @Override
  public ComplexColliderSimulationResult performSimulation(
    User user, MotionVector context,
    float forward, float strafe,
    boolean attackReduce, boolean jumped, boolean handActive
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    float yawSine = movementData.yawSine();
    float yawCosine = movementData.yawCosine();
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    boolean inWater = movementData.inWater;
    boolean elytraFlying = movementData.elytraFlying;
    boolean inLava = movementData.inLava();
    boolean swimming = movementData.swimming;
    boolean waterUpdate = clientData.waterUpdate();
    boolean ignoreSneakingInput = swimming && clientData.beeUpdate();
    if (movementData.actualSneaking() && !ignoreSneakingInput) {
      strafe = (float) ((double) strafe * 0.3);
      forward = (float) ((double) forward * 0.3);
    }
    if (handActive) {
      strafe *= 0.2f;
      forward *= 0.2f;
    }
    if (attackReduce) {
      context.motionX *= 0.6;
      context.motionZ *= 0.6;
    }
    if (jumped) {
      boolean allowJumpInWater = false;
      if (clientData.waterUpdate() && inWater) {
        // Geht nicht anders
        Material material = BukkitBlockAccess.cacheAppliedTypeAccess(
          user, user.player().getWorld(),
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ
        );
        int blockData = BukkitBlockAccess.cacheAppliedDataAccess(
          user, user.player().getWorld(),
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ
        );
        float heightPercentage = LegacyWaterflow.resolveLiquidHeightPercentage(blockData);
        if (movementData.onGround) {
          heightPercentage += movementData.positionY % 1;
          allowJumpInWater = !Materials.isWater(material) || heightPercentage > 0.5;
        }
      }
      if (inWater && !allowJumpInWater) {
        context.motionY += 0.04F;
      } else if (inLava) {
        // #handleJumpLava
        context.motionY += 0.03999999910593033D;
      } else {
        context.motionY = movementData.jumpMotion();
        if (movementData.sprintingAllowed()) {
          context.motionX -= yawSine * 0.2F;
          context.motionZ += yawCosine * 0.2F;
        }
      }
    }
    if (waterUpdate && swimming) {
      double d3 = movementData.lookVector.getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
      boolean fluidStateEmpty = Fluids.fluidStateEmpty(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || !fluidStateEmpty) {
        context.motionY += (d3 - context.motionY) * d4;
      }
    }
    if (inWater) {
      performSimulationInWaterOfState(user, context, forward, strafe, yawSine, yawCosine);
    } else if (inLava) {
      performLavaSimulationOfState(user, context, forward, strafe, yawSine, yawCosine);
    } else {
      performDefaultMoveSimulationOfState(user, context, forward, strafe, yawSine, yawCosine);
    }

    if (!inWater && !elytraFlying && !inLava) {
      tryRelinkFlyingPosition(user, context);
    }

    Vector motionMultiplier = movementData.motionMultiplier();
    if (motionMultiplier != null) {
      context.motionX *= motionMultiplier.getX();
      context.motionY *= motionMultiplier.getY();
      context.motionZ *= motionMultiplier.getZ();
      movementData.physicsMotionX = 0;
      movementData.physicsMotionY = 0;
      movementData.physicsMotionZ = 0;
    }

    ComplexColliderSimulationResult collisionResult = Collider.simulateComplexCollision(
      user, context, movementData.inWeb,
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return collisionResult;
  }

  private void performSimulationInWaterOfState(
    User user, MotionVector context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();
    float f2 = 0.02F;
    float f3 = (float) PlayerEnchantmentHelper.resolveDepthStriderModifier(player);
    if (f3 > 3.0F) {
      f3 = 3.0F;
    }
    if (!movementData.lastOnGround) {
      f3 *= 0.5F;
    }
    if (f3 > 0.0F) {
      f2 += (movementData.aiMoveSpeed() - f2) * f3 / 3.0F;
    }
    performRelativeMoveSimulationOfState(context, f2, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performLavaSimulationOfState(
    User user,
    MotionVector context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float friction = 0.02f;
    performRelativeMoveSimulationOfState(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performDefaultMoveSimulationOfState(
    User user, MotionVector context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    performRelativeMoveSimulationOfState(context, movementData.friction(), yawSine, yawCosine, moveForward, moveStrafe);
    if (MovementContext.isOnLadder(user, movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ)) {
      float f6 = 0.15F;
      context.motionX = WrappedMathHelper.clamp_double(context.motionX, -f6, f6);
      context.motionZ = WrappedMathHelper.clamp_double(context.motionZ, -f6, f6);
      if (context.motionY < -0.15D) {
        context.motionY = -0.15D;
      }
      if (movementData.sneaking && context.motionY < 0.0D) {
        context.motionY = 0.0D;
      }
    }
  }

  private void performRelativeMoveSimulationOfState(
    MotionVector context, float friction,
    float yawSine, float yawCosine,
    float moveForward, float moveStrafe
  ) {
    float f = moveStrafe * moveStrafe + moveForward * moveForward;
    if (f >= 1.0E-4F) {
      f = (float) Math.sqrt(f);
      f = friction / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      context.motionX += moveStrafe * yawCosine - moveForward * yawSine;
      context.motionZ += moveForward * yawCosine + moveStrafe * yawSine;
    }
  }

  private void tryRelinkFlyingPosition(User user, MotionVector context) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onGround;
    Location location = new Location(player.getWorld(), positionX, positionY, positionZ);
    double slipperiness = movementData.lastOnGround ? MovementContext.currentSlipperiness(user, location) : 0.91f;
    double resetMotion = movementData.resetMotion();
    double jumpUpwardsMotion = movementData.jumpMotion();

    double interpolations = 0;
    double interpolateX = context.motionX;
    double interpolateY = context.motionY;
    double interpolateZ = context.motionZ;

    for (; interpolations <= 2; interpolations++) {
      SimpleColliderSimulationResult colliderResult = Collider.simulateSimpleCollision(
        player, positionX, positionY, positionZ,
        interpolateX, interpolateY, interpolateZ
      );

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionZ();
      positionZ += colliderResult.motionY();

      double diffX = positionX - movementData.verifiedPositionX;
      double diffY = positionY - movementData.verifiedPositionY;
      double diffZ = positionZ - movementData.verifiedPositionZ;
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - movementData.motionY()) < 1e-5 && jumpLessThanExpected;

      if (!flyingPacket(diffX, diffY, diffZ) && !jump) {
        break;
      } else if (jump && flyingPacket(diffX * 0.15, 0.0, diffZ * 0.15) && !movementData.denyJump()) {
        context.motionY = jumpUpwardsMotion;
        movementData.artificialFallDistance = 0f;
        movementData.physicsPacketRelinkFlyVL = 0;
        break;
      } else if (movementData.motionY() < 0) {
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
          player, context,
          positionX, positionY, positionZ,
          nextPredictedX, nextPredictedY, nextPredictedZ
        );
      }

      interpolateX *= slipperiness;
      interpolateY -= movementData.gravity;
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

  private void applyCollidedMotionsToContext(
    Player player, MotionVector context,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    SimpleColliderSimulationResult colliderResult = Collider.simulateSimpleCollision(player, positionX, positionY, positionZ, motionX, motionY, motionZ);
    context.motionX = colliderResult.motionX();
    context.motionY = colliderResult.motionY();
    context.motionZ = colliderResult.motionZ();
  }

  public void notePossibleFlyingPacket(User user, ComplexColliderSimulationResult collisionResult) {
    UserMetaMovementData movementData = user.meta().movementData();
    MotionVector context = collisionResult.context();
    if (flyingPacket(context.motionX, context.motionY, context.motionZ)) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  private final static double FLYING_DISTANCE = 0.0009;

  private boolean flyingPacket(double diffX, double diffY, double diffZ) {
    double distance = diffX * diffX + diffY * diffY + diffZ * diffZ;
    return distance <= FLYING_DISTANCE;
  }

  @Override
  public void prepareNextTick(
    User user,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    Player player = user.player();
    World world = player.getWorld();
    User.UserMeta meta = user.meta();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    MotionVector motionVector = movementData.motionProcessorContext;
    motionVector.reset(motionX, motionY, motionZ);

    boolean elytraFlying = movementData.elytraFlying;//PoseHelper.flyingWithElytra(player);
    boolean inWater = movementData.inWater;
    boolean inLava = movementData.inLava();
    boolean collidedHorizontally = movementData.collidedHorizontally;
    double gravity = movementData.gravity;
    double slipperiness;
    if (movementData.lastOnGround) {
      double blockPositionX = WrappedMathHelper.floor(movementData.verifiedPositionX);
      double blockPositionY = WrappedMathHelper.floor(movementData.verifiedPositionY - movementData.frictionPosSubtraction());
      double blockPositionZ = WrappedMathHelper.floor(movementData.verifiedPositionZ);
      Location blockBelow = new Location(world, blockPositionX, blockPositionY, blockPositionZ);
      slipperiness = MovementContext.currentSlipperiness(user, blockBelow);
    } else {
      slipperiness = 0.91f;
    }

    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(user, positionX, positionY, positionZ);
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

    movementData.resetMotionMultiplier();
    simulateMovementOfCollidedBlocks(user, motionVector, boundingBox);
    updateFallState(user, motionY, movementData.onGround);

    if (inWater) {
      simulateWaterAfter(user, motionVector, boundingBox, collidedHorizontally, gravity);
    } else if (inLava) {
      simulateLavaAfter(player, user, motionVector, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, motionVector, gravity, slipperiness);
    }

    if (clientData.combatUpdate() && MinecraftVersions.VER1_9_0.atOrAbove() /* todo: add scoreboard check */) {
      performGlobalEntityPush(user, motionVector, boundingBox);
    }

    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.physicsMotionX = motionVector.motionX;
      movementData.physicsMotionY = motionVector.motionY;
      movementData.physicsMotionZ = motionVector.motionZ;
    }
    movementData.increaseFlyingPacket();
    movementData.pastPlayerAttackPhysics++;
    movementData.pastPushedByWaterFlow++;

    if (movementData.onGround) {
      movementData.physicsPacketRelinkFlyVL = 0;
    }
  }

  private void updateFallState(User user, double motionY, boolean onGround) {
    UserMetaMovementData movementData = user.meta().movementData();
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
    User user, MotionVector context,
    WrappedAxisAlignedBB entityBoundingBox
  ) {
    Player player = user.player();
    World world = player.getWorld();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;

    int blockCollisionPosX = WrappedMathHelper.floor(positionX);
    int blockCollisionPosY = WrappedMathHelper.floor(positionY - 0.2f);
    int blockCollisionPosZ = WrappedMathHelper.floor(positionZ);
    Material block = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);

    if (block == Material.AIR) {
      Material blockBelow = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);
      if (blockBelow.name().contains("FENCE") || blockBelow.name().contains("WALL")) {
        block = blockBelow;
      }
    }

    BlockPhysics.fallenUpon(user, block);

    // onLanded
    if (movementData.collidedVertically) {
      Vector collisionVector = BlockPhysics.blockLanded(
        user, block,
        context.motionX, movementData.physicsMotionY, context.motionZ
      );
      if (collisionVector != null) {
        context.motionX = collisionVector.getX();
        context.motionY = collisionVector.getY();
        context.motionZ = collisionVector.getZ();
      } else {
        context.motionY = 0.0;
      }
    }

    // EntityCollidedWithBlock
    if (movementData.onGround && !movementData.sneaking) {
      Vector collisionVector = BlockPhysics.entityCollision(
        user, block,
        context.motionX, context.motionY, context.motionZ
      );
      if (collisionVector != null) {
        context.motionX = collisionVector.getX();
        context.motionY = collisionVector.getY();
        context.motionZ = collisionVector.getZ();
      }
    }

    // Block collisions

    movementData.aquaticUpdateInLava = false;

    int blockPositionStartX = WrappedMathHelper.floor(entityBoundingBox.minX + 0.001);
    int blockPositionStartY = WrappedMathHelper.floor(entityBoundingBox.minY + 0.001);
    int blockPositionStartZ = WrappedMathHelper.floor(entityBoundingBox.minZ + 0.001);
    int blockPositionEndX = WrappedMathHelper.floor(entityBoundingBox.maxX - 0.001);
    int blockPositionEndY = WrappedMathHelper.floor(entityBoundingBox.maxY - 0.001);
    int blockPositionEndZ = WrappedMathHelper.floor(entityBoundingBox.maxZ - 0.001);

    Location blockCollisionFrom = new Location(world, positionX, positionY, positionZ);
    for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
      for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
        for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
          Location location = new Location(world, x, y, z);
          Material material = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z);
          Vector collisionVector = BlockPhysics.entityCollision(
            user, material,
            location,
            blockCollisionFrom,
            context.motionX, context.motionY, context.motionZ
          );
          if (collisionVector != null) {
            context.motionX = collisionVector.getX();
            context.motionY = collisionVector.getY();
            context.motionZ = collisionVector.getZ();
          }
        }
      }
    }

    if (clientData.protocolVersion() >= VER_1_14) {
      int soulSandModifier = PlayerEnchantmentHelper.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0) {
        Block blockAccess = BukkitBlockAccess.blockAccess(world, positionX, positionY - 0.5000001, positionZ);
        Material material = blockAccess.getType();
        float speedFactor = BlockPhysics.speedFactor(user, material);
        context.motionX *= speedFactor;
        context.motionZ *= speedFactor;
      }
    }
  }

  private void simulateWaterAfter(
    User user, MotionVector motionVector, WrappedAxisAlignedBB entityBoundingBox,
    boolean collidedHorizontally, double gravity
  ) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    double positionY = movementData.positionY;
    float f1;
    if (clientData.waterUpdate()) {
      f1 = movementData.sprinting ? 0.9f : 0.8f;
    } else {
      f1 = 0.8f;
    }
    float f3 = Math.min(3.0f, (float) PlayerEnchantmentHelper.resolveDepthStriderModifier(player));
    if (!movementData.lastOnGround) {
      f3 *= 0.5F;
    }
    if (f3 > 0.0F) {
      f1 += (0.54600006F - f1) * f3 / 3.0F;
    }
    if (EffectLogic.isPotionDolphinActive(player)) {
      f1 = 0.96F;
    }
    motionVector.motionX *= f1;
    motionVector.motionY *= 0.8f;
    motionVector.motionZ *= f1;
    if (!clientData.waterUpdate()) {
      motionVector.motionY -= 0.02D;
    }
    if (clientData.waterUpdate() && !movementData.sprinting) {
      if (motionVector.motionY <= 0.0D && Math.abs(motionVector.motionY - 0.005D) >= 0.003D && Math.abs(motionVector.motionY - gravity / 16.0D) < 0.003D) {
        motionVector.motionY = -0.003D;
      } else {
        motionVector.motionY -= gravity / 16.0D;
      }
    }
//    if (movementData.collidedHorizontally && MovementContextHelper.isOffsetPositionInLiquid(player, entityBoundingBox, x, y, z)) {
//      motionVector.motionY = 0.3;
//    }
  }

  private void simulateLavaAfter(
    Player player, User user,
    MotionVector context, WrappedAxisAlignedBB boundingBox,
    boolean collidedHorizontally
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    double positionY = movementData.positionY;
    context.motionX *= 0.5D;
    context.motionY *= 0.5D;
    context.motionZ *= 0.5D;
    context.motionY -= 0.02D;
    boolean offsetPositionInLiquid = MovementContext.isOffsetPositionInLiquid(
      player, boundingBox,
      context.motionX,
      context.motionY + 0.6f - positionY + movementData.verifiedPositionY,
      context.motionZ
    );
    if (collidedHorizontally && offsetPositionInLiquid) {
      context.motionY = 0.30000001192092896D;
    }
  }

  private void simulateNormalAfter(User user, MotionVector context, double gravity, double multiplier) {
    Player player = user.player();
    if (EffectLogic.isPotionLevitationActive(player)) {
      int levitationAmplifier = EffectLogic.effectAmplifier(player, EffectLogic.EFFECT_LEVITATION);
      context.motionY += (0.05D * (double) (levitationAmplifier + 1) - context.motionY) * 0.2D;
      user.meta().movementData().artificialFallDistance = 0f;
    } else {
      context.motionY -= gravity;
    }
    context.motionX *= multiplier;
    context.motionY *= 0.98f;
    context.motionZ *= multiplier;
  }

  private void performGlobalEntityPush(User user, MotionVector context, WrappedAxisAlignedBB boundingBox) {
    Collection<WrappedEntity> entities = user.meta().connectionData().synchronizedEntityMap().values();
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.pushedByEntity = false;
    for (WrappedEntity entity : entities) {
      if (!entity.tracingEnabled() || !entity.clientSynchronized) {
        continue;
      }
      if (entity.entityBoundingBox().intersectsWith(boundingBox)) {
        applyEntityPush(user, context, entity);
      }
    }
  }

  private void applyEntityPush(User user, MotionVector motionVector, WrappedEntity entity) {
    UserMetaMovementData movementData = user.meta().movementData();
    double xDistance = movementData.positionX - entity.position.posX;
    double zDistance = movementData.positionZ - entity.position.posZ;
    double biggerDistance = WrappedMathHelper.abs_max(xDistance, zDistance);
    if (biggerDistance >= (double) 0.01F) {
      biggerDistance = WrappedMathHelper.sqrt_double(biggerDistance);
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
      if (!movementData.hasRidingEntity()) {
        movementData.pushedByEntity = true;
        motionVector.motionX += xDistance;
        motionVector.motionZ += zDistance;
      }
    }
  }
}