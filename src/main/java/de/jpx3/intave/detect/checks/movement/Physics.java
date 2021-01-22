package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.detect.checks.movement.physics.collision.block.BlockCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionResult;
import de.jpx3.intave.detect.checks.movement.physics.water.*;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementPoseHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.items.PlayerEnchantmentHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

import static de.jpx3.intave.detect.checks.movement.physics.PhysicsHelper.resolveKeysFromInput;
import static de.jpx3.intave.tools.MathHelper.formatDouble;
import static de.jpx3.intave.tools.MathHelper.formatPosition;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_AQUATIC_UPDATE;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_VILLAGE_UPDATE;

public final class Physics extends IntaveCheck {
  private final static double VL_DECREMENT_PER_VALID_MOVE = 0.05;
  private final static boolean SIMULATE_USE_ITEM_TWICE = true;

  private final IntavePlugin plugin;
  private final BlockCollisionRepository blockCollisionRepository;
  private final EntityCollisionRepository entityCollisionRepository;

  private WaterMovementLegacyResolver waterMovementLegacyResolver;
  private AquaticWaterMovementBase aquaticWaterMovement;

  private final CheckViolationLevelDecrementer decrementer;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
    this.blockCollisionRepository = new BlockCollisionRepository();
    this.entityCollisionRepository = new EntityCollisionRepository();
    this.decrementer = new CheckViolationLevelDecrementer(this, VL_DECREMENT_PER_VALID_MOVE * 20);
    this.entityCollisionRepository.setup();
    setupWaterMovement();
  }

  private void setupWaterMovement() {
    setupLegacyMovement();
    setupAquaticMovement();
  }

  private void setupLegacyMovement() {
    this.waterMovementLegacyResolver = new WaterMovementLegacyResolver();
  }

  private void setupAquaticMovement() {
    MinecraftVersion minecraftVersion = ProtocolLibAdapter.serverVersion();
    if (minecraftVersion.isAtLeast(ProtocolLibAdapter.NETHER_UPDATE)) {
      this.aquaticWaterMovement = new AquaticNetherUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.BEE_UPDATE)) {
      this.aquaticWaterMovement = new AquaticBeeUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.VILLAGE_UPDATE)) {
      this.aquaticWaterMovement = new AquaticVillageUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.AQUATIC_UPDATE)) {
      this.aquaticWaterMovement = new AquaticAquaticUpdateMovementResolver();
    } else {
      this.aquaticWaterMovement = new AquaticUnknownMovementResolver(waterMovementLegacyResolver);
    }
  }

  public void receiveMovement(User user, boolean hasMovement) {
    UserMetaMovementData movementData = user.meta().movementData();
    double motionX = movementData.motionX();
    double motionY = movementData.motionY();
    double motionZ = movementData.motionZ();
    if (hasMovement) {
      processMovement(user, motionX, motionY, motionZ);
    }
  }

  public void endMovement(User user, boolean hasMovement) {
    UserMetaMovementData movementData = user.meta().movementData();
    double motionX = movementData.motionX();
    double motionY = movementData.motionY();
    double motionZ = movementData.motionZ();
    if (hasMovement) {
      prepareNextTick(
        user,
        movementData.positionX, movementData.positionY, movementData.positionZ,
        motionX, motionY, motionZ
      );
    }
  }

  private void processMovement(User user, double receivedMotionX, double receivedMotionY, double receivedMotionZ) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    float friction = resolveFriction(player, movementData, positionX, positionY, positionZ);
    boolean sprinting = movementData.sprinting;
    boolean sneaking;
    if (clientData.delayedSneak()) {
      sneaking = movementData.lastSneaking;
    } else if (clientData.alternativeSneak()) {
      sneaking = movementData.lastSneaking || movementData.sneaking;
    } else {
      sneaking = movementData.sneaking;
    }
    if (inventoryData.inventoryOpen()) {
      sprinting = false;
    }
    simulateMotionClamp(user);
    float rotationYaw = movementData.rotationYaw;
    float yawSine = SinusCache.sin(rotationYaw * (float) Math.PI / 180.0F, false);
    float yawCosine = SinusCache.cos(rotationYaw * (float) Math.PI / 180.0F, false);

    /*
    Physics process
     */
    EntityCollisionResult predictedMovement;
    predictedMovement = simulateMovementBiased(user, friction, sprinting, sneaking, yawSine, yawCosine);
    PhysicsProcessorContext context = predictedMovement.context();
    double distance = MathHelper.resolveDistance(
      context.motionX, context.motionY, context.motionZ,
      receivedMotionX, receivedMotionY, receivedMotionZ
    );
    if (distance > 0.001) {
      predictedMovement = simulatePossibleMovements(
        user, friction, sprinting, sneaking, yawSine, yawCosine,
        receivedMotionX, receivedMotionY, receivedMotionZ
      );
    }

