package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.diagnostics.KeyPressStudy;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.event.dispatch.AttackDispatcher;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.collider.complex.ComplexColliderSimulationResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.reflect.ReflectiveDataWatcherAccess.DATA_WATCHER_BLOCKING_ID;

@Relocate
public final class SimulationProcessor {
  private final static double REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT = 0.001;
  private final static double REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED = 0.1;
  private final static ComplexColliderSimulationResult INVALID_SIMULATION = new ComplexColliderSimulationResult(
    new MotionVector(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), false, false, false, false, false, false
  );

  public ComplexColliderSimulationResult simulate(User user, Pose pose) {
    PoseSimulator simulator = pose.simulator();
    boolean keyDependent = simulator.affectedByMovementKeys();
    return keyDependent ? performKeySimulation(user) : simulateMovementWithoutKeyPress(user);
  }

  private ComplexColliderSimulationResult performKeySimulation(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    return movementData.applyClientKeys ? performKeySimulationFromInput(user) : performKeyComparisonSimulation(user);
  }

  private ComplexColliderSimulationResult performKeySimulationFromInput(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    int clientInputKey = movementData.clientInputKey;
    int clientStrafeKey = movementData.clientStrafeKey;
    boolean jump = movementData.clientPressedJump && movementData.lastOnGround;
    movementData.keyForward = clientInputKey;
    movementData.keyStrafe = clientStrafeKey;
    movementData.physicsJumped = jump;
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulateMovementWithKeyPress(user, clientInputKey, clientStrafeKey, jump);
  }

  private ComplexColliderSimulationResult performKeyComparisonSimulation(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    ComplexColliderSimulationResult simulation;
    double simulationAccuracy;
    boolean biasedSimulationFailed;

    //
    // try prediction biased simulation
    //
    simulation = simulateMovementKeyPredictionBiased(user);
    simulationAccuracy = simulation.accuracy(movementData.motion());
    biasedSimulationFailed = simulationAccuracy > REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT;

    if (biasedSimulationFailed) {
      //
      // try last-key biased simulation
      //
      simulation = simulateMovementLastKeyBiased(user);
      simulationAccuracy = simulation.accuracy(movementData.motion());
      biasedSimulationFailed = simulationAccuracy > REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT;
    }

    //
    // perform iterative simulation procedure
    //
    boolean iterativeAllowed = !user.meta().inventoryData().inventoryOpen();
    if (biasedSimulationFailed && iterativeAllowed) {
      IterativeSimulationContext iterativeSimulationContext = simulateMovementIterative(user);
      simulation = iterativeSimulationContext.bestSimulation();
      applyIterativeSimulationTo(user, iterativeSimulationContext);
    }
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulation;
  }

  private void applyIterativeSimulationTo(User user, IterativeSimulationContext iterativeResult) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    if (movementData.pastPlayerAttackPhysics == 0 && movementData.sprinting && !iterativeResult.reduced()) {
      movementData.ignoredAttackReduce = true;
    }
    if (inventoryData.handActive() && !iterativeResult.handActive()) {
      boolean releaseHandConditions = Math.hypot(movementData.motionX(), movementData.motionZ()) > 0.2 || movementData.lastTeleport >= 2;
      if (releaseHandConditions) {
        releaseHandOf(user);
      }
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
      boolean hasShield = user.meta().clientData().combatUpdate();
      int threshold = itemStack.getType() == Material.BOW || hasShield ? 5 : 3;
      if (movementData.physicsEatingSlotSwitchVL++ > threshold) {
        inventoryData.applySlotSwitch();
      } else {
        inventoryData.setHandActive(true);
      }
    }
    if (!ViaVersionAdapter.ignoreBlocking(user.player())) {
      Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(user.player(), DATA_WATCHER_BLOCKING_ID, false));
    }
  }

  public ComplexColliderSimulationResult simulateMovementWithoutKeyPress(
    User user
  ) {
    return simulateMovementWithKeyPress(user, 0, 0, false);
  }

  public ComplexColliderSimulationResult simulateMovementWithKeyPress(
    User user, int forward, int strafe, boolean jumped
  ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    Pose movementPoseType = movementData.movementPoseType();
    MotionVector motionVector = MotionVector.from(movementData.motionProcessorContext);
    motionVector.resetTo(movementData);
    return movementPoseType.simulator().performSimulation(
      user, motionVector,
      forward, strafe, false, jumped,
      meta.inventoryData().handActive()
    );
  }

  private ComplexColliderSimulationResult simulateMovementKeyPredictionBiased(User user) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.start();
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();
    MotionVector motionVector = movementData.motionProcessorContext;
    double lastMotionX = movementData.physicsMotionX;
    double lastMotionZ = movementData.physicsMotionZ;
    boolean jumped = false;
    if (movementData.lastOnGround && !movementData.denyJump()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpMotion();
      if (jumped && movementData.sprinting) {
        lastMotionX -= movementData.yawSine() * 0.2f;
        lastMotionZ += movementData.yawCosine() * 0.2f;
      }
    }
    if (movementData.inWater && !movementData.denyJump()) {
      jumped = movementData.motionY() > 0.0;
    }
    double differenceX = movementData.motionX() - lastMotionX;
    double differenceZ = movementData.motionZ() - lastMotionZ;
    float yaw = movementData.rotationYaw;
    int direction = directionFrom(differenceX, differenceZ, yaw);

    boolean inventoryOpen = inventoryData.inventoryOpen();
    if (!inventoryOpen && directionPredictionError(differenceX, differenceZ, yaw) > REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED) {
      movementData.physicsJumped = false;
      movementData.keyForward = -2;
      movementData.keyStrafe = -2;
      motionVector.resetTo(movementData);
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
      return INVALID_SIMULATION;
    }
    int keyForward = forwardKeyFrom(direction);
    int keyStrafe = strafeKeyFrom(direction);
    boolean handActive = inventoryData.handActive();
    boolean attackReduce = !AttackDispatcher.REDUCING_DISABLED && movementData.sprintingAllowed() && user.meta().movementData().pastPlayerAttackPhysics == 0;
    if (movementData.sprinting && keyForward != 1) {
      keyForward = 0;
      keyStrafe = 0;
    }
    if (inventoryOpen) {
      keyForward = 0;
      keyStrafe = 0;
    }
    float moveForward = keyForward * 0.98f;
    float moveStrafe = keyStrafe * 0.98f;
    movementData.physicsJumped = jumped;
    motionVector.resetTo(movementData);
    movementData.keyForward = keyForward;
    movementData.keyStrafe = keyStrafe;
    ComplexColliderSimulationResult simulationResult = simulator.performSimulation(user, motionVector, moveForward, moveStrafe, attackReduce, jumped, handActive);

    Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulationResult;
  }

  private ComplexColliderSimulationResult simulateMovementLastKeyBiased(User user) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_LK_BIA.start();
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();
    MotionVector motionVector = movementData.motionProcessorContext;

    int keyForward = movementData.lastKeyForward;
    int keyStrafe = movementData.lastKeyStrafe;
    boolean inventoryOpen = inventoryData.inventoryOpen();

    // return if prediction bias already has calculated this keys
    if (!inventoryOpen && keyForward == movementData.keyForward && keyStrafe == movementData.keyStrafe) {
      Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      return INVALID_SIMULATION;
    }

    boolean jumped = false;
    if (movementData.lastOnGround && !movementData.denyJump()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpMotion();
    }
    if (movementData.inWater && !movementData.denyJump()) {
      jumped = movementData.motionY() > 0.0;
    }

    boolean handActive = inventoryData.handActive();
    boolean attackReduce = !AttackDispatcher.REDUCING_DISABLED && movementData.sprintingAllowed() && user.meta().movementData().pastPlayerAttackPhysics == 0;
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
    movementData.keyForward = keyForward;
    movementData.keyStrafe = keyStrafe;
    ComplexColliderSimulationResult simulationResult = simulator.performSimulation(user, motionVector, moveForward, moveStrafe, attackReduce, jumped, handActive);
    Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulationResult;
  }

  private int directionFrom(double differenceX, double differenceZ, float yaw) {
    if (Math.hypot(differenceX, differenceZ) > 0.001) {
      double direction;
      direction = Math.toDegrees(Math.atan2(differenceZ, differenceX)) - 90d;
      direction -= yaw;
      direction %= 360d;
      if (direction < 0)
        direction += 360;
      direction = Math.abs(direction);
      direction /= 45d;
      return (int) Math.round(direction);
    }
    return -1;
  }

  private double directionPredictionError(double differenceX, double differenceZ, float yaw) {
    if (Math.hypot(differenceX, differenceZ) > 0.001) {
      double direction;
      direction = Math.toDegrees(Math.atan2(differenceZ, differenceX)) - 90d;
      direction -= yaw;
      direction %= 360d;
      if (direction < 0)
        direction += 360;
      direction = Math.abs(direction);
      direction /= 45d;
      return Math.abs(direction - (int) Math.round(direction));
    }
    return 0;
  }

  private final int[] forwardKeys = {1, 1, 0, -1, -1, -1, 0, 1, 1};
  private final int[] strafeKeys  = {0, -1, -1, -1, 0, 1, 1, 1, 0};

  private int forwardKeyFrom(int direction) {
    return direction == -1 ? 0 : forwardKeys[direction];
  }

  private int strafeKeyFrom(int direction) {
    return direction == -1 ? 0 : strafeKeys[direction];
  }

  private final static boolean[] BOOLEAN_STATES_TF = new boolean[]{true, false};
  private final static boolean[] BOOLEAN_STATES_FT = new boolean[]{false, true};

  private final static int[][] KEYS_USAGE_ORDERED = {{1, 0}, {1, -1}, {1, 1}, {0, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 0}, {-1, 1}};

  private IterativeSimulationContext simulateMovementIterative(User user) {
    Timings.CHECK_PHYSICS_PROC_ITR.start();
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    Pose movementPoseType = movementData.movementPoseType();
    PoseSimulator simulator = movementPoseType.simulator();
    IterativeSimulationContext iterativeSimulation = movementData.iterativeSimulation();
    iterativeSimulation.restore();
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    boolean estimatedJump = Math.abs(movementData.motionY() - 0.2) < 1e-5 || movementData.motionY() == movementData.jumpMotion();
    boolean skipUseItem = !clientData.sprintWhenHandActive() && movementData.sprinting;

    SIMULATION:
    for (boolean useItemState : inventoryData.handActive() ? BOOLEAN_STATES_TF : BOOLEAN_STATES_FT) {
      if (skipUseItem && useItemState) {
        continue;
      }
      for (boolean attackReduce : BOOLEAN_STATES_FT) {
        if (attackReduce && (movementData.pastPlayerAttackPhysics >= 1 || AttackDispatcher.REDUCING_DISABLED)) {
          continue;
        }

        for (boolean jumped : estimatedJump ? BOOLEAN_STATES_TF : BOOLEAN_STATES_FT) {
          // Jumps are only allowed on the ground :(
          if (jumped && !lastOnGround && !inLava && !inWater) {
            continue;
          }
          if (jumped && movementData.denyJump()) {
            continue;
          }
          for (int i = 0; i < 9; i++) {
            int[] keyPair = KEYS_USAGE_ORDERED[i];
            int keyForward = keyPair[0];
            int keyStrafe = keyPair[1];
            if (movementData.sprinting && keyForward != 1) {
              continue;
            }
            simulateIterativeState(
              user,
              movementData,
              inventoryData,
              iterativeSimulation,
              simulator,
              keyForward,
              keyStrafe,
              attackReduce,
              jumped,
              useItemState,
              false
            );
            if (iterativeSimulation.smallestDistance() <= REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT) {
              break SIMULATION;
            }
          }
        }
      }
    }
    if (iterativeSimulation.noMatch() || iterativeSimulation.collisionResult == null) {
      simulateIterativeState(
        user,
        movementData,
        inventoryData,
        iterativeSimulation,
        simulator,
        0,
        0,
        false,
        false,
        false,
        true
      );
    }
    Timings.CHECK_PHYSICS_PROC_ITR.stop();
    return iterativeSimulation;
  }

  private static String resolveKeysFromInput(int forward, int strafe) {
    String key = "";
    if (forward == 1) {
      key += "W";
    } else if (forward == -1) {
      key += "S";
    }
    if (strafe == 1) {
      key += "A";
    } else if (strafe == -1) {
      key += "D";
    }
    return key;
  }

  private double compareReceivedMotionWithMotion(User user, MotionVector context) {
    UserMetaMovementData movementData = user.meta().movementData();
    return MathHelper.distanceOf(
      context.motionX, context.motionY, context.motionZ,
      movementData.motionX(), movementData.motionY(), movementData.motionZ()
    );
  }

  private void simulateIterativeState(
    User user,
    UserMetaMovementData movementData,
    UserMetaInventoryData inventoryData,
    IterativeSimulationContext result,
    PoseSimulator simulator,
    int keyForward,
    int keyStrafe,
    boolean attackReduce,
    boolean jumped,
    boolean handActive,
    boolean forceApply
  ) {
    MotionVector motionVector = movementData.motionProcessorContext;
    float moveForward = keyForward * 0.98f;
    float moveStrafe = keyStrafe * 0.98f;
    motionVector.resetTo(movementData);
    ComplexColliderSimulationResult collisionResult = simulator.performSimulation(
      user, motionVector, moveForward, moveStrafe,
      attackReduce, jumped, handActive
    );
    MotionVector predictedMotion = collisionResult.context();
    double distance = compareReceivedMotionWithMotion(user, predictedMotion);
    if (forceApply || inventoryData.handActive() == handActive || distance < 0.001) {
      result.tryAppendToState(collisionResult, distance, keyForward, keyStrafe, attackReduce, jumped, handActive);
    }
  }

  public static final class IterativeSimulationContext {
    private final static int DEFAULT_DISTANCE = Integer.MAX_VALUE;

    private ComplexColliderSimulationResult collisionResult;
    private int forward, strafe;
    private boolean jumped;
    private boolean reduced;
    private double smallestDistance;
    private boolean handActive;

    public IterativeSimulationContext() {
      this.smallestDistance = DEFAULT_DISTANCE;
    }

    public void restore() {
      collisionResult = null;
      forward = 0;
      strafe = 0;
      jumped = false;
      reduced = false;
      smallestDistance = DEFAULT_DISTANCE;
      handActive = false;
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

    public ComplexColliderSimulationResult bestSimulation() {
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