package de.jpx3.intave.detect.checks.movement.physics.pose;

import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionResult;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementPoseHelper;
import de.jpx3.intave.tools.items.PlayerEnchantmentHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserMetaViolationLevelData;
import de.jpx3.intave.world.BlockAccessor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_VILLAGE_UPDATE;

public class PhysicsNormalPlayerMovement extends PhysicsCalculationPart {
  @Override
  public EntityCollisionResult performSimulation(
    User user, Physics.PhysicsProcessorContext context,
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
    if (movementData.actualSneaking()) {
      strafe = (float) ((double) strafe * 0.3);
      forward = (float) ((double) forward * 0.3);
      if (inWater && clientData.waterUpdate()) {
        context.motionY -= 0.04F;
      }
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
      if (inWater) {
        context.motionY += 0.04F;
      } else if (inLava) {
        // #handleJumpLava
        context.motionY += 0.03999999910593033D;
      } else {
        context.motionY = movementData.jumpUpwardsMotion();
        if (movementData.sprintingAllowed()) {
          context.motionX -= yawSine * 0.2F;
          context.motionZ += yawCosine * 0.2F;
        }
      }
    }
    if (waterUpdate && swimming) {
      double d3 = movementData.lookVector.getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
      boolean fluidStateEmpty = aquaticWaterMovementBase().fluidStateEmpty(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || !fluidStateEmpty) {
        context.motionY += (d3 - context.motionY) * d4;
      }
    }
    if (inWater) {
      performSimulationInWaterOfState(user, context, forward, strafe, yawSine, yawCosine);
    } else if (inLava) {
      performLavaSimulationOfState(context, forward, strafe, yawSine, yawCosine);
    } else {
      performDefaultMoveSimulationOfState(user, context, forward, strafe, yawSine, yawCosine);
    }

    if (!inWater && !elytraFlying && !inLava) {
      tryRelinkFlyingPosition(user, context);
    }

    EntityCollisionResult collisionResult = entityCollisionRepository().resolveEntityCollisionOf(
      user, context, movementData.inWeb,
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return collisionResult;
  }

  private void performSimulationInWaterOfState(
    User user, Physics.PhysicsProcessorContext context,
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
    Physics.PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float friction = 0.02f;
    performRelativeMoveSimulationOfState(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performDefaultMoveSimulationOfState(
    User user, Physics.PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    performRelativeMoveSimulationOfState(context, movementData.friction(), yawSine, yawCosine, moveForward, moveStrafe);
    if (PlayerMovementHelper.isOnLadder(user, movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ)) {
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
    Physics.PhysicsProcessorContext context, float friction,
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

  private void tryRelinkFlyingPosition(User user, Physics.PhysicsProcessorContext context) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onGround;
    Location location = new Location(player.getWorld(), positionX, positionY, positionZ);
    double slipperiness = movementData.lastOnGround ? PlayerMovementHelper.resolveSlipperiness(user, location) : 0.91f;
    double resetMotion = movementData.resetMotion();
    double jumpUpwardsMotion = movementData.jumpUpwardsMotion();

    double interpolations = 0;
    double interpolateX = context.motionX;
    double interpolateY = context.motionY;
    double interpolateZ = context.motionZ;

    for (; interpolations <= 2; interpolations++) {
      CollisionHelper.CollisionResult collisionResult = CollisionHelper.resolveQuickCollisions(
        player, positionX, positionY, positionZ,
        interpolateX, interpolateY, interpolateZ
      );

      positionX += collisionResult.motionX();
      positionY += collisionResult.motionZ();
      positionZ += collisionResult.motionY();

      double diffX = positionX - movementData.verifiedPositionX;
      double diffY = positionY - movementData.verifiedPositionY;
      double diffZ = positionZ - movementData.verifiedPositionZ;
      onGround = collisionResult.onGround();

      boolean jumpLessThanExpected = collisionResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround && Math.abs(((collisionResult.motionY()) + jumpUpwardsMotion) - movementData.motionY()) < 1e-5 && jumpLessThanExpected;

      if (!flyingPacket(diffX, diffY, diffZ) && !jump) {
        break;
      } else if (jump && flyingPacket(diffX * 0.15, 0.0, diffZ * 0.15)) {
        context.motionY = jumpUpwardsMotion;
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
    Player player, Physics.PhysicsProcessorContext context,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    CollisionHelper.CollisionResult result = CollisionHelper.resolveQuickCollisions(player, positionX, positionY, positionZ, motionX, motionY, motionZ);
    context.motionX = result.motionX();
    context.motionY = result.motionY();
    context.motionZ = result.motionZ();
  }

  public void notePossibleFlyingPacket(User user, EntityCollisionResult collisionResult) {
    UserMetaMovementData movementData = user.meta().movementData();
    Physics.PhysicsProcessorContext context = collisionResult.context();
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
    Physics.PhysicsProcessorContext context = movementData.physicsProcessorContext;
    context.reset(motionX, motionY, motionZ);

    boolean elytraFlying = PlayerMovementPoseHelper.flyingWithElytra(player);
    boolean inWater = movementData.inWater;
    boolean inLava = movementData.inLava();
    boolean collidedHorizontally = movementData.collidedHorizontally;
    double gravity = movementData.gravity;
    double slipperiness;
    if (movementData.lastOnGround) {
      double blockPositionX = WrappedMathHelper.floor(movementData.verifiedPositionX);
      double blockPositionY = WrappedMathHelper.floor(movementData.verifiedPositionY - 1.0);
      double blockPositionZ = WrappedMathHelper.floor(movementData.verifiedPositionZ);
      Location blockBelow = new Location(world, blockPositionX, blockPositionY, blockPositionZ);
      slipperiness = PlayerMovementHelper.resolveSlipperiness(user, blockBelow);
    } else {
      slipperiness = 0.91f;
    }

    WrappedAxisAlignedBB boundingBox = CollisionHelper.boundingBoxOf(user, positionX, positionY, positionZ);
    movementData.setBoundingBox(boundingBox);

    if (movementData.inWeb) {
      context.motionX = 0.0;
      context.motionY = 0.0;
      context.motionZ = 0.0;
      movementData.inWeb = false;
    }

    if (movementData.physicsResetMotionX) {
      context.motionX = 0.0;
    }
    if (movementData.physicsResetMotionZ) {
      context.motionZ = 0.0;
    }

    simulateMovementOfCollidedBlocks(user, context, boundingBox);
    updateFallState(user, motionY, movementData.onGround);

    if (inWater) {
      simulateWaterAfter(user, context, boundingBox, collidedHorizontally, gravity);
    } else if (inLava) {
      simulateLavaAfter(player, user, context, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, context, gravity, slipperiness);
    }

    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.physicsMotionX = context.motionX;
      movementData.physicsMotionY = context.motionY;
      movementData.physicsMotionZ = context.motionZ;
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
      if (movementData.artificialFallDistance > 0.0F) {
        float fallDistance = movementData.artificialFallDistance;
        Synchronizer.synchronize(() -> {
          Object playerHandle = user.playerHandle();
          movementData.allowFallDamage = true;
          physics().dealFallDamage(playerHandle, fallDistance);
          movementData.allowFallDamage = false;
        });
        movementData.artificialFallDistance = 0.0F;
      }
    } else if (motionY < 0.0D) {
      movementData.artificialFallDistance += -motionY;
    }
  }

  private void simulateMovementOfCollidedBlocks(
    User user, Physics.PhysicsProcessorContext context,
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
    int blockCollisionPosY = WrappedMathHelper.floor(positionY - 0.20000000298023224D);
    int blockCollisionPosZ = WrappedMathHelper.floor(positionZ);
    Material block = BlockAccessor.cacheAppliedTypeAccess(user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);

    if (block == Material.AIR) {
      Material blockBelow = BlockAccessor.cacheAppliedTypeAccess(user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);
      if (blockBelow.name().contains("FENCE") || blockBelow.name().contains("WALL")) {
        block = blockBelow;
      }
    }

    blockCollisionRepository().fallenUpon(user, block);

    // onLanded
    if (movementData.collidedVertically) {
      Vector collisionVector = blockCollisionRepository().blockLanded(
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
      Vector collisionVector = blockCollisionRepository().entityCollision(
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
          Material material = BlockAccessor.cacheAppliedTypeAccess(user, world, x, y, z);
          Vector collisionVector = blockCollisionRepository().entityCollision(
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

    if (clientData.protocolVersion() >= PROTOCOL_VERSION_VILLAGE_UPDATE) {
      int soulSandModifier = PlayerEnchantmentHelper.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0) {
        Block blockAccess = BlockAccessor.blockAccess(world, positionX, positionY - 0.6, positionZ);
        Material material = blockAccess.getType();
        Vector speedFactor = blockCollisionRepository().speedFactor(user, material, context.motionX, context.motionY, context.motionZ);
        if (speedFactor != null) {
          context.motionX = speedFactor.getX();
          context.motionY = speedFactor.getY();
          context.motionZ = speedFactor.getZ();
        }
      }
    }
  }

  private void simulateWaterAfter(
    User user, Physics.PhysicsProcessorContext context, WrappedAxisAlignedBB entityBoundingBox,
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
    //fixme
//        if (this.isPotionActive(MobEffects.DOLPHINS_GRACE)) {
//          f1 = 0.96F;
//        }
    context.motionX *= f1;
    context.motionY *= 0.8f;
    context.motionZ *= f1;
    if (!clientData.waterUpdate()) {
      context.motionY -= 0.02D;
    }
    if (clientData.waterUpdate() && !movementData.sprinting) {
      if (context.motionY <= 0.0D && Math.abs(context.motionY - 0.005D) >= 0.003D && Math.abs(context.motionY - gravity / 16.0D) < 0.003D) {
        context.motionY = -0.003D;
      } else {
        context.motionY -= gravity / 16.0D;
      }
    }
    double liquidPositionY;
    if (clientData.waterUpdate()) {
      liquidPositionY = context.motionY + 0.6f - positionY + movementData.verifiedPositionY;
    } else {
      liquidPositionY = context.motionY + 0.6f;
    }
    boolean offsetPositionInLiquid = PlayerMovementHelper.isOffsetPositionInLiquid(
      player, entityBoundingBox, context.motionX, liquidPositionY, context.motionZ
    );
    if (collidedHorizontally && offsetPositionInLiquid) {
      context.motionY = 0.3f;
    }
  }

  private void simulateLavaAfter(
    Player player, User user,
    Physics.PhysicsProcessorContext context, WrappedAxisAlignedBB boundingBox,
    boolean collidedHorizontally
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    double positionY = movementData.positionY;
    context.motionX *= 0.5D;
    context.motionY *= 0.5D;
    context.motionZ *= 0.5D;
    context.motionY -= 0.02D;
    boolean offsetPositionInLiquid = PlayerMovementHelper.isOffsetPositionInLiquid(
      player, boundingBox,
      context.motionX,
      context.motionY + 0.6f - positionY + movementData.verifiedPositionY,
      context.motionZ
    );
    if (collidedHorizontally && offsetPositionInLiquid) {
      context.motionY = 0.30000001192092896D;
    }
  }

  private void simulateNormalAfter(User user, Physics.PhysicsProcessorContext context, double gravity, double multiplier) {
    Player player = user.player();
    if (PlayerEffectHelper.isPotionLevitationActive(player)) {
      int levitationAmplifier = PlayerEffectHelper.effectAmplifier(player, PlayerEffectHelper.EFFECT_LEVITATION);
      context.motionY += (0.05D * (double) (levitationAmplifier + 1) - context.motionY) * 0.2D;
    } else {
      context.motionY -= gravity;
    }
    context.motionX *= multiplier;
    context.motionY *= 0.98f;
    context.motionZ *= multiplier;
  }
}