/*   context = predictedMovement.context();
    distance = MathHelper.resolveDistance(
      context.motionX, context.motionY, context.motionZ,
      receivedMotionX, receivedMotionY, receivedMotionZ
    );

    if (distance > 0.005 && movementData.pastVelocity == 1 && movementData.lastVelocity != null) {
      double actualMotionX = movementData.physicsLastMotionX;
      double actualMotionY = movementData.physicsLastMotionY;
      double actualMotionZ = movementData.physicsLastMotionZ;
      movementData.physicsLastMotionX = movementData.lastVelocity.getX();
      movementData.physicsLastMotionY = movementData.lastVelocity.getY();
      movementData.physicsLastMotionZ = movementData.lastVelocity.getZ();


      double distanceBefore = distance;

      if (distance < distanceBefore) {
        predictedMovement = simulatePossibleMovements(
          user, friction, sprinting, sneaking, yawSine, yawCosine,
          receivedMotionX, receivedMotionY, receivedMotionZ
        );
        distance = MathHelper.resolveDistance(
          context.motionX, context.motionY, context.motionZ,
          receivedMotionX, receivedMotionY, receivedMotionZ
        );
        context = predictedMovement.context();
      }
      if (distance < 0.001) {
        player.sendMessage(ChatColor.DARK_RED + "Velocity was delayed! " + distance + "; before=" + distanceBefore +
                             ": pastVelocity=" + movementData.pastVelocity);
      }

      movementData.physicsLastMotionX = actualMotionX;
      movementData.physicsLastMotionY = actualMotionY;
      movementData.physicsLastMotionZ = actualMotionZ;
    }*/

    evaluateBestSimulation(user, predictedMovement);
    movementData.onGround = predictedMovement.onGround();
    movementData.collidedHorizontally = predictedMovement.collidedHorizontally();
    movementData.collidedVertically = predictedMovement.collidedVertically();
    movementData.physicsResetMotionX = predictedMovement.resetMotionX();
    movementData.physicsResetMotionZ = predictedMovement.resetMotionZ();
    movementData.pastRiptideSpin++;
  }

  private EntityCollisionResult simulatePossibleMovements(
    User user, float friction,
    boolean sprinting, boolean sneaking,
    float yawSine, float yawCosine,
    double receivedMotionX, double receivedMotionY, double receivedMotionZ
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    double lastMotionX = movementData.physicsLastMotionX;
    double lastMotionY = movementData.physicsLastMotionY;
    double lastMotionZ = movementData.physicsLastMotionZ;
    boolean lenientItemUsageChecking = lenientItemUsageChecking(user);
    boolean inventoryOpen = inventoryData.inventoryOpen();
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    boolean elytraFlying = movementData.elytraFlying;
    int bestForwardKey = 0;
    int bestStrafeKey = 0;
    double mostAccurateDistance = Integer.MAX_VALUE;
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
    EntityCollisionResult predictedMovement = null;

    LOOP:
    for (int heldItemState = 0; heldItemState <= 1; heldItemState++) {
      boolean handActive = heldItemState == 1;

      if (!SIMULATE_USE_ITEM_TWICE) {
        if (!lenientItemUsageChecking) {
          // Force the player to accept the item usage
          int blockLenience = inventoryData.pastItemUsageTransition < 5 && inventoryData.pastHotBarSlotChange < 5 ? 5 : 0;
          if (inventoryData.handActiveTicks >= blockLenience && inventoryData.handActive() && !handActive) {
            continue;
          }
          // Remove the ability to accept the item usage
          if (!inventoryData.handActive() && handActive) {
            continue;
          }
        }
      }

      for (int attackState = 0; attackState <= 1; attackState++) {
        boolean attackReduce = attackState == 1;
        if (attackReduce && movementData.pastPlayerAttackPhysics >= 1) {
          continue;
        }

        for (int jumpState = 0; jumpState <= 1; jumpState++) {
          boolean jumped = jumpState == 1;
          // Jumps are only allowed on the ground :(
          if (jumped && !lastOnGround && !inLava && !inWater) {
            continue;
          }

          for (int keyForward = 1; keyForward > -2; keyForward--) {
            for (int keyStrafe = -1; keyStrafe <= 1; keyStrafe++) {
              if (sprinting && keyForward != 1) {
                continue;
              }
              if (inventoryOpen) {
                if ((keyForward != 0 || keyStrafe != 0) || jumped) {
                  continue;
                }
              }
              context.reset(lastMotionX, lastMotionY, lastMotionZ);
              EntityCollisionResult collisionResult = performSimulationOfState(
                user, context, yawSine, yawCosine, friction, keyForward, keyStrafe,
                sneaking, attackReduce, jumped, sprinting, handActive
              );
              PhysicsProcessorContext collisionContext = collisionResult.context();
              double differenceX = collisionContext.motionX - receivedMotionX;
              double differenceY = collisionContext.motionY - receivedMotionY;
              double differenceZ = collisionContext.motionZ - receivedMotionZ;
              double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);
              if (distance < mostAccurateDistance) {
                predictedMovement = collisionResult;
                mostAccurateDistance = distance;
                bestForwardKey = keyForward;
                bestStrafeKey = keyStrafe;
              }
              boolean fastMovementProcess = (!inWater && inLava) || elytraFlying;
              if (distance < 5e-4 && fastMovementProcess) {
                break LOOP;
              }
            }
          }
        }
      }
    }
    movementData.keyForward = bestForwardKey;
    movementData.keyStrafe = bestStrafeKey;
    return predictedMovement;
  }

  private EntityCollisionResult simulateMovementBiased(
    User user, float friction,
    boolean sprinting, boolean sneaking,
    float yawSine, float yawCosine
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    if (inventoryData.inventoryOpen()) {
      keyForward = 0;
      keyStrafe = 0;
    }
    boolean handActive = inventoryData.handActive();
    boolean attackReduce = movementData.pastPlayerAttackPhysics == 0;

    boolean jumped = false;
    if (movementData.lastOnGround && !inventoryData.inventoryOpen()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpUpwardsMotion();
    }

    context.reset(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ);
    return performSimulationOfState(
      user, context, yawSine, yawCosine, friction, keyForward, keyStrafe,
      sneaking, attackReduce, jumped, sprinting, handActive
    );
  }

  private EntityCollisionResult performSimulationOfState(
    User user, PhysicsProcessorContext context,
    float yawSine, float yawCosine, float friction,
    int keyForward, int keyStrafe,
    boolean sneaking, boolean attackReduce,
    boolean jumped, boolean sprinting,
    boolean handActive
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    float moveStrafe = keyStrafe * 0.98f;
    float moveForward = keyForward * 0.98f;
    float rotationPitch = movementData.rotationPitch;
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    boolean inWater = movementData.inWater;
    boolean elytraFlying = movementData.elytraFlying;
    boolean inLava = movementData.inLava();
    boolean swimming = movementData.swimming;
    boolean waterUpdate = clientData.waterUpdate();
    double gravity = movementData.gravity;
    if (sneaking) {
      moveStrafe = (float) ((double) moveStrafe * 0.3);
      moveForward = (float) ((double) moveForward * 0.3);
      if (inWater && clientData.waterUpdate()) {
        context.motionY -= 0.04F;
      }
    }
    if (handActive) {
      moveStrafe *= 0.2f;
      moveForward *= 0.2f;
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
        if (sprinting) {
          context.motionX -= yawSine * 0.2F;
          context.motionZ += yawCosine * 0.2F;
        }
      }
    }
    if (waterUpdate && swimming) {
      double d3 = movementData.lookVector.getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
      boolean fluidStateEmpty = aquaticWaterMovement.fluidStateEmpty(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || !fluidStateEmpty) {
        context.motionY += (d3 - context.motionY) * d4;
      }
    }
    if (inWater) {
      performSimulationInWaterOfState(user, context, moveForward, moveStrafe, yawSine, yawCosine);
    } else if (elytraFlying) {
      performElytraSimulationOfState(movementData.lookVector, context, rotationPitch, gravity);
    } else if (inLava) {
      performLavaSimulationOfState(context, moveForward, moveStrafe, yawSine, yawCosine);
    } else {
      performDefaultMoveSimulationOfState(user, context, moveForward, moveStrafe, yawSine, yawCosine, friction);
    }

    if (!inWater && !elytraFlying && !inLava) {
      tryRelinkFlyingPosition(user, context);
    }

    EntityCollisionResult collisionResult = entityCollisionRepository.resolveEntityCollisionOf(
      user, context, movementData.inWeb,
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return collisionResult;
  }

  private void performSimulationInWaterOfState(
    User user, PhysicsProcessorContext context,
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

  private void performElytraSimulationOfState(
    Vector lookVector, PhysicsProcessorContext context,
    float rotationPitch, double gravity
  ) {
    float f = rotationPitch * 0.017453292F;
    double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
    double dist2 = Math.sqrt(context.motionX * context.motionX + context.motionZ * context.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = WrappedMathHelper.cos(f);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
//                predictedMotionY += -0.08 + (double) f4 * 0.06D;
    context.motionY += gravity * (-1 + pitchCosine * 0.75);

    if (context.motionY < 0.0D && rotationVectorDistance > 0.0D) {
      double d2 = context.motionY * -0.1D * (double) pitchCosine;
      context.motionY += d2;
      context.motionX += lookVector.getX() * d2 / rotationVectorDistance;
      context.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
    }

    // 1.9
//                if (f < 0.0F) {
//                  double d9 = d8 * (double) (-WrappedMathHelper.sin(f)) * 0.04D;
//                  predictedMotionY += d9 * 3.2D;
//                  predictedMotionX -= elytraMoveVector.getX() * d9 / d6;
//                  predictedMotionZ -= elytraMoveVector.getZ() * d9 / d6;
//                }
    // 1.16
    if (f < 0.0F && rotationVectorDistance > 0.0D) {
      double d9 = dist2 * (double) (-WrappedMathHelper.sin(f)) * 0.04D;
      context.motionY += d9 * 3.2D;
      context.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
      context.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
//                  vector3d = vector3d.add(-vector3d1.x * d9 / d1, d9 * 3.2D, -vector3d1.z * d9 / d1);
    }

    // 1.9
    if (rotationVectorDistance > 0.0D) {
      context.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - context.motionX) * 0.1D;
      context.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - context.motionZ) * 0.1D;
    }
    // 1.16
//                if (d6 > 0.0D) {
//                  predictedMotionX += (elytraMoveVector.getX() / d6 * d1 - predictedMotionX) * 0.1D;
//                  predictedMotionZ += (elytraMoveVector.getZ() / d6 * d1 - predictedMotionZ) * 0.1D;
//                }

    context.motionX *= 0.99f;
    context.motionY *= 0.98f;
    context.motionZ *= 0.99f;
  }

  private void performLavaSimulationOfState(
    PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float friction = 0.02f;
    performRelativeMoveSimulationOfState(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void performDefaultMoveSimulationOfState(
    User user, PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine,
    float friction
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    performRelativeMoveSimulationOfState(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
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
    PhysicsProcessorContext context, float friction,
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

  private void tryRelinkFlyingPosition(User user, PhysicsProcessorContext context) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onGround;
    Location location = new Location(player.getWorld(), positionX, positionY, positionZ);
    double slipperiness = movementData.lastOnGround ? PlayerMovementHelper.resolveSlipperiness(location) : 0.91f;
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
      } else if (jump && flyingPacket(diffX, 0.0, diffZ)) {
        context.motionY = jumpUpwardsMotion;
        movementData.physicsPacketRelinkFlyVL = 0;
        break;
      } else {
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
    Player player, PhysicsProcessorContext context,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    CollisionHelper.CollisionResult result = CollisionHelper.resolveQuickCollisions(player, positionX, positionY, positionZ, motionX, motionY, motionZ);
    context.motionX = result.motionX();
    context.motionY = result.motionY();
    context.motionZ = result.motionZ();
  }

  private void notePossibleFlyingPacket(User user, EntityCollisionResult collisionResult) {
    UserMetaMovementData movementData = user.meta().movementData();
    PhysicsProcessorContext context = collisionResult.context();
    if (flyingPacket(context.motionX, context.motionY, context.motionZ)) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  private final static double FLYING_DISTANCE = 0.0009;

  private boolean flyingPacket(double diffX, double diffY, double diffZ) {
    double distance = diffX * diffX + diffY * diffY + diffZ * diffZ;
    return distance <= FLYING_DISTANCE;
  }

  private boolean lenientItemUsageChecking(User user) {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItemStack = inventoryData.heldItem();
    return heldItemStack != null && heldItemStack.getType() == InventoryUseItemHelper.ITEM_TRIDENT;
  }

  private void evaluateBestSimulation(User user, EntityCollisionResult expectedMovement) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    boolean spectator = player.getGameMode() == GameMode.SPECTATOR;

    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaAbilityData abilityData = meta.abilityData();
    PhysicsProcessorContext context = expectedMovement.context();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.flying();
    String key = resolveKeysFromInput(keyForward, keyStrafe);

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
    double receivedPositionX = movementData.positionX;
    double receivedPositionY = movementData.positionY;
    double receivedPositionZ = movementData.positionZ;
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    double predictedX = context.motionX;
    double predictedY = context.motionY;
    double predictedZ = context.motionZ;

    double differenceX = predictedX - receivedMotionX;
    double differenceY = predictedY - receivedMotionY;
    double differenceZ = predictedZ - receivedMotionZ;
    double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);

    boolean onLadder = PlayerMovementHelper.isOnLadder(user, positionX, positionY + 1.5, positionZ);
    onLadder |= PlayerMovementHelper.isOnLadder(user, positionX, positionY - 0.5, positionZ);
    onLadder |= PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean onLadderLast = movementData.onLadderLast;
    movementData.onLadderLast = onLadder;
    onLadder = movementData.onLadderLast || onLadderLast;

    double verticalViolationIncrease = calculateVerticalViolationLevelIncrease(user, predictedY, onLadder);
    double horizontalViolationIncrease = calculateHorizontalViolationIncrease(user, keyForward, keyStrafe, predictedX, predictedZ, onLadder);
    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;

    if (distance > 1e-3) {
      movementData.suspiciousMovement = true;
    }

    if (flying || spectator) {
      violationLevelIncrease = 0;
    }

    if (movementData.pastVelocity < 10 && inventoryData.pastItemUsageTransition > 7) {
      if (violationLevelIncrease > 0) {
        violationLevelIncrease = Math.max(violationLevelIncrease, 1.0);
      }
      // Could be smaller (testing required)
      if (distance > 0.005) {
        violationLevelIncrease *= 8.5;
      }
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.980;
      violationLevelData.physicsVL -= 0.012;
    }

    Location verifiedLocation = movementData.verifiedLocation();

    List<WrappedAxisAlignedBB> intersectionBoundingBoxesLast = Collision.resolve(user.player(), CollisionHelper.boundingBoxOf(user, verifiedLocation.getX(), verifiedLocation.getY(), verifiedLocation.getZ()));
    List<WrappedAxisAlignedBB> intersectionBoundingBoxesCurrent = Collision.resolve(user.player(), CollisionHelper.boundingBoxOf(user, receivedPositionX, receivedPositionY, receivedPositionZ));

    boolean boundingBoxIntersectionLast = !intersectionBoundingBoxesLast.isEmpty();//CollisionHelper.checkBoundingBoxIntersection(user, CollisionHelper.boundingBoxOf(user, verifiedLocation.getX(), verifiedLocation.getY(), verifiedLocation.getZ()));
    boolean boundingBoxIntersectionCurrent = !intersectionBoundingBoxesCurrent.isEmpty();//CollisionHelper.checkBoundingBoxIntersection(user, CollisionHelper.boundingBoxOf(user, receivedPositionX, receivedPositionY, receivedPositionZ));
    boolean movedIntoBlock = !boundingBoxIntersectionLast && boundingBoxIntersectionCurrent;

    if (boundingBoxIntersectionCurrent && !spectator) {
      if (movedIntoBlock) {
        movementData.invalidMovement = true;

        String boundingBoxOutput;
        if (intersectionBoundingBoxesCurrent.size() > 1) {
          boundingBoxOutput = String.valueOf(intersectionBoundingBoxesCurrent);
        } else {
          boundingBoxOutput = String.valueOf(intersectionBoundingBoxesCurrent.get(0));
        }

        String message = "moved into block";
        String details = boundingBoxOutput;
        user.boundingBoxAccess().invalidate();

        plugin.retributionService().processViolation(player, 0, "Physics", message, details);
        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, 8);
      } else {
        plugin.eventService().emulationEngine().emulationPushOutOfBlock(player);
      }
    }

    // Update the player's verified location
    if (spectator || violationLevelIncrease == 0 && !movedIntoBlock) {
      Location location = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
      movementData.setVerifiedLocation(location, "Movement validation (normal)");
    }

    if (violationLevelIncrease > 0 && !spectator) {
      violationLevelIncrease = Math.min(60.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL += violationLevelIncrease;

      user.boundingBoxAccess().invalidate();
    }

    if ((!spectator && !movedIntoBlock && violationLevelData.physicsVL > 20 && violationLevelIncrease > 0)) {
      movementData.invalidMovement = true;
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);

      Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
      String message = "moved incorrectly";
      String details = received + " e: " + expected;

      boolean setback = plugin.retributionService().processViolation(player, violationLevelIncrease / 20d, "Physics", message, details) || violationLevelData.physicsVL >= 60;
      if (setback) {
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, context.motionY > 0 ? 12 : 4);
      }
      if(setback) {
        movementData.invalidMovement = true;
      }
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL < 1) {
//      player.sendMessage("Decremengin");
      decrementer.decrement(user, VL_DECREMENT_PER_VALID_MOVE);
    }

    violationLevelData.physicsVL = Math.max(0, violationLevelData.physicsVL);
    violationLevelData.physicsVL = Math.min(100, violationLevelData.physicsVL);

    if (movementData.inWater || movementData.onLadderLast) {
      movementData.artificialFallDistance = 0;
    }

    if (movementData.inLava()) {
      movementData.artificialFallDistance *= 0.5;
    }

    if (IntaveControl.DEBUG_MOVEMENT) {
      ChatColor chatColor = violationLevelIncrease == 0 ? ChatColor.GRAY : ChatColor.YELLOW;
      String motion = MathHelper.formatPositionAsInt(positionX, positionY, positionZ);
      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 4);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 3);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 3);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 3);

      String violationLevelInfo;
      if (violationLevelIncrease > 0) {
        violationLevelInfo = "g:" + displayPhysicsVL + ",c:" + displayViolationIncrease + "(" + displayHorizontalVL + "," + displayVerticalVL + ")";
      } else {
        violationLevelInfo = "g:" + displayPhysicsVL;
      }
      String debug = chatColor + motion + " ";
      if (movementData.recentlyEncounteredFlyingPacket(0)) {
        debug += "f";
      }
      debug += "(" + key + ") " + " " + violationLevelInfo;

