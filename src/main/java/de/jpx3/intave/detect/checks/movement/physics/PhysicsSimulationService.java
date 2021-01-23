package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionResult;
import de.jpx3.intave.detect.checks.movement.physics.pose.PhysicsCalculationPart;
import de.jpx3.intave.detect.checks.movement.physics.pose.PhysicsMovementPoseType;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.inventory.ItemStack;

public final class PhysicsSimulationService {
  private final static boolean SIMULATE_USE_ITEM_TWICE = true;

  public EntityCollisionResult simulate(User user, PhysicsMovementPoseType poseType) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaInventoryData inventoryData = meta.inventoryData();

    PhysicsCalculationPart calculationPart = poseType.calculationPart();
    boolean keyCalculation = calculationPart.requiresKeyCalculation();

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
      sneaking = false;
    }

    float yawSine = SinusCache.sin(movementData.rotationYaw * (float) Math.PI / 180.0F, false);
    float yawCosine = SinusCache.cos(movementData.rotationYaw * (float) Math.PI / 180.0F, false);
    float friction = PlayerMovementHelper.resolveFriction(user, movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ);

    if (keyCalculation) {
      EntityCollisionResult predictedMovement;
      predictedMovement = simulateMovementBiased(user, yawSine, yawCosine, friction, sprinting, sneaking);
      Physics.PhysicsProcessorContext context = predictedMovement.context();
      double biasedDistance = MathHelper.resolveDistance(
        context.motionX, context.motionY, context.motionZ,
        movementData.motionX(), movementData.motionY(), movementData.motionZ()
      );
      if (biasedDistance > 0.001) {
        predictedMovement = simulatePossibleMovement(user, yawSine, yawCosine, friction, sprinting, sneaking);
      }
      return predictedMovement;
    }

    return simulateMovementWithoutKeyPress(user, friction);
  }

  public EntityCollisionResult simulateMovementWithoutKeyPress(User user, float friction) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    PhysicsMovementPoseType movementPoseType = movementData.movementPoseType();
    Physics.PhysicsProcessorContext context = Physics.PhysicsProcessorContext.from(movementData.physicsProcessorContext);
    context.reset(movementData.physicsLastMotionX, movementData.physicsLastMotionY,movementData.physicsLastMotionZ);
    boolean sprinting = movementData.sprinting;
    boolean sneaking = movementData.sneaking;
    float yawSine = SinusCache.sin(movementData.rotationYaw * (float) Math.PI / 180.0F, false);
    float yawCosine = SinusCache.cos(movementData.rotationYaw * (float) Math.PI / 180.0F, false);
    return movementPoseType.calculationPart().performSimulation(
      user, context, yawSine, yawCosine, friction,
      0, 0, sneaking, false, false,
      sprinting, meta.inventoryData().handActive()
    );
  }

  private EntityCollisionResult simulateMovementBiased(
    User user,
    float yawSine, float yawCosine, float friction,
    boolean sprinting, boolean sneaking
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    PhysicsMovementPoseType movementPoseType = movementData.movementPoseType();
    PhysicsCalculationPart calculationPart = movementPoseType.calculationPart();
    Physics.PhysicsProcessorContext context = movementData.physicsProcessorContext;
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    if (inventoryData.inventoryOpen()) {
      keyForward = 0;
      keyStrafe = 0;
    }
    boolean handActive = inventoryData.handActive();
    boolean attackReduce = sprinting && movementData.pastPlayerAttackPhysics == 0;

    boolean jumped = false;
    if (movementData.lastOnGround && !inventoryData.inventoryOpen()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpUpwardsMotion();
    }

    float moveForward = keyForward * 0.98f;
    float moveStrafe = keyStrafe * 0.98f;
    context.reset(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ);
    return calculationPart.performSimulation(
      user, context, yawSine, yawCosine, friction, moveForward, moveStrafe,
      sneaking, attackReduce, jumped, sprinting, handActive
    );
  }

  private EntityCollisionResult simulatePossibleMovement(
    User user,
    float yawSine, float yawCosine, float friction,
    boolean sprinting, boolean sneaking
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    PhysicsMovementPoseType movementPoseType = movementData.movementPoseType();
    PhysicsCalculationPart calculationPart = movementPoseType.calculationPart();

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();

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
    Physics.PhysicsProcessorContext context = movementData.physicsProcessorContext;
    EntityCollisionResult predictedMovement = null;

    LOOP:
    for (int heldItemState = 0; heldItemState <= 1; heldItemState++) {
      boolean handActive = heldItemState == 1;

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

      for (int attackState = 0; attackState <= 1; attackState++) {
        boolean attackReduce = attackState == 1;
        if (attackReduce && movementData.pastPlayerAttackPhysics >= 1) {
          continue;
        }

        for (int jumpState = 0; jumpState <= 1; jumpState++) {
          boolean jumped = jumpState == 1;
          // Jumps are only allowed on the ground :(
          if (jumped && ((!lastOnGround && !inLava && !inWater) || inventoryOpen)) {
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
              float moveForward = keyForward * 0.98f;
              float moveStrafe = keyStrafe * 0.98f;
              context.reset(lastMotionX, lastMotionY, lastMotionZ);
              EntityCollisionResult collisionResult = calculationPart.performSimulation(
                user, context, yawSine, yawCosine, friction, moveForward, moveStrafe,
                sneaking, attackReduce, jumped, sprinting, handActive
              );
              Physics.PhysicsProcessorContext collisionContext = collisionResult.context();
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
              if (!calculationPart.requiresKeyCalculation()) {
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

  private boolean lenientItemUsageChecking(User user) {
    if (SIMULATE_USE_ITEM_TWICE) {
      return true;
    }
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItemStack = inventoryData.heldItem();
    return heldItemStack != null && heldItemStack.getType() == InventoryUseItemHelper.ITEM_TRIDENT;
  }
}