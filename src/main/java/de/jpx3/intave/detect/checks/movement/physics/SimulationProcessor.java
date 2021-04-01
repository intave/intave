package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.detect.checks.movement.physics.simulators.PoseSimulator;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.event.dispatch.AttackDispatcher;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.collider.result.ComplexColliderSimulationResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.reflect.ReflectiveDataWatcherAccess.DATA_WATCHER_BLOCKING_ID;

@Relocate
public final class SimulationProcessor {
  private final static double VERIFY_DISTANCE = 0.001;

  public ComplexColliderSimulationResult simulate(User user, Pose pose) {
    PoseSimulator simulator = pose.simulator();
    boolean keyCalculation = simulator.requiresKeyCalculation();
    return keyCalculation ? performKeySimulation(user) : simulateMovementWithoutKeyPress(user);
  }

  private ComplexColliderSimulationResult performKeySimulation(User user) {
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    boolean forceBiased = inventoryData.inventoryOpen();
    ComplexColliderSimulationResult predictedMovement;
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    //
    // Perform biased simulation
    //
    predictedMovement = simulateMovementBiased(user);
    double movementDistance = calculateMovementDistance(user, predictedMovement.context());
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    //
    // Perform iterative simulation if biased fails
    //
    if (!forceBiased && movementDistance > VERIFY_DISTANCE) {
      Timings.CHECK_PHYSICS_PROC_ITR.start();
      IterativeSimulationResult iterativeSimulationResult = simulatePossibleMovement(user);
      predictedMovement = iterativeSimulationResult.collisionResult();
      applyIterativeSimulationTo(user, iterativeSimulationResult);
      Timings.CHECK_PHYSICS_PROC_ITR.stop();
    }
    return predictedMovement;
  }