//      debug += " (sneak " + movementData.sneaking + ")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += "handActive=" + inventoryData.handActive();
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.pastFlyingPacketAccurate;
      debug += " dist=" + formatDouble(distance, 10);
//      debug += " inventoryOpen=" + inventoryData.inventoryOpen();
      debug += " " + (violationLevelData.isInActiveTeleportBundle ? "+" : "-");
      if (movedIntoBlock) {
        debug += " bb-intersection";
      }

      String finalDebug = debug;
      Synchronizer.synchronize(() -> player.sendMessage(finalDebug));
    }
  }

  private final static double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  private double calculateVerticalViolationLevelIncrease(User user, double predictedY, boolean onLadder) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    boolean swimming = movementData.swimming;
    boolean elytraFlying = movementData.elytraFlying;
    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;
    double receivedMotionY = movementData.motionY();
    double differenceY = Math.abs(receivedMotionY - predictedY);
    boolean accountedSkippedMovement = movementData.recentlyEncounteredFlyingPacket(2);
    double legitimateDeviation = accountedSkippedMovement ? 1e-2 : 1e-5;
    // MotionY calculations with sin/cos (FastMath affected)
    if (swimming || elytraFlying) {
      legitimateDeviation = 0.001;
    }

    if ((movementData.pastPushedByWaterFlow < 10 || movementData.inLava()) && distanceMoved < 0.2) {
      legitimateDeviation = 0.1;
    }

    // Movement in webs is always skipped
    if (movementData.inWeb && receivedMotionY < 0.0) {
      legitimateDeviation = 0.1;
    }

    // Water flow cannot be calculated correctly
    if (pushedByWaterFlow) {
      legitimateDeviation = 0.1;
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    if (movementData.recentlyEncounteredFlyingPacket(3) && differenceY > 1e-3) {
      boolean inLiquid = movementData.pastWaterMovement <= 10 || movementData.inLava();
      if (inLiquid || movementData.physicsPacketRelinkFlyVL++ <= 1) {
        legitimateDeviation = Math.max(legitimateDeviation, inLiquid ? 0.1 : 0.03);
      }
    }

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);

    // Jump out of water
    if (movementData.inWater && abuseVertically > 1e-5 && receivedMotionY > 0.0 && receivedMotionY < 0.35) {
      Location location = new Location(player.getWorld(), movementData.positionX, movementData.positionY, movementData.positionZ);
      if (CollisionHelper.nearBySolidBlock(location, 0.4)) {
        boolean airAbove = !PlayerMovementHelper.isAllLiquid(player.getWorld(), movementData.boundingBox());
        if (airAbove) {
          abuseVertically = 0;
        }
      }
    }

    double multiplier = abuseVertically > 1e-5 ? 205.0 : 25.0;

    if (onLadder && movementData.motionY() <= LADDER_UPWARDS_MOTION) {
      abuseVertically = 0;
    }

    if (movementData.pastWaterMovement < 5 || movementData.inLava()) {
      multiplier *= 0.4;
    }

    return abuseVertically * multiplier;
  }

  private double calculateHorizontalViolationIncrease(
    User user, int keyForward, int keyStrafe,
    double predictedX, double predictedZ,
    boolean onLadder
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    double motionX = movementData.motionX();
    double motionZ = movementData.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    double predictedDistanceMoved = Math.hypot(predictedX, predictedZ);
    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;
    double legitimateDeviation = movementData.pastPlayerAttackPhysics <= 1 ? 0.01 : 0.0007;

    if (movementData.pastWaterMovement < 10) {
      legitimateDeviation = 0.01;
    }

    if (pushedByWaterFlow) {
      legitimateDeviation = 0.028;
    }

    // Flying packet
    if (movementData.recentlyEncounteredFlyingPacket(2)) {
      if (movementData.onGround) {
        //TODO: Check if last block-placement did not happen recently
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
        legitimateDeviation = lessThanExpected ? 0.115 : 0.005;
//        legitimateDeviation = 0.05;
      } else {
        legitimateDeviation = 0.05;
      }
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    boolean pressedNothing = keyStrafe == 0 && keyForward == 0;
    boolean recentlySentFlying = movementData.recentlyEncounteredFlyingPacket(2);
    boolean recentlyVelocity = movementData.pastVelocity <= 1;

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      if (lessThanExpected || distanceMoved < 0.2) {
        legitimateDeviation = Math.max(legitimateDeviation, distanceMoved);
      }
    }
    if (pressedNothing && !inventoryData.inventoryOpen()) {
      double deviation = movementData.onGround || movementData.lastOnGround ? 0.1 : 0.07;
      legitimateDeviation = Math.max(legitimateDeviation, deviation);
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movementData.motionY() < 0 ? 0.4 : 0.2))) {
      legitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    double abuseHorizontally = Math.max(0, distance - legitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005
      && inventoryData.pastItemUsageTransition > 10;
    if (movedTooQuickly && distanceMoved > 0.2 && abuseHorizontally > 0 && !recentlyVelocity) {
//      double v = Math.max(abuseHorizontally, 0.3) * 100.0;
//      Bukkit.broadcastMessage(user.bukkitPlayer().getName() + " moved too quickly: vl+" + v + " -" + inventoryData.pastItemUsageTransition);
      return Math.max(abuseHorizontally, 0.3) * 100.0;
    }
    return abuseHorizontally * ((abuseHorizontally > 0.1) ? 20.0 : 10.0);
  }

  private final static double RIPTIDE_TOLERANCE = 3.005;
  private final static double RIPTIDE_TOLERANCE_2 = 0.05;
  private final static double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(UserMetaMovementData movementData) {
    double riptideTolerance;
    if (movementData.onGround) {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }

  private void prepareNextTick(
    User user,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    Player player = user.player();
    World world = player.getWorld();
    User.UserMeta meta = user.meta();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaMovementData movementData = meta.movementData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
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
      slipperiness = PlayerMovementHelper.resolveSlipperiness(blockBelow);
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

    if (inWater) {
      simulateWaterAfter(user, context, boundingBox, collidedHorizontally, gravity);
    } else if (inLava) {
      simulateLavaAfter(player, user, context, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      simulateNormalAfter(user, context, gravity, slipperiness);
    }

    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.physicsLastMotionX = context.motionX;
      movementData.physicsLastMotionY = context.motionY;
      movementData.physicsLastMotionZ = context.motionZ;
    }

    updateAquatics(user);

    movementData.lastSprinting = movementData.sprinting;
    movementData.lastSneaking = movementData.sneaking;
    movementData.increaseFlyingPacket();
    movementData.pastPlayerAttackPhysics++;
    movementData.pastPushedByWaterFlow++;

    if (movementData.onGround) {
      movementData.physicsPacketRelinkFlyVL = 0;
    }
  }

  private void simulateMovementOfCollidedBlocks(
    User user, PhysicsProcessorContext context,
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
    Block block = BlockAccessor.blockAccess(world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);

    if (block.getType() == Material.AIR) {
      Block blockBelow = BlockAccessor.blockAccess(world, blockCollisionPosX, blockCollisionPosY - 1, blockCollisionPosZ);
      Material material = blockBelow.getType();
      if (material.name().contains("FENCE") || material.name().contains("WALL")) {
        block = blockBelow;
        // blockPosition = blockPosition.down();
      }
    }

    blockCollisionRepository.fallenUpon(user, block.getType());

    // onLanded
    if (movementData.collidedVertically) {
      Vector collisionVector = blockCollisionRepository.blockLanded(
        user, block.getType(),
        context.motionX, movementData.physicsLastMotionY, context.motionZ
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
      Vector collisionVector = blockCollisionRepository.entityCollision(
        user, block.getType(),
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
          Material material = BlockAccessor.blockAccess(world, x, y, z).getType();
          Vector collisionVector = blockCollisionRepository.entityCollision(
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
        Vector speedFactor = blockCollisionRepository.speedFactor(user, material, context.motionX, context.motionY, context.motionZ);
        if (speedFactor != null) {
          context.motionX = speedFactor.getX();
          context.motionY = speedFactor.getY();
          context.motionZ = speedFactor.getZ();
        }
      }
    }
  }

  private void simulateWaterAfter(
    User user, PhysicsProcessorContext context, WrappedAxisAlignedBB entityBoundingBox,
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
    PhysicsProcessorContext context, WrappedAxisAlignedBB boundingBox,
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

  private void simulateNormalAfter(User user, PhysicsProcessorContext context, double gravity, double multiplier) {
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

  private void simulateMotionClamp(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    double resetMotion = movementData.resetMotion();
    if (Math.abs(movementData.physicsLastMotionX) < resetMotion) {
      movementData.physicsLastMotionX = 0.0;
    }
    if (Math.abs(movementData.physicsLastMotionY) < resetMotion) {
      movementData.physicsLastMotionY = 0.0;
    }
    if (Math.abs(movementData.physicsLastMotionZ) < resetMotion) {
      movementData.physicsLastMotionZ = 0.0;
    }
  }

  private void updateAquatics(User user) {
    updateInWater(user);
    updateEyesInWater(user);
  }

  private void updateEyesInWater(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.eyesInWater = aquaticWaterMovement.areEyesInFluid(user, movementData.positionX, movementData.positionY, movementData.positionZ);
  }

  private void updateInWater(User user) {
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    if (clientData.protocolVersion() >= PROTOCOL_VERSION_AQUATIC_UPDATE) {
      movementData.inWater = aquaticWaterMovement.handleFluidAcceleration(user, movementData.boundingBox());
    } else {
      WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();
      WrappedAxisAlignedBB checkableBoundingBox = entityBoundingBox
        .expand(0.0D, -0.4000000059604645D, 0.0D)
        .contract(0.001D, 0.001D, 0.001D);
      movementData.inWater = waterMovementLegacyResolver.handleMaterialAcceleration(user, checkableBoundingBox);
    }

    if (movementData.inWater) {
      movementData.pastWaterMovement = 0;
    }
  }

  private float resolveFriction(
    Player player,
    UserMetaMovementData movementData,
    double positionX, double positionY, double positionZ
  ) {
    float speed;
    if (movementData.lastOnGround) {
      Location location = new Location(
        player.getWorld(),
        WrappedMathHelper.floor(positionX),
        WrappedMathHelper.floor(positionY - 1.0),
        WrappedMathHelper.floor(positionZ)
      );
      float slipperiness = PlayerMovementHelper.resolveSlipperiness(location);
      float var4 = 0.16277136f / (slipperiness * slipperiness * slipperiness);
      speed = movementData.aiMoveSpeed() * var4;
    } else {
      speed = movementData.jumpMovementFactor();
    }
    return speed;
  }

  public static final class PhysicsProcessorContext {
    public double motionX;
    public double motionY;
    public double motionZ;

    public PhysicsProcessorContext() {
      this(0.0, 0.0, 0.0);
    }

    public PhysicsProcessorContext(double motionX, double motionY, double motionZ) {
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
    }

    public void reset(double x, double y, double z) {
      this.motionX = x;
      this.motionY = y;
      this.motionZ = z;
    }

    public static PhysicsProcessorContext from(PhysicsProcessorContext context) {
      return new PhysicsProcessorContext(context.motionX, context.motionY, context.motionZ);
    }
  }
}