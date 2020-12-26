package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.detect.checks.movement.physics.collision.PhysicsCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.water.*;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementLocaleHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.inventory.InventoryUseItemHelper;
import de.jpx3.intave.tools.inventory.PlayerEnchantmentHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.CollisionFactory;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
  private final static boolean MOVEMENT_EMULATION = false;
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
    } else if (minecraftVersion.isAtLeast(MinecraftVersion.AQUATIC_UPDATE)) {
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
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaClientData clientData = meta.clientData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    boolean inventoryOpen = inventoryData.inventoryOpen();

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
    if (sprinting && sneaking && !clientData.sprintWhenSneaking()) {
      sprinting = false;
    }
    if (inventoryOpen) {
      sprinting = false;
    }

    int lastForwardKey = movementData.keyForward;
    int lastStrafeKey = movementData.keyStrafe;

    long startTime = System.nanoTime();

    /*
    Physics process
     */
    context.flyingPacketAccurate = false;
    PhysicsEntityMovementData predictedMovement = physicsFast(user, friction, sprinting, sneaking, yawSine, yawCosine);

    Vector moveVector = predictedMovement.moveVector;
    double differenceX = moveVector.getX() - receivedMotionX;
    double differenceY = moveVector.getY() - receivedMotionY;
    double differenceZ = moveVector.getZ() - receivedMotionZ;
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
    if (DEBUG_PERFORMANCE) {
      double endTime = (System.nanoTime() - startTime) / 1_000_000.0;
      System.out.println("[Intave] Physics-Performance-Debug: " + endTime + " ms/c");
    }

    evaluateMovement(
      user, predictedMovement,
      lastForwardKey, lastStrafeKey,
      context.flyingPacketAccurate
    );


    movementData.onGround = predictedMovement.onGround;
    movementData.collidedHorizontally = predictedMovement.collidedHorizontally;
    movementData.collidedVertically = predictedMovement.collidedVertically;
    movementData.physicsResetMotionX = predictedMovement.resetMotionX;
    movementData.physicsResetMotionZ = predictedMovement.resetMotionZ;
    movementData.pastRiptideSpin++;
  }

  private PhysicsEntityMovementData physicsAccurate(
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
    boolean isOnLadder = PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ);
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
    PhysicsEntityMovementData predictedMovement = null;

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
        if (attackReduce && movementData.pastPlayerAttackPhysics > 2) {
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
                sneaking, attackReduce, jumped, sprinting, handActive, isOnLadder
              );

              PhysicsEntityMovementData contextMovement = resolveMoveVector(
                user, inWeb,
                positionX, positionY, positionZ,
                context.predictedX, context.predictedY, context.predictedZ
              );
              Vector moveVector = contextMovement.moveVector;
              double differenceX = moveVector.getX() - receivedMotionX;
              double differenceY = moveVector.getY() - receivedMotionY;
              double differenceZ = moveVector.getZ() - receivedMotionZ;
              double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);
              if (distance < mostAccurateDistance) {
                predictedMovement = contextMovement;
                mostAccurateDistance = distance;
                bestForwardKey = keyForward;
                bestStrafeKey = keyStrafe;
              }
              boolean fastMovementProcess = (!isOnLadder && !inWater && inLava) || elytraFlying;
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

  private PhysicsEntityMovementData physicsFast(
    User user, float friction,
    boolean sprinting, boolean sneaking,
    float yawSine, float yawCosine
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    PhysicsProcessorContext context = movementData.physicsProcessorContext;

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean isOnLadder = PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean handActive = inventoryData.handActive();
    boolean attackReduce = movementData.pastPlayerAttackPhysics == 0;
    boolean jumped = movementData.jumpUpwardsMotion() == movementData.motionY();

    context.reset(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ);
    physicsCalculate(
      user, context, yawSine, yawCosine, friction, keyForward, keyStrafe,
      sneaking, attackReduce, jumped, sprinting, handActive, isOnLadder
    );

    boolean inWeb = movementData.inWeb;
    return resolveMoveVector(
      user, inWeb,
      positionX, positionY, positionZ,
      context.predictedX, context.predictedY, context.predictedZ
    );
  }

  private void physicsCalculate(
    User user, PhysicsProcessorContext context,
    float yawSine, float yawCosine, float friction,
    int keyForward, int keyStrafe,
    boolean sneaking, boolean attackReduce,
    boolean jumped, boolean sprinting,
    boolean handActive, boolean isOnLadder
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    float moveStrafe = keyStrafe * 0.98f;
    float moveForward = keyForward * 0.98f;
    float rotationYaw = movementData.rotationYaw;
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
        context.predictedY -= 0.04F;
      }
    }
    if (handActive) {
      moveStrafe *= 0.2f;
      moveForward *= 0.2f;
    }

    if (attackReduce) {
      context.predictedX *= 0.6;
      context.predictedZ *= 0.6;
    }

    if (jumped) {
      if (inWater) {
        context.predictedY += 0.04F;
      } else if (inLava) {
        // #handleJumpLava
        context.predictedY += 0.03999999910593033D;
      } else {
        context.predictedY = movementData.jumpUpwardsMotion();
        if (sprinting) {
          context.predictedX -= yawSine * 0.2F;
          context.predictedZ += yawCosine * 0.2F;
        }
      }
    }

    if (waterUpdate && swimming) {
      double d3 = movementData.lookVector.getY();
      double d4 = d3 < -0.2D ? 0.085D : 0.06D;
      boolean fluidStateEmpty = aquaticWaterMovement.fluidStateEmpty(user, positionX, positionY + 1.0 - 0.1, positionZ);
      if (d3 <= 0.0D || jumped || !fluidStateEmpty) {
        context.predictedY += (d3 - context.predictedY) * d4;
      }
    }

    if (inWater) {
      physicsCalculateWater(user, context, moveForward, moveStrafe, yawSine, yawCosine);
    } else if (elytraFlying) {
      physicsCalculateElytra(movementData.lookVector, context, rotationPitch, rotationYaw, gravity);
    } else if (inLava) {
      physicsCalculateLava(context, moveForward, moveStrafe, yawSine, yawCosine);
    } else {
      physicsCalculateNormal(user, context, moveForward, moveStrafe, yawSine, yawCosine, friction, isOnLadder);
    }
  }

  private void physicsCalculateWater(
    User user, PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    Player player = user.bukkitPlayer();
    UserMetaMovementData movementData = user.meta().movementData();

    float f = moveStrafe * moveStrafe + moveForward * moveForward;
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

    if (f >= 1.0E-4F) {
      f = (float) Math.sqrt(f);
      f = f2 / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      context.predictedX += moveStrafe * yawCosine - moveForward * yawSine;
      context.predictedZ += moveForward * yawCosine + moveStrafe * yawSine;
    }
  }

  private void physicsCalculateElytra(
    Vector lookVector, PhysicsProcessorContext context,
    float rotationPitch, float rotationYaw,
    double gravity
  ) {
    float f = rotationPitch * 0.017453292F;
    double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
    double dist2 = Math.sqrt(context.predictedX * context.predictedX + context.predictedZ * context.predictedZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = WrappedMathHelper.cos(f);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
//                predictedMotionY += -0.08 + (double) f4 * 0.06D;
    context.predictedY += gravity * (-1 + pitchCosine * 0.75);

    if (context.predictedY < 0.0D && rotationVectorDistance > 0.0D) {
      double d2 = context.predictedY * -0.1D * (double) pitchCosine;
      context.predictedY += d2;
      context.predictedX += lookVector.getX() * d2 / rotationVectorDistance;
      context.predictedZ += lookVector.getZ() * d2 / rotationVectorDistance;
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
      context.predictedY += d9 * 3.2D;
      context.predictedX += -lookVector.getX() * d9 / rotationVectorDistance;
      context.predictedZ += -lookVector.getZ() * d9 / rotationVectorDistance;
//                  vector3d = vector3d.add(-vector3d1.x * d9 / d1, d9 * 3.2D, -vector3d1.z * d9 / d1);
    }

    // 1.9
    if (rotationVectorDistance > 0.0D) {
      context.predictedX += (lookVector.getX() / rotationVectorDistance * dist2 - context.predictedX) * 0.1D;
      context.predictedZ += (lookVector.getZ() / rotationVectorDistance * dist2 - context.predictedZ) * 0.1D;
    }
    // 1.16
//                if (d6 > 0.0D) {
//                  predictedMotionX += (elytraMoveVector.getX() / d6 * d1 - predictedMotionX) * 0.1D;
//                  predictedMotionZ += (elytraMoveVector.getZ() / d6 * d1 - predictedMotionZ) * 0.1D;
//                }

    context.predictedX *= 0.99f;
    context.predictedY *= 0.98f;
    context.predictedZ *= 0.99f;
  }

  private void physicsCalculateLava(
    PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine
  ) {
    float f = moveStrafe * moveStrafe + moveForward * moveForward;
    float friction = 0.02f;
    if (f >= 1.0E-4F) {
      f = (float) Math.sqrt(f);
      f = friction / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      context.predictedX += moveStrafe * yawCosine - moveForward * yawSine;
      context.predictedZ += moveForward * yawCosine + moveStrafe * yawSine;
    }
  }

  private void physicsCalculateNormal(
    User user, PhysicsProcessorContext context,
    float moveForward, float moveStrafe,
    float yawSine, float yawCosine,
    float friction, boolean isOnLadder
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    float f = moveStrafe * moveStrafe + moveForward * moveForward;

    if (f >= 1.0E-4F) {
      f = (float) Math.sqrt(f);
      f = friction / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      context.predictedX += moveStrafe * yawCosine - moveForward * yawSine;
      context.predictedZ += moveForward * yawCosine + moveStrafe * yawSine;
    }

    if (isOnLadder) {
      float f6 = 0.15F;
      context.predictedX = WrappedMathHelper.clamp_double(context.predictedX, -f6, f6);
      context.predictedZ = WrappedMathHelper.clamp_double(context.predictedZ, -f6, f6);
      if (context.predictedY < -0.15D) {
        context.predictedY = -0.15D;
      }
      if (movementData.sneaking && context.predictedY < 0.0D) {
        context.predictedY = 0.0D;
      }
    }

    if (!context.flyingPacketAccurate) {
      double motionDistance = MathHelper.resolveDistance(context.predictedX, context.predictedY, context.predictedZ);
      context.flyingPacketAccurate = motionDistance < 0.009;
    }

    Vector flyingVector = resolveFlyingVectorMidAir(user, context.predictedX, context.predictedY, context.predictedZ);
    if (flyingVector != null) {
      context.predictedX = flyingVector.getX();
      context.predictedY = flyingVector.getY();
      context.predictedZ = flyingVector.getZ();
    }
  }

  private boolean lenientItemUsageChecking(User user) {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItemStack = inventoryData.heldItem();
    return heldItemStack != null && heldItemStack.getType() == InventoryUseItemHelper.ITEM_TRIDENT;
  }

  private void evaluateMovement(
    User user,
    PhysicsEntityMovementData expectedMovement,
    int lastKeyForward, int lastKeyStrafe,
    boolean flyingPacketAccurate
  ) {
    Player player = user.bukkitPlayer();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaAbilityData abilityData = meta.abilityData();

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

    Vector moveVector = expectedMovement.moveVector;
    double predictedX = moveVector.getX();
    double predictedY = moveVector.getY();
    double predictedZ = moveVector.getZ();

    double differenceX = moveVector.getX() - receivedMotionX;
    double differenceY = moveVector.getY() - receivedMotionY;
    double differenceZ = moveVector.getZ() - receivedMotionZ;
    double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);

    double hDistance = Math.hypot(
      receivedPositionX - movementData.verifiedPositionX,
      receivedPositionZ - movementData.verifiedPositionZ
    );

    // A + D; W + S; spam on the ground
    if (expectedMovement.onGround && hDistance < 0.2) {
      boolean forwardCritical = keyForward != lastKeyForward;
      boolean strafeCritical = keyStrafe != lastKeyStrafe;

      if ((forwardCritical || strafeCritical) || hDistance < 0.01) {
        flyingPacketAccurate = true;
      }
    }

    if (flyingPacketAccurate) {
      movementData.pastFlyPacket = 0;
    } else {
      movementData.pastFlyPacket++;
    }

    double verticalViolationIncrease = resolveVerticalViolationIncrease(user, predictedY);
    double horizontalViolationIncrease = resolveHorizontalViolationIncrease(user, predictedX, predictedZ);
    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;

    if (flying) {
      violationLevelIncrease = 0;
    }

    if (movementData.pastVelocity < 10) {
      if (violationLevelIncrease > 0) {
        violationLevelIncrease = Math.max(violationLevelIncrease, 1.0);
      }
      violationLevelIncrease *= 2.5;
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.980;
      violationLevelData.physicsVL -= 0.012;
    }

    // Update the player's verified location
    if (violationLevelIncrease == 0) {
      movementData.verifiedLocation = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ);
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

      if (violationLevelData.physicsVL > 20 && MOVEMENT_EMULATION) {
        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, 7);
      }
    }

    violationLevelData.physicsVL = Math.max(0, violationLevelData.physicsVL);
    violationLevelData.physicsVL = Math.min(35, violationLevelData.physicsVL);

    if (DEBUG_MOVEMENT) {
      ChatColor chatColor = violationLevelIncrease == 0 ? ChatColor.GRAY : ChatColor.YELLOW;
      String position = MathHelper.formatPositionAsInt(receivedPositionX, receivedPositionY, receivedPositionZ);
      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 4);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 3);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 3);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 3);

      if (movementData.pastFlyPacket <= 1) {
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
      player.sendMessage(debug + " dist=" + formatDouble(distance, 10));
    }
  }

  private double resolveVerticalViolationIncrease(
    User user,
    double predictedY
  ) {
    UserMetaMovementData movementData = user.meta().movementData();

    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    boolean swimming = movementData.swimming;
    boolean elytraFlying = movementData.elytraFlying;
    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;

    double receivedMotionY = movementData.motionY();
    double differenceY = Math.abs(receivedMotionY - predictedY);

    boolean accountedSkippedMovement = movementData.pastFlyPacket <= 2;
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

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);
    double multiplier = abuseVertically > 0.3 ? 45.0 : 25.0;

    if (movementData.pastWaterMovement < 5 || movementData.inLava()) {
      multiplier *= 0.4;
    }

    return abuseVertically * multiplier;
  }

  private double resolveHorizontalViolationIncrease(
    User user,
    double predictedX, double predictedZ
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    double motionX = movementData.motionX();
    double motionZ = movementData.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );

    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;

    double legitimateDeviation = 7e-4;

    if (movementData.pastWaterMovement < 10) {
      legitimateDeviation = 0.01;
    }

    if (pushedByWaterFlow) {
      legitimateDeviation = 0.028;
    }

    // Flying packet
    if (movementData.pastFlyPacket <= 2) {
      legitimateDeviation = movementData.onGround ? 0.155 : 0.009;
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    double blindDistance = movementData.inWater ? 0.09 : 0.07;
    if (distanceMoved < blindDistance) {
      legitimateDeviation = Math.max(blindDistance * 0.7, legitimateDeviation);
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    double abuseHorizontally = Math.max(0, distance - legitimateDeviation);
    return abuseHorizontally * (abuseHorizontally > 0.1 ? 20.0 : 10.0);
  }

  private final static double RIPTIDE_TOLERANCE = 3.005;
  private final static double RIPTIDE_TOLERANCE_2 = 0.05;
  private final static double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(UserMetaMovementData movementData) {
    double riptideTolerance;
    if (movementData.onGround) {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_GROUND_TOLERANCE_2;
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

    boolean elytraFlying = PlayerMovementLocaleHelper.flyingWithElytra(player);

    boolean inWater = movementData.inWater;
    boolean inLava = movementData.inLava();
    boolean collidedHorizontally = movementData.collidedHorizontally;
    double gravity = movementData.gravity;

    double multiplier;
    if (movementData.lastOnGround) {
      double blockPositionX = WrappedMathHelper.floor(movementData.verifiedPositionX);
      double blockPositionY = WrappedMathHelper.floor(movementData.verifiedPositionY - 1.0);
      double blockPositionZ = WrappedMathHelper.floor(movementData.verifiedPositionZ);
      Location blockBelow = new Location(world, blockPositionX, blockPositionY, blockPositionZ);
      multiplier = PlayerMovementHelper.resolveSlipperiness(blockBelow);
    } else {
      multiplier = 0.91f;
    }

    WrappedAxisAlignedBB entityBoundingBox = CollisionHelper.entityBoundingBoxOf(user, positionX, positionY, positionZ);
    movementData.setBoundingBox(entityBoundingBox);

    if (movementData.inWeb) {
      motionX = 0.0;
      motionY = 0.0;
      motionZ = 0.0;
      movementData.inWeb = false;
    }

    if (clientData.motionResetOnCollision()) {
      if (movementData.physicsResetMotionX) {
        motionX = 0.0;
      }
      if (movementData.physicsResetMotionZ) {
        motionZ = 0.0;
      }
    }

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
        motionX, movementData.physicsLastMotionY, motionZ
      );
      if (collisionVector != null) {
        motionX = collisionVector.getX();
        motionY = collisionVector.getY();
        motionZ = collisionVector.getZ();
      } else {
        motionY = 0.0;
      }
    }

    // EntityCollidedWithBlock
    if (movementData.onGround && !movementData.sneaking) {
      Vector collisionVector = collisionRepository.entityCollision(
        user, block.getType(),
        motionX, motionY, motionZ
      );
      if (collisionVector != null) {
        motionX = collisionVector.getX();
        motionY = collisionVector.getY();
        motionZ = collisionVector.getZ();
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
            motionX, motionY, motionZ
          );
          if (collisionVector != null) {
            motionX = collisionVector.getX();
            motionY = collisionVector.getY();
            motionZ = collisionVector.getZ();
          }
        }
      }
    }

    if (clientData.protocolVersion() >= PROTOCOL_VERSION_VILLAGE_UPDATE) {
      int soulSandModifier = PlayerEnchantmentHelper.resolveSoulSpeedModifier(player);
      if (soulSandModifier == 0) {
        Block blockAccess = BlockAccessor.blockAccess(world, positionX, positionY - 0.6, positionZ);
        Material material = blockAccess.getType();
        Vector speedFactor = collisionRepository.speedFactor(user, material, motionX, motionY, motionZ);
        if (speedFactor != null) {
          motionX = speedFactor.getX();
          motionY = speedFactor.getY();
          motionZ = speedFactor.getZ();
        }
      }
    }

    if (inWater) {
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

      motionX *= f1;
      motionY *= 0.8f;
      motionZ *= f1;

      if (!clientData.waterUpdate()) {
        motionY -= 0.02D;
      }

      if (clientData.waterUpdate() && !movementData.sprinting) {
        if (motionY <= 0.0D && Math.abs(motionY - 0.005D) >= 0.003D && Math.abs(motionY - gravity / 16.0D) < 0.003D) {
          motionY = -0.003D;
        } else {
          motionY -= gravity / 16.0D;
        }
      }

      double liquidPositionY;
      if (clientData.waterUpdate()) {
        liquidPositionY = motionY + 0.6f - positionY + movementData.verifiedPositionY;
      } else {
        liquidPositionY = motionY + 0.6f;
      }

      boolean offsetPositionInLiquid = PlayerMovementHelper.isOffsetPositionInLiquid(
        player, entityBoundingBox, motionX, liquidPositionY, motionZ
      );
      if (collidedHorizontally && offsetPositionInLiquid) {
        motionY = 0.3f;
      }
    } else if (inLava) {
      motionX *= 0.5D;
      motionY *= 0.5D;
      motionZ *= 0.5D;
      motionY -= 0.02D;

      boolean offsetPositionInLiquid = PlayerMovementHelper.isOffsetPositionInLiquid(
        player, entityBoundingBox,
        motionX,
        motionY + 0.6f - positionY + movementData.verifiedPositionY,
        motionZ
      );
      if (collidedHorizontally && offsetPositionInLiquid) {
        motionY = 0.30000001192092896D;
      }
    } else if (!elytraFlying) {
      if (PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ) && collidedHorizontally) {
        motionY = 0.2;
      }

      if (PlayerEffectHelper.isPotionLevitationActive(player)) {
        int levitationAmplifier = PlayerEffectHelper.effectAmplifier(player, PlayerEffectHelper.EFFECT_LEVITATION);
        motionY += (0.05D * (double) (levitationAmplifier + 1) - motionY) * 0.2D;
      } else {
        motionY -= gravity;
      }

      motionX *= multiplier;
      motionY *= 0.98f;
      motionZ *= multiplier;
    }

    double resetMotion = movementData.resetMotion();
    if (Math.abs(motionX) < resetMotion) {
      motionX = 0.0;
    }
    if (Math.abs(motionY) < resetMotion) {
      motionY = 0.0;
    }
    if (Math.abs(motionZ) < resetMotion) {
      motionZ = 0.0;
    }

    movementData.physicsLastMotionX = motionX;
    movementData.physicsLastMotionY = motionY;
    movementData.physicsLastMotionZ = motionZ;

    updateAquatics(user);

    movementData.verifiedPositionX = positionX;
    movementData.verifiedPositionY = positionY;
    movementData.verifiedPositionZ = positionZ;

    movementData.lastOnGround = movementData.onGround;
    movementData.lastSprinting = movementData.sprinting;
    movementData.lastSneaking = movementData.sneaking;
    movementData.pastPlayerAttackPhysics++;
    movementData.pastFlyPacket++;
    movementData.pastPushedByWaterFlow++;
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

  private final static double FLYING_DEVIATION = 6e-3;
  private final static boolean DEBUG_FLYING = false;

  @Nullable
  private Vector resolveFlyingVectorMidAir(User user, double predictedX, double predictedY, double predictedZ) {
    Player player = user.bukkitPlayer();
    UserMetaMovementData movementData = user.meta().movementData();

    if (Math.abs(predictedY - movementData.motionY()) <= FLYING_DEVIATION) {
      return null;
    }

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onGround;
    float accumulation = 0.91f;
    if (movementData.lastOnGround) {
      accumulation = 0.6f;
    }
    double resetMotion = movementData.resetMotion();
    double jumpUpwardsMotion = movementData.jumpUpwardsMotion();

    double i = 0;
    double flyingMotionX = predictedX;
    double flyingMotionY = predictedY;
    double flyingMotionZ = predictedZ;

    for (i++; i <= 2; i++) {
      CollisionHelper.CollisionResult collisionResult = CollisionHelper.resolveQuickCollisions(
        player, positionX, positionY, positionZ,
        flyingMotionX, flyingMotionY, flyingMotionZ
      );
      flyingMotionX = collisionResult.motionX();
      flyingMotionY = collisionResult.motionY();
      flyingMotionZ = collisionResult.motionZ();
      onGround = collisionResult.onGround();

      // Packet comparison
      if (onGround) {
        double jumpFlyingMotionY = flyingMotionY + jumpUpwardsMotion;
        if (Math.abs(jumpFlyingMotionY - movementData.motionY()) < FLYING_DEVIATION && jumpFlyingMotionY < jumpUpwardsMotion) {
          if (i == 1) {
            predictedX = flyingMotionX;
            predictedZ = flyingMotionZ;
            predictedY = jumpFlyingMotionY;
            if (DEBUG_FLYING) {
              player.sendMessage("@f jump:" + jumpFlyingMotionY + ", loop:" + i);
            }
            break;
          }
        }
      } else {
        double vDistance = Math.abs(flyingMotionY - movementData.motionY());
        if (vDistance < FLYING_DEVIATION && !collisionResult.collidedVertically()) {
          predictedX = flyingMotionX;
          predictedY = flyingMotionY;
          predictedZ = flyingMotionZ;
          if (DEBUG_FLYING) {
            player.sendMessage("@f air:" + predictedY + ", recv:" + movementData.motionY());
          }
          break;
        }
      }

      if (onGround) {
        flyingMotionY = 0.0;
      }

      flyingMotionX *= accumulation;
      flyingMotionY -= 0.08;
      flyingMotionY *= 0.98f;
      flyingMotionZ *= accumulation;

      positionX += flyingMotionX;
      positionY += flyingMotionY;
      positionZ += flyingMotionZ;

      if (Math.abs(flyingMotionX) < resetMotion) {
        flyingMotionX = 0.0;
      }
      if (Math.abs(flyingMotionY) < resetMotion) {
        flyingMotionY = 0.0;
      }
      if (Math.abs(flyingMotionZ) < resetMotion) {
        flyingMotionZ = 0.0;
      }
    }

    return new Vector(predictedX, predictedY, predictedZ);
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

  private Physics.PhysicsEntityMovementData resolveMoveVector(
    User user, boolean inWeb,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    Player player = user.bukkitPlayer();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();

    if (inWeb) {
      motionX *= 0.25D;
      motionY *= 0.05f;
      motionZ *= 0.25D;
    }

    double startMotionX = motionX;
    double startMotionY = motionY;
    double startMotionZ = motionZ;

    boolean safeWalkActive = movementData.onGround && movementData.lastSneaking;
    if (safeWalkActive) {
      double d6;

      for (d6 = 0.05D; motionX != 0.0D && CollisionFactory.getCollisionBoxes(player, movementData.boundingBox().offset(motionX, -1.0D, 0.0D)).isEmpty(); startMotionX = motionX) {
        if (motionX < d6 && motionX >= -d6) {
          motionX = 0.0D;
        } else if (motionX > 0.0D) {
          motionX -= d6;
        } else {
          motionX += d6;
        }
      }

      for (; motionZ != 0.0D && CollisionFactory.getCollisionBoxes(player, movementData.boundingBox().offset(0.0D, -1.0D, motionZ)).isEmpty(); startMotionZ = motionZ) {
        if (motionZ < d6 && motionZ >= -d6) {
          motionZ = 0.0D;
        } else if (motionZ > 0.0D) {
          motionZ -= d6;
        } else {
          motionZ += d6;
        }
      }

      for (; motionX != 0.0D && motionZ != 0.0D && CollisionFactory.getCollisionBoxes(player, movementData.boundingBox().offset(motionX, -1.0D, motionZ)).isEmpty(); startMotionZ = motionZ) {
        if (motionX < d6 && motionX >= -d6) {
          motionX = 0.0D;
        } else if (motionX > 0.0D) {
          motionX -= d6;
        } else {
          motionX += d6;
        }

        startMotionX = motionX;

        if (motionZ < d6 && motionZ >= -d6) {
          motionZ = 0.0D;
        } else if (motionZ > 0.0D) {
          motionZ -= d6;
        } else {
          motionZ += d6;
        }
      }
    }

    List<WrappedAxisAlignedBB> collisionBoxes = CollisionFactory.getCollisionBoxes(
      player,
      movementData.boundingBox().addCoord(motionX, motionY, motionZ)
    );
    WrappedAxisAlignedBB startBoundingBox = movementData.boundingBox();
    WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionY = collisionBox.calculateYOffset(entityBoundingBox, motionY);
    }
    entityBoundingBox = (entityBoundingBox.offset(0.0D, motionY, 0.0D));
    boolean flag1 = movementData.lastOnGround || startMotionY != motionY && startMotionY < 0.0D;

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionX = collisionBox.calculateXOffset(entityBoundingBox, motionX);
    }
    entityBoundingBox = entityBoundingBox.offset(motionX, 0.0D, 0.0D);

    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionZ = collisionBox.calculateZOffset(entityBoundingBox, motionZ);
    }
    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, motionZ);

    if (flag1 && (startMotionX != motionX || startMotionZ != motionZ)) {
      double copyX = motionX;
      double copyY = motionY;
      double copyZ = motionZ;
      WrappedAxisAlignedBB axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      motionY = STEP_HEIGHT;
      List<WrappedAxisAlignedBB> list = CollisionFactory.getCollisionBoxes(
        player,
        entityBoundingBox.addCoord(startMotionX, motionY, startMotionZ)
      );
      WrappedAxisAlignedBB axisalignedbb4 = entityBoundingBox;
      WrappedAxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(startMotionX, 0.0D, startMotionZ);
      double d9 = motionY;

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
      double d17 = motionY;

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
        motionX = d15;
        motionZ = d16;
        motionY = -d9;
        entityBoundingBox = axisalignedbb4;
      } else {
        motionX = d18;
        motionZ = d19;
        motionY = -d17;
        entityBoundingBox = axisalignedbb14;
      }

      for (WrappedAxisAlignedBB axisalignedbb12 : list) {
        motionY = axisalignedbb12.calculateYOffset(entityBoundingBox, motionY);
      }

      entityBoundingBox = entityBoundingBox.offset(0.0, motionY, 0.0);

      if (copyX * copyX + copyZ * copyZ >= motionX * motionX + motionZ * motionZ) {
        motionX = copyX;
        motionY = copyY;
        motionZ = copyZ;
        entityBoundingBox = axisalignedbb3;
      }
    }

    boolean collidedVertically = startMotionY != motionY;
    boolean collidedHorizontally = startMotionX != motionX || startMotionZ != motionZ;
    boolean onGround = startMotionY != motionY && startMotionY < 0.0;
    boolean moveResetX = startMotionX != motionX;
    boolean moveResetZ = startMotionZ != motionZ;

    double newPositionX = (entityBoundingBox.minX + entityBoundingBox.maxX) / 2.0D;
    double newPositionY = entityBoundingBox.minY;
    double newPositionZ = (entityBoundingBox.minZ + entityBoundingBox.maxZ) / 2.0D;
    Vector moveVector = new Vector(
      newPositionX - positionX,
      newPositionY - positionY,
      newPositionZ - positionZ
    );

    return new Physics.PhysicsEntityMovementData(
      moveVector, onGround,
      collidedHorizontally, collidedVertically,
      moveResetX, moveResetZ
    );
  }

  private static final class PhysicsEntityMovementData {
    private final Vector moveVector;
    private final boolean onGround, collidedHorizontally, collidedVertically;
    private final boolean resetMotionX, resetMotionZ;

    public PhysicsEntityMovementData(
      Vector moveVector, boolean onGround,
      boolean collidedHorizontally, boolean collidedVertically,
      boolean resetMotionX, boolean resetMotionZ
    ) {
      this.moveVector = moveVector;
      this.onGround = onGround;
      this.collidedHorizontally = collidedHorizontally;
      this.collidedVertically = collidedVertically;
      this.resetMotionX = resetMotionX;
      this.resetMotionZ = resetMotionZ;
    }
  }

  public static final class PhysicsProcessorContext {
    private double predictedX;
    private double predictedY;
    private double predictedZ;
    private boolean flyingPacketAccurate;

    public void reset(double x, double y, double z) {
      this.predictedX = x;
      this.predictedY = y;
      this.predictedZ = z;
    }
  }
}