  private void applyIterativeSimulationTo(User user, IterativeSimulationResult iterativeResult) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    if (movementData.pastPlayerAttackPhysics == 0 && movementData.sprinting && !iterativeResult.reduced()) {
      movementData.ignoredAttackReduce = true;
    }
    if (inventoryData.handActive() && !iterativeResult.handActive()) {
      releaseHandOf(user);
    }
    movementData.keyForward = iterativeResult.forward();
    movementData.keyStrafe = iterativeResult.strafe();
    movementData.physicsJumped = iterativeResult.jumped();
  }

  private void releaseHandOf(User user) {
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaMovementData movementData = meta.movementData();
    inventoryData.setHandActive(false);
    ItemStack itemStack = inventoryData.heldItem();
    if (itemStack != null && !InventoryUseItemHelper.isSwordItem(user.player(), itemStack)) {
      int threshold = itemStack.getType() == Material.BOW ? 5 : 1;
      if (movementData.physicsEatingSlotSwitchVL++ > threshold) {
        inventoryData.applySlotSwitch();
      } else {
        inventoryData.setHandActive(true);
      }
    }
    Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(user.player(), DATA_WATCHER_BLOCKING_ID, false));
  }

  public ComplexColliderSimulationResult simulateMovementWithoutKeyPress(User user) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    Pose movementPoseType = movementData.movementPoseType();
    MotionVector motionVector = MotionVector.from(movementData.motionVector);
    motionVector.resetTo(movementData);
    return movementPoseType.simulator().performSimulation(
      user, motionVector,
      0, 0, false, false,
      meta.inventoryData().handActive()
    );
  }

  private ComplexColliderSimulationResult simulateMovementBiased(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();
    MotionVector motionVector = movementData.motionVector;
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
    motionVector.resetTo(movementData);
    return simulator.performSimulation(user, motionVector, moveForward, moveStrafe, attackReduce, jumped, handActive);
  }

  private final static boolean[] BOOLEAN_STATES_TF = new boolean[]{true, false};
  private final static boolean[] BOOLEAN_STATES_FT = new boolean[]{false, true};

  private IterativeSimulationResult simulatePossibleMovement(User user) {
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaMovementData movementData = meta.movementData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();
    IterativeSimulationResult iterativeSimulation = new IterativeSimulationResult();
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    SIMULATION:
    for (boolean useItemState : inventoryData.handActive() ? BOOLEAN_STATES_TF : BOOLEAN_STATES_FT) {
      for (boolean attackReduce : BOOLEAN_STATES_FT) {
        if (attackReduce && (movementData.pastPlayerAttackPhysics >= 1 || AttackDispatcher.REDUCING_DISABLED)) {
          continue;
        }
        for (boolean jumped : BOOLEAN_STATES_FT) {
          // Jumps are only allowed on the ground :(
          if (jumped && !lastOnGround && !inLava && !inWater) {
            continue;
          }
          if (jumped && movementData.denyJump()) {
            continue;
          }
          for (int keyForward = 1; keyForward >= -1; keyForward--) {
            for (int keyStrafe = -1; keyStrafe <= 1; keyStrafe++) {
              if (movementData.sprinting && keyForward != 1) {
                continue;
              }
              simulateIterativeState(
                user,
                iterativeSimulation,
                simulator,
                keyForward,
                keyStrafe,
                attackReduce,
                jumped,
                useItemState
              );
              if (iterativeSimulation.smallestDistance() <= VERIFY_DISTANCE) {
                break SIMULATION;
              }
            }
          }
        }
      }
    }
    if (iterativeSimulation.noMatch()) {
      simulateIterativeState(
        user,
        iterativeSimulation,
        simulator,
        0,
        0,
        false,
        false,
        false
      );
    }
    return iterativeSimulation;
  }

  private double calculateMovementDistance(User user, MotionVector context) {
    UserMetaMovementData movementData = user.meta().movementData();
    return MathHelper.resolveDistance(
      context.motionX, context.motionY, context.motionZ,
      movementData.motionX(), movementData.motionY(), movementData.motionZ()
    );
  }

  private void simulateIterativeState(
    User user,
    IterativeSimulationResult result,
    PoseSimulator simulator,
    int keyForward,
    int keyStrafe,
    boolean attackReduce,
    boolean jumped,
    boolean handActive
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    MotionVector motionVector = movementData.motionVector;
    float moveForward = keyForward * 0.98f;
    float moveStrafe = keyStrafe * 0.98f;
    motionVector.resetTo(movementData);
    ComplexColliderSimulationResult collisionResult = simulator.performSimulation(
      user, motionVector, moveForward, moveStrafe,
      attackReduce, jumped, handActive
    );
    MotionVector collisionContext = collisionResult.context();
    double distance = calculateMovementDistance(user, collisionContext);
    result.tryAppendToState(collisionResult, distance, keyForward, keyStrafe, attackReduce, jumped, handActive);
  }

  private static final class IterativeSimulationResult {
    private final static int DEFAULT_DISTANCE = Integer.MAX_VALUE;

    private ComplexColliderSimulationResult collisionResult;
    private int forward, strafe;
    private boolean jumped;
    private boolean reduced;
    private double smallestDistance;
    private boolean handActive;

    public IterativeSimulationResult() {
      this.smallestDistance = DEFAULT_DISTANCE;
    }

    public void tryAppendToState(
      ComplexColliderSimulationResult collisionResult,
      double newDistance,
      int forward,
      int strafe,
      boolean attackReduce,
      boolean jumped,
      boolean handActive
    ) {
      if (newDistance < this.smallestDistance) {
        appendToState(collisionResult, newDistance, forward, strafe, attackReduce, jumped, handActive);
      }
    }

    private void appendToState(
      ComplexColliderSimulationResult collisionResult,
      double newDistance,
      int forward,
      int strafe,
      boolean attackReduce,
      boolean jumped,
      boolean handActive
    ) {
      this.collisionResult = collisionResult;
      this.smallestDistance = newDistance;
      this.forward = forward;
      this.strafe = strafe;
      this.reduced = attackReduce;
      this.jumped = jumped;
      this.handActive = handActive;
    }

    public boolean noMatch() {
      return this.smallestDistance == DEFAULT_DISTANCE;
    }

    public ComplexColliderSimulationResult collisionResult() {
      return collisionResult;
    }

    public int forward() {
      return forward;
    }

    public int strafe() {
      return strafe;
    }

    public boolean jumped() {
      return jumped;
    }

    public boolean reduced() {
      return reduced;
    }

    public double smallestDistance() {
      return smallestDistance;
    }

    public boolean handActive() {
      return handActive;
    }
  }
}