package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.detect.checks.movement.physics.collision.PhysicsCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.water.*;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementLocaleHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.items.PlayerEnchantmentHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.CollisionFactory;
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
  private final static boolean DEBUG_MOVEMENT = true;
  private final static boolean DEBUG_PERFORMANCE = false; // Disable DEBUG_MOVEMENT
  private final static boolean MOVEMENT_EMULATION = true;
  private final static float STEP_HEIGHT = 0.6f;

  private final IntavePlugin plugin;
  private final PhysicsCollisionRepository collisionRepository;

  private WaterMovementLegacyResolver waterMovementLegacyResolver;
  private AquaticWaterMovementBase aquaticWaterMovement;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
    this.collisionRepository = new PhysicsCollisionRepository();
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
      prepareNextTick(
        user,
        movementData.positionX, movementData.positionY, movementData.positionZ,
        motionX, motionY, motionZ
      );
    }
  }

  private void processMovement(User user, double receivedMotionX, double receivedMotionY, double receivedMotionZ) {
    Player player = user.bukkitPlayer();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    float friction = resolveFriction(player, movementData, positionX, positionY, positionZ);
    boolean sneaking;
    if (clientData.delayedSneak()) {
      sneaking = movementData.lastSneaking;
    } else if (clientData.alternativeSneak()) {
      sneaking = movementData.lastSneaking || movementData.sneaking;
    } else {
      sneaking = movementData.sneaking;
    }
    float rotationYaw = movementData.rotationYaw;
    float yawSine = SinusCache.sin(rotationYaw * (float) Math.PI / 180.0F, false);
    float yawCosine = SinusCache.cos(rotationYaw * (float) Math.PI / 180.0F, false);
    boolean sprinting = movementData.sprinting;
    long startTime = System.nanoTime();
    /*
    Physics process
     */
    PreciseCollisionResult predictedMovement = physicsFast(user, friction, sprinting, sneaking, yawSine, yawCosine);
    PhysicsProcessorContext contextFastProcess = predictedMovement.context;
    double differenceX = contextFastProcess.motionX - receivedMotionX;
    double differenceY = contextFastProcess.motionY - receivedMotionY;
    double differenceZ = contextFastProcess.motionZ - receivedMotionZ;
    double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);
    if (distance > 0.001) {
      predictedMovement = physicsAccurate(
        user, friction, sprinting, sneaking, yawSine, yawCosine,
        receivedMotionX, receivedMotionY, receivedMotionZ
      );
    }
//    if (DEBUG_PERFORMANCE) {
//      double endTime = (System.nanoTime() - startTime) / 1_000_000.0;
//      System.out.println("[Intave] Physics-Performance-Debug: " + endTime + " ms/c");
//    }
    evaluateMovement(user, predictedMovement);
    if (DEBUG_PERFORMANCE) {
      double endTime = (System.nanoTime() - startTime) / 1_000_000.0;
      System.out.println("[Intave] Physics-Performance-Debug: " + endTime + " ms/c");
    }
    movementData.onGround = predictedMovement.onGround;
    movementData.collidedHorizontally = predictedMovement.collidedHorizontally;
    movementData.collidedVertically = predictedMovement.collidedVertically;
    movementData.physicsResetMotionX = predictedMovement.resetMotionX;
    movementData.physicsResetMotionZ = predictedMovement.resetMotionZ;
    movementData.pastRiptideSpin++;
  }

  private PreciseCollisionResult physicsAccurate(
    User user, float friction,
    boolean sprinting, boolean sneaking,
    float yawSine, float yawCosine,
    double receivedMotionX, double receivedMotionY, double receivedMotionZ
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    double lastMotionX = movementData.physicsLastMotionX;
    double lastMotionY = movementData.physicsLastMotionY;
    double lastMotionZ = movementData.physicsLastMotionZ;
    boolean lenientItemUsageChecking = lenientItemUsageChecking(user);
    boolean inventoryOpen = inventoryData.inventoryOpen();
    boolean inWeb = movementData.inWeb;
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    boolean elytraFlying = movementData.elytraFlying;
    int bestForwardKey = 0;
    int bestStrafeKey = 0;
    double mostAccurateDistance = Integer.MAX_VALUE;
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
    PreciseCollisionResult predictedMovement = null;

    LOOP:
    for (int heldItemState = 0; heldItemState <= 1; heldItemState++) {
      boolean handActive = heldItemState == 1;

      if (!lenientItemUsageChecking) {
        // Force the player to accept the item usage
        int blockLenience = inventoryData.pastItemUsageTransition < 5 ? 5 : 0;
        if (inventoryData.handActiveTicks >= blockLenience && inventoryData.handActive() && !handActive) {
          continue;
        }
        // Remove the ability to accept the item usage
        if (!inventoryData.handActive() && handActive) {
          continue;
        }
      }

      for (int attackState = 0; attackState <= 1; attackState++) {
        boolean attackReduce = attackState == 1;
        if (attackReduce && movementData.pastPlayerAttackPhysics > 5) {
          continue;
        }

        for (int jumpState = 0; jumpState <= 1; jumpState++) {
          boolean jumped = jumpState == 1;
          // Jumps are only allowed on the ground :(
          if (jumped && !lastOnGround && !inLava && !inWater) {
            continue;
          }

          for (int keyStrafe = -1; keyStrafe <= 1; keyStrafe++) {
            for (int keyForward = -1; keyForward <= 1; keyForward++) {
              if (sprinting && keyForward != 1) {
                continue;
              }
              if (inventoryOpen) {
                if ((keyForward != 0 || keyStrafe != 0) || jumped) {
                  continue;
                }
              }
              context.reset(lastMotionX, lastMotionY, lastMotionZ);
              physicsCalculate(
                user, context, yawSine, yawCosine, friction, keyForward, keyStrafe,
                sneaking, attackReduce, jumped, sprinting, handActive
              );
              PreciseCollisionResult collisionResult = physicsCalculateCollision(
                user, context, inWeb,
                positionX, positionY, positionZ
              );
              PhysicsProcessorContext collisionContext = collisionResult.context;
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

  private PreciseCollisionResult physicsFast(
    User user, float friction,
    boolean sprinting, boolean sneaking,
    float yawSine, float yawCosine
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
//    if (inventoryData.inventoryOpen()) {
//      keyForward = 0;
//      keyStrafe = 0;
//    }
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    boolean handActive = inventoryData.handActive();
    boolean attackReduce = movementData.pastPlayerAttackPhysics == 0;
    boolean jumped = movementData.jumpUpwardsMotion() == movementData.motionY()
      && movementData.onGround
      && !inventoryData.inventoryOpen();
    context.reset(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ);
    physicsCalculate(
      user, context, yawSine, yawCosine, friction, keyForward, keyStrafe,
      sneaking, attackReduce, jumped, sprinting, handActive
    );
    boolean inWeb = movementData.inWeb;
    return physicsCalculateCollision(
      user, context, inWeb,
      positionX, positionY, positionZ
    );
  }

  private void physicsCalculate(
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
      physicsCalculateWater(user, context, moveForward, moveStrafe, yawSine, yawCosine);
    } else if (elytraFlying) {
      physicsCalculateElytra(movementData.lookVector, context, rotationPitch, gravity);
    } else if (inLava) {
      physicsCalculateLava(context, moveForward, moveStrafe, yawSine, yawCosine);
    } else {
      physicsCalculateNormal(user, context, moveForward, moveStrafe, yawSine, yawCosine, friction);
    }
  }

  private void physicsCalculateWater(
    User user, PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    Player player = user.bukkitPlayer();
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
    physicsCalculateRelativeMovement(context, f2, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void physicsCalculateRelativeMovement(
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

  private void physicsCalculateElytra(
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

  private void physicsCalculateLava(
    PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float friction = 0.02f;
    physicsCalculateRelativeMovement(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
  }

  private void physicsCalculateNormal(
    User user, PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine,
    float friction
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    physicsCalculateRelativeMovement(context, friction, yawSine, yawCosine, moveForward, moveStrafe);
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
    physicsCalculateFlying(user, context);
  }

  private void physicsCalculateFlying(User user, PhysicsProcessorContext context) {
    Player player = user.bukkitPlayer();
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
      movementData.pastFlyingPacketAccurate = 0;
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

  private void evaluateMovement(User user, PreciseCollisionResult expectedMovement) {
    Player player = user.bukkitPlayer();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaAbilityData abilityData = meta.abilityData();
    PhysicsProcessorContext context = expectedMovement.context;

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
    onLadder = onLadder || PlayerMovementHelper.isOnLadder(user, positionX, positionY - 0.5, positionZ);
    onLadder = onLadder || PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean onLadderLast = movementData.onLadderLast;
    movementData.onLadderLast = onLadder;
    onLadder = movementData.onLadderLast || onLadderLast;

    double verticalViolationIncrease = resolveVerticalViolationIncrease(user, predictedY, onLadder);
    double horizontalViolationIncrease = resolveHorizontalViolationIncrease(user, keyForward, keyStrafe, predictedX, predictedZ, onLadder);
    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;

    if (flying) {
      violationLevelIncrease = 0;
    }

    if (movementData.pastVelocity < 10 && inventoryData.pastItemUsageTransition > 7) {
      if (violationLevelIncrease > 0) {
        violationLevelIncrease = Math.max(violationLevelIncrease, 1.0);
      }
      violationLevelIncrease *= 3.5;
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.980;
      violationLevelData.physicsVL -= 0.012;
    }

    // Update the player's verified location
    if (violationLevelIncrease == 0) {
      movementData.verifiedLocation = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
    }

    if (violationLevelData.physicsVL > 2 && violationLevelIncrease > 0) {
      inventoryData.resynchronizeHeldItem();
      if (inventoryData.handActive()) {
        inventoryData.applySlotSwitch();
      }
    }

    if (violationLevelIncrease > 0) {
      violationLevelIncrease = Math.min(60.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL += violationLevelIncrease;
    }

    if (violationLevelData.physicsVL > 20 && violationLevelIncrease > 0) {
      movementData.invalidMovement = true;
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);
      String message = "sent unexpected position: (" + received + ") but expected (" + expected + ")";

      plugin.retributionService().markPlayer(player, (int) violationLevelIncrease, "Physics", message);

      if (violationLevelData.physicsVL > 40 && MOVEMENT_EMULATION) {
        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, 1);
      }
    }

    violationLevelData.physicsVL = Math.max(0, violationLevelData.physicsVL);
    violationLevelData.physicsVL = Math.min(100, violationLevelData.physicsVL);

    if (DEBUG_MOVEMENT) {
      ChatColor chatColor = violationLevelIncrease == 0 ? ChatColor.GRAY : ChatColor.YELLOW;
      String position = MathHelper.formatPositionAsInt(receivedPositionX, receivedPositionY, receivedPositionZ);
      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 4);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 3);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 3);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 3);

      if (movementData.pastFlyingPacketAccurate == 0) {
        key += ".";
      }

      String violationLevelInfo;
      if (violationLevelIncrease > 0) {
        violationLevelInfo = "g:" + displayPhysicsVL + ",c:" + displayViolationIncrease
          + "(" + displayHorizontalVL + "," + displayVerticalVL + ")";
      } else {
        violationLevelInfo = "g:" + displayPhysicsVL;
      }
      String debug = chatColor + position + " (" + key + ") " + " " + violationLevelInfo;
//      debug += " (sneak " + movementData.sneaking + ")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += "handActive=" + inventoryData.handActive();
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.pastFlyingPacketAccurate;
      if (violationLevelIncrease > 0) {
        debug += " dist=" + formatDouble(distance, 10);
      }
//      debug += " inventoryOpen=" + inventoryData.inventoryOpen();
      debug += " " + (violationLevelData.isInActiveTeleportBundle ? "+" : "-");
      player.sendMessage(player.getName() + "| " + debug);

//      player.sendMessage(debug + " dist=" + formatDouble(distance, 10));
    }
  }

  private final static double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  private double resolveVerticalViolationIncrease(User user, double predictedY, boolean onLadder) {
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
    boolean accountedSkippedMovement = movementData.pastFlyingPacketAccurate <= 2;
    double legitimateDeviation = accountedSkippedMovement ? 1e-2 : 1e-5;
    // MotionY calculations with sin/cos (FastMath affected)
    if (swimming || elytraFlying) {
      legitimateDeviation = 0.001;
    }

    if ((movementData.pastWaterMovement < 10 || movementData.inLava()) && distanceMoved < 0.1) {
      legitimateDeviation = 0.1;
    }

    // Jump out of water
    if (movementData.pastWaterMovement < 5 && !movementData.inWater && distanceMoved < 0.1) {
      legitimateDeviation = 0.6;
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

    if (movementData.pastFlyingPacketAccurate <= 3) {
      legitimateDeviation = 0.03;
    }

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);
    double multiplier = abuseVertically > 1e-5 ? 105.0 : 25.0;

    if (onLadder && movementData.motionY() <= LADDER_UPWARDS_MOTION) {
      abuseVertically = 0;
    }

    if (movementData.pastWaterMovement < 5 || movementData.inLava()) {
      multiplier *= 0.4;
    }

    return abuseVertically * multiplier;
  }

  private double resolveHorizontalViolationIncrease(
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
    double legitimateDeviation = 7e-4;

    if (movementData.pastWaterMovement < 10) {
      legitimateDeviation = 0.01;
    }

    if (pushedByWaterFlow) {
      legitimateDeviation = 0.028;
    }

    // Flying packet
    if (movementData.pastFlyingPacketAccurate <= 2) {
      legitimateDeviation = movementData.onGround ? 0.155 : 0.05;
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    boolean pressedNothing = keyStrafe == 0 && keyForward == 0;
    boolean recentlySentFlying = movementData.pastFlyingPacketAccurate <= 2;
    boolean recentlyVelocity = movementData.pastVelocity <= 1;

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      if (lessThanExpected || distanceMoved < 0.2) {
        legitimateDeviation = Math.max(legitimateDeviation, distanceMoved);
      }
    }
    if (pressedNothing) {
      double deviation = movementData.onGround || movementData.lastOnGround ? 0.1 : 0.07;
      legitimateDeviation = Math.max(legitimateDeviation, deviation);
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movementData.motionY() < 0 ? 0.4 : 0.2))) {
      legitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    double abuseHorizontally = Math.max(0, distance - legitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.005
        && inventoryData.pastItemUsageTransition > 10;
    if (movedTooQuickly && distanceMoved > 0.2 && abuseHorizontally > 0 && !recentlySentFlying && !recentlyVelocity) {
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
    Player player = user.bukkitPlayer();
    World world = player.getWorld();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;
    context.reset(motionX, motionY, motionZ);

    boolean elytraFlying = PlayerMovementLocaleHelper.flyingWithElytra(player);
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

    WrappedAxisAlignedBB boundingBox = CollisionHelper.entityBoundingBoxOf(user, positionX, positionY, positionZ);
    movementData.setBoundingBox(boundingBox);

    if (movementData.inWeb) {
      context.motionX = 0.0;
      context.motionY = 0.0;
      context.motionZ = 0.0;
      movementData.inWeb = false;
    }

    if (clientData.motionResetOnCollision()) {
      if (movementData.physicsResetMotionX) {
        context.motionX = 0.0;
      }
      if (movementData.physicsResetMotionZ) {
        context.motionZ = 0.0;
      }
    }

    physicsCalculateBlockCollisions(user, context, boundingBox);

    if (inWater) {
      physicsCalculateWaterAfter(user, context, boundingBox, collidedHorizontally, gravity);
    } else if (inLava) {
      physicsCalculateLavaAfter(player, user, context, boundingBox, collidedHorizontally);
    } else if (!elytraFlying) {
      physicsCalculateNormalAfter(user, context, gravity, slipperiness);
    }

    physicsCalculateMovementClamp(user, context);

    movementData.physicsLastMotionX = context.motionX;
    movementData.physicsLastMotionY = context.motionY;
    movementData.physicsLastMotionZ = context.motionZ;

    updateAquatics(user);

    movementData.verifiedPositionX = positionX;
    movementData.verifiedPositionY = positionY;
    movementData.verifiedPositionZ = positionZ;

    movementData.lastOnGround = movementData.onGround;
    movementData.lastSprinting = movementData.sprinting;
    movementData.lastSneaking = movementData.sneaking;
    movementData.pastPlayerAttackPhysics++;
    movementData.pastFlyingPacketAccurate++;
    movementData.pastPushedByWaterFlow++;
  }

  private void physicsCalculateBlockCollisions(
    User user, PhysicsProcessorContext context,
    WrappedAxisAlignedBB entityBoundingBox
  ) {
    Player player = user.bukkitPlayer();
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

    // onLanded
    if (movementData.collidedVertically) {
      Vector collisionVector = collisionRepository.blockLanded(
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
      Vector collisionVector = collisionRepository.entityCollision(
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
          Vector collisionVector = collisionRepository.entityCollision(
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
        Vector speedFactor = collisionRepository.speedFactor(user, material, context.motionX, context.motionY, context.motionZ);
        if (speedFactor != null) {
          context.motionX = speedFactor.getX();
          context.motionY = speedFactor.getY();
          context.motionZ = speedFactor.getZ();
        }
      }
    }
  }

  private void physicsCalculateWaterAfter(
    User user, PhysicsProcessorContext context, WrappedAxisAlignedBB entityBoundingBox,
    boolean collidedHorizontally, double gravity
  ) {
    Player player = user.bukkitPlayer();
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

  private void physicsCalculateLavaAfter(
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

  private void physicsCalculateNormalAfter(User user, PhysicsProcessorContext context, double gravity, double multiplier) {
    Player player = user.bukkitPlayer();
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

  private void physicsCalculateMovementClamp(User user, PhysicsProcessorContext context) {
    UserMetaMovementData movementData = user.meta().movementData();
    double resetMotion = movementData.resetMotion();
    if (Math.abs(context.motionX) < resetMotion) {
      context.motionX = 0.0;
    }
    if (Math.abs(context.motionY) < resetMotion) {
      context.motionY = 0.0;
    }
    if (Math.abs(context.motionZ) < resetMotion) {
      context.motionZ = 0.0;
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

  private PreciseCollisionResult physicsCalculateCollision(
    User user, PhysicsProcessorContext context,
    boolean inWeb,
    double positionX, double positionY, double positionZ
  ) {
    Player player = user.bukkitPlayer();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();

    if (inWeb) {
      context.motionX *= 0.25D;
      context.motionY *= 0.05f;
      context.motionZ *= 0.25D;
    }

    double startMotionX = context.motionX;
    double startMotionY = context.motionY;
    double startMotionZ = context.motionZ;

    boolean safeWalkActive = movementData.onGround && movementData.sneaking;
    if (safeWalkActive) {
      WrappedAxisAlignedBB boundingBox = movementData.boundingBox();

      double d6;

      for (d6 = 0.05D; context.motionX != 0.0D && CollisionFactory.getCollisionBoxes(player, boundingBox.offset(context.motionX, -1.0D, 0.0D)).isEmpty(); startMotionX = context.motionX) {
        if (context.motionX < d6 && context.motionX >= -d6) {
          context.motionX = 0.0D;
        } else if (context.motionX > 0.0D) {
          context.motionX -= d6;
        } else {
          context.motionX += d6;
        }
      }

      for (; context.motionZ != 0.0D && CollisionFactory.getCollisionBoxes(player, boundingBox.offset(0.0D, -1.0D, context.motionZ)).isEmpty(); startMotionZ = context.motionZ) {
        if (context.motionZ < d6 && context.motionZ >= -d6) {
          context.motionZ = 0.0D;
        } else if (context.motionZ > 0.0D) {
          context.motionZ -= d6;
        } else {
          context.motionZ += d6;
        }
      }

      for (; context.motionX != 0.0D && context.motionZ != 0.0D && CollisionFactory.getCollisionBoxes(player, boundingBox.offset(context.motionX, -1.0D, context.motionZ)).isEmpty(); startMotionZ = context.motionZ) {
        if (context.motionX < d6 && context.motionX >= -d6) {
          context.motionX = 0.0D;
        } else if (context.motionX > 0.0D) {
          context.motionX -= d6;
        } else {
          context.motionX += d6;
        }

        startMotionX = context.motionX;

        if (context.motionZ < d6 && context.motionZ >= -d6) {
          context.motionZ = 0.0D;
        } else if (context.motionZ > 0.0D) {
          context.motionZ -= d6;
        } else {
          context.motionZ += d6;
        }
      }
    }

    List<WrappedAxisAlignedBB> collisionBoxes = CollisionFactory.getCollisionBoxes(
      player,
      movementData.boundingBox().addCoord(context.motionX, context.motionY, context.motionZ)
    );
    WrappedAxisAlignedBB startBoundingBox = movementData.boundingBox();
    WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      context.motionY = collisionBox.calculateYOffset(entityBoundingBox, context.motionY);
    }
    entityBoundingBox = (entityBoundingBox.offset(0.0D, context.motionY, 0.0D));
    boolean flag1 = movementData.lastOnGround || startMotionY != context.motionY && startMotionY < 0.0D;

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      context.motionX = collisionBox.calculateXOffset(entityBoundingBox, context.motionX);
    }
    entityBoundingBox = entityBoundingBox.offset(context.motionX, 0.0D, 0.0D);

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      context.motionZ = collisionBox.calculateZOffset(entityBoundingBox, context.motionZ);
    }
    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, context.motionZ);

    if (flag1 && (startMotionX != context.motionX || startMotionZ != context.motionZ)) {
      double copyX = context.motionX;
      double copyY = context.motionY;
      double copyZ = context.motionZ;
      WrappedAxisAlignedBB axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      context.motionY = STEP_HEIGHT;
      List<WrappedAxisAlignedBB> list = CollisionFactory.getCollisionBoxes(
        player,
        entityBoundingBox.addCoord(startMotionX, context.motionY, startMotionZ)
      );
      WrappedAxisAlignedBB axisalignedbb4 = entityBoundingBox;
      WrappedAxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(startMotionX, 0.0D, startMotionZ);
      double d9 = context.motionY;

      for (WrappedAxisAlignedBB axisalignedbb6 : list) {
        d9 = axisalignedbb6.calculateYOffset(axisalignedbb5, d9);
      }

      axisalignedbb4 = axisalignedbb4.offset(0.0D, d9, 0.0D);
      double d15 = startMotionX;

      for (WrappedAxisAlignedBB axisalignedbb7 : list) {
        d15 = axisalignedbb7.calculateXOffset(axisalignedbb4, d15);
      }

      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0D, 0.0D);
      double d16 = startMotionZ;

      for (WrappedAxisAlignedBB axisalignedbb8 : list) {
        d16 = axisalignedbb8.calculateZOffset(axisalignedbb4, d16);
      }

      axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d16);
      WrappedAxisAlignedBB axisalignedbb14 = entityBoundingBox;
      double d17 = context.motionY;

      for (WrappedAxisAlignedBB axisalignedbb9 : list) {
        d17 = axisalignedbb9.calculateYOffset(axisalignedbb14, d17);
      }

      axisalignedbb14 = axisalignedbb14.offset(0.0D, d17, 0.0D);
      double d18 = startMotionX;

      for (WrappedAxisAlignedBB axisalignedbb10 : list) {
        d18 = axisalignedbb10.calculateXOffset(axisalignedbb14, d18);
      }

      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0D, 0.0D);
      double d19 = startMotionZ;

      for (WrappedAxisAlignedBB axisalignedbb11 : list) {
        d19 = axisalignedbb11.calculateZOffset(axisalignedbb14, d19);
      }

      axisalignedbb14 = axisalignedbb14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;

      if (d20 > d10) {
        context.motionX = d15;
        context.motionZ = d16;
        context.motionY = -d9;
        entityBoundingBox = axisalignedbb4;
      } else {
        context.motionX = d18;
        context.motionZ = d19;
        context.motionY = -d17;
        entityBoundingBox = axisalignedbb14;
      }

      for (WrappedAxisAlignedBB axisalignedbb12 : list) {
        context.motionY = axisalignedbb12.calculateYOffset(entityBoundingBox, context.motionY);
      }

      entityBoundingBox = entityBoundingBox.offset(0.0, context.motionY, 0.0);

      if (copyX * copyX + copyZ * copyZ >= context.motionX * context.motionX + context.motionZ * context.motionZ) {
        context.motionX = copyX;
        context.motionY = copyY;
        context.motionZ = copyZ;
        entityBoundingBox = axisalignedbb3;
      }
    }

    boolean collidedVertically = startMotionY != context.motionY;
    boolean collidedHorizontally = startMotionX != context.motionX || startMotionZ != context.motionZ;
    boolean onGround = startMotionY != context.motionY && startMotionY < 0.0;
    boolean moveResetX = startMotionX != context.motionX;
    boolean moveResetZ = startMotionZ != context.motionZ;

    double newPositionX = (entityBoundingBox.minX + entityBoundingBox.maxX) / 2.0D;
    double newPositionY = entityBoundingBox.minY;
    double newPositionZ = (entityBoundingBox.minZ + entityBoundingBox.maxZ) / 2.0D;
    context.motionX = newPositionX - positionX;
    context.motionY = newPositionY - positionY;
    context.motionZ = newPositionZ - positionZ;

    return new PreciseCollisionResult(PhysicsProcessorContext.from(context), onGround, collidedHorizontally, collidedVertically, moveResetX, moveResetZ);
  }

  private static final class PreciseCollisionResult {
    private final PhysicsProcessorContext context;
    private final boolean onGround, collidedHorizontally, collidedVertically;
    private final boolean resetMotionX, resetMotionZ;

    public PreciseCollisionResult(
      PhysicsProcessorContext context, boolean onGround,
      boolean collidedHorizontally, boolean collidedVertically,
      boolean resetMotionX, boolean resetMotionZ
    ) {
      this.context = context;
      this.onGround = onGround;
      this.collidedHorizontally = collidedHorizontally;
      this.collidedVertically = collidedVertically;
      this.resetMotionX = resetMotionX;
      this.resetMotionZ = resetMotionZ;
    }
  }

  public static final class PhysicsProcessorContext {
    private double motionX;
    private double motionY;
    private double motionZ;

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