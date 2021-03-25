package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.detect.checks.movement.physics.simulators.PoseSimulator;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.event.dispatch.AttackDispatcher;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.reflect.ReflectiveDataWatcherAccess.DATA_WATCHER_BLOCKING_ID;

public final class SimulationProcessor {
  public ComplexColliderSimulationResult simulate(User user, Pose pose) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();

    PoseSimulator simulator = pose.simulator();
    boolean keyCalculation = simulator.requiresKeyCalculation();

    if (keyCalculation) {
      ComplexColliderSimulationResult predictedMovement;
      Timings.CHECK_PHYSICS_PROC_BIA.start();
      predictedMovement = simulateMovementBiased(user);
      double movementDistance = calculateMovementDistance(user, predictedMovement.context());
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      if (movementDistance > 0.001) {
        Timings.CHECK_PHYSICS_PROC_ITR.start();
        predictedMovement = simulatePossibleMovement(user);
        movementDistance = calculateMovementDistance(user, predictedMovement.context());
        Timings.CHECK_PHYSICS_PROC_ITR.stop();
      }

      if (inventoryData.handActive() && movementDistance > 0.001) {
        inventoryData.setHandActive(false);
        predictedMovement = simulatePossibleMovement(user);
        movementDistance = calculateMovementDistance(user, predictedMovement.context());

        if (movementDistance > 0.001) {
          // Movement is still wrong -> activate hand again
          inventoryData.setHandActive(true);
        } else {
          // Release the player's hand on the client and serverside
          ItemStack itemStack = inventoryData.heldItem();
          if (itemStack != null && !InventoryUseItemHelper.isSwordItem(user.player(), itemStack)) {
            if (movementData.physicsEatingSlotSwitchVL++ > 1) {
              inventoryData.applySlotSwitch();
            } else {
              inventoryData.setHandActive(true);
            }
          }
          Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(user.player(), DATA_WATCHER_BLOCKING_ID, false));
        }
      }

      return predictedMovement;
    }

    return simulateMovementWithoutKeyPress(user);
  }

  public ComplexColliderSimulationResult simulateMovementWithoutKeyPress(User user) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    Pose movementPoseType = movementData.movementPoseType();
    ProcessorMotionContext context = ProcessorMotionContext.from(movementData.processorMotionContext);
    context.resetTo(movementData);
    return movementPoseType.simulator().performSimulation(
      user, context,
      0, 0, false, false,
      meta.inventoryData().handActive()
    );
  }

  private ComplexColliderSimulationResult simulateMovementBiased(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator calculationPart = movementPoseType.simulator();
    ProcessorMotionContext context = movementData.processorMotionContext;
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean handActive = inventoryData.handActive();
    boolean attackReduce = !AttackDispatcher.REDUCING_DISABLED && movementData.sprintingAllowed() && user.meta().movementData().pastPlayerAttackPhysics == 0;

    boolean jumped = false;
    if (movementData.lastOnGround && !movementData.denyJump()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpUpwardsMotion();
    }

    if (movementData.sprinting && keyForward != 1) {
      keyForward = 0;
      keyStrafe = 0;
    }

    if (inventoryData.inventoryOpen()) {
      keyForward = 0;
      keyStrafe = 0;
    }

    float moveForward = keyForward * 0.98f;
    float moveStrafe = keyStrafe * 0.98f;
    movementData.physicsJumped = jumped;
    context.resetTo(movementData);
    return calculationPart.performSimulation(user, context, moveForward, moveStrafe, attackReduce, jumped, handActive);
  }

  private ComplexColliderSimulationResult simulatePossibleMovement(User user) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();

    boolean inventoryOpen = inventoryData.inventoryOpen();
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    boolean elytraFlying = movementData.elytraFlying;
    int bestForwardKey = 0;
    int bestStrafeKey = 0;
    boolean jumpedOnBestSimulation = false;
    double mostAccurateDistance = Integer.MAX_VALUE;
    boolean reduceOnPlayerAttack = false;

    ProcessorMotionContext context = movementData.processorMotionContext;
    ComplexColliderSimulationResult predictedMovement = null;

    LOOP:
    for (int attackState = 0; attackState <= 1; attackState++) {
      boolean attackReduce = attackState == 1;
      if (attackReduce && (movementData.pastPlayerAttackPhysics >= 1 || AttackDispatcher.REDUCING_DISABLED)) {
        continue;
      }

      for (int jumpState = 0; jumpState <= 1; jumpState++) {
        boolean jumped = jumpState == 1;
        // Jumps are only allowed on the ground :(
        if (jumped && (!lastOnGround && !inLava && !inWater)) {
          continue;
        }
        if (jumped && movementData.denyJump()) {
          continue;
        }

        for (int keyForward = 1; keyForward > -2; keyForward--) {
          for (int keyStrafe = -1; keyStrafe <= 1; keyStrafe++) {
            if (movementData.sprinting && keyForward != 1) {
              continue;
            }
            if (inventoryOpen) {
              if ((keyForward != 0 || keyStrafe != 0) || jumped) {
                continue;
              }
            }
            float moveForward = keyForward * 0.98f;
            float moveStrafe = keyStrafe * 0.98f;
            context.resetTo(movementData);
            ComplexColliderSimulationResult collisionResult = simulator.performSimulation(
              user, context, moveForward, moveStrafe,
              attackReduce, jumped, inventoryData.handActive()
            );
            ProcessorMotionContext collisionContext = collisionResult.context();
            double differenceX = collisionContext.motionX - receivedMotionX;
            double differenceY = collisionContext.motionY - receivedMotionY;
            double differenceZ = collisionContext.motionZ - receivedMotionZ;
            double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);
            if (distance < mostAccurateDistance) {
              predictedMovement = collisionResult;
              mostAccurateDistance = distance;
              bestForwardKey = keyForward;
              bestStrafeKey = keyStrafe;
              jumpedOnBestSimulation = jumped;
              reduceOnPlayerAttack = attackReduce;
            }
            boolean fastMovementProcess = (!inWater && inLava) || elytraFlying;
            if (distance < 5e-4 && fastMovementProcess) {
              break LOOP;
            }
            if (!simulator.requiresKeyCalculation()) {
              break LOOP;
            }
          }
        }
      }
    }

    if (predictedMovement == null) {
      predictedMovement = simulateMovementWithoutKeyPress(user);
    }

    if (movementData.pastPlayerAttackPhysics == 0 && movementData.sprinting && !reduceOnPlayerAttack) {
      movementData.ignoredAttackReduce = true;
    }

    movementData.keyForward = bestForwardKey;
    movementData.keyStrafe = bestStrafeKey;
    movementData.physicsJumped = jumpedOnBestSimulation;
    return predictedMovement;
  }

  private double calculateMovementDistance(User user, ProcessorMotionContext context) {
    UserMetaMovementData movementData = user.meta().movementData();
    return MathHelper.resolveDistance(
      context.motionX, context.motionY, context.motionZ,
      movementData.motionX(), movementData.motionY(), movementData.motionZ()
    );
  }
}