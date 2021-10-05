package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.diagnostic.IterativeStudy;
import de.jpx3.intave.diagnostic.KeyPressStudy;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.shade.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

@Relocate
public final class PredictiveSimulationProcessor implements SimulationProcessor {

  /*
  * this class is rather messy
  * please refactor
  * */
  private final boolean itemUsageReset;

  public PredictiveSimulationProcessor(boolean itemUsageReset) {
    this.itemUsageReset = itemUsageReset;
  }

  @Override
  public Simulation simulate(User user, Simulator simulator) {
    boolean keyDependent = simulator.affectedByMovementKeys();
    return keyDependent ? performKeySimulation(user, simulator) : simulateWithoutKeyPress(user, simulator);
  }

  private Simulation performKeySimulation(User user, Simulator simulator) {
    MovementMetadata movementData = user.meta().movement();
    return movementData.externalKeyApply ? performKeySimulationFromInput(user, simulator) : performKeyComparisonSimulation(user, simulator);
  }

  private Simulation performKeySimulationFromInput(User user, Simulator simulator) {
    MovementMetadata movementData = user.meta().movement();
    int clientInputKey = movementData.clientForwardKey;
    int clientStrafeKey = movementData.clientStrafeKey;
    boolean jump = movementData.clientPressedJump && movementData.lastOnGround;
    movementData.keyForward = clientInputKey;
    movementData.keyStrafe = clientStrafeKey;
    movementData.physicsJumped = jump;
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulateWithKeyPress(user, simulator, clientInputKey, clientStrafeKey, jump);
  }

  private final static double REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT = 0.002;
  private final static double REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT = 0.02;

  private Simulation performKeyComparisonSimulation(User user, Simulator simulator) {
    MovementMetadata movementData = user.meta().movement();
    Simulation simulation;
    double simulationAccuracy;
    boolean biasedSimulationFailed;

    //
    // try prediction biased simulation
    //
    simulation = simulateMovementKeyPredictionBiased(user, simulator);
    simulationAccuracy = simulation.accuracy(movementData.motion());
    biasedSimulationFailed = simulationAccuracy > REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT;

    if (biasedSimulationFailed) {
      //
      // try last-key biased simulation
      //
      simulation = simulateMovementLastKeyBiased(user, simulator);
      simulationAccuracy = simulation.accuracy(movementData.motion());
      biasedSimulationFailed = simulationAccuracy > REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT;
    }

    //
    // perform iterative simulation procedure
    //
    boolean iterativeAllowed = /* misplaced - please solve this otherwise */ !user.meta().inventory().inventoryOpen();
    if (biasedSimulationFailed && iterativeAllowed) {
      SimulationStack simulationStack = simulateMovementIterative(user, simulator);
      simulation = simulationStack.bestSimulation();
      enterIterativeSimulationStack(user, simulationStack);
    }
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulation;
  }

  private void enterIterativeSimulationStack(User user, SimulationStack simulationStack) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();
    if (movementData.pastPlayerAttackPhysics == 0 && movementData.sprinting && !simulationStack.reduced()) {
      movementData.ignoredAttackReduce = true;
    }
    /* misplaced - please solve this otherwise */
    boolean movementSuggestsHandIsActive = simulationStack.handActive();
    boolean packetsSuggestsHandIsActive = inventoryData.handActive();
    if (packetsSuggestsHandIsActive && !movementSuggestsHandIsActive) {
      boolean releaseHandConditions = Hypot.fast(movementData.motionX(), movementData.motionZ()) > 0.3 || movementData.lastTeleport >= 2;
      if (releaseHandConditions && itemUsageReset) {
        user.meta().inventory().releaseItemNextTick();
      }
    }
    movementData.keyForward = simulationStack.forward();
    movementData.keyStrafe = simulationStack.strafe();
    movementData.physicsJumped = simulationStack.jumped();
  }

  @Override
  public Simulation simulateWithKeyPress(
    User user, Simulator simulator, int forward, int strafe, boolean jumped
  ) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    Motion motion = movementData.motionProcessorContext.copy();
    motion.resetTo(movementData);
    MovementConfiguration configuration = MovementConfiguration.select(
      forward, strafe,
      false, movementData.sprintingAllowed(),
      jumped, meta.inventory().handActive()
    );
    return simulator.performSimulation(
      user, motion, configuration
    );
  }

  private final static double REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED = 0.1;

  private Simulation simulateMovementKeyPredictionBiased(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.start();
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    Motion motionVector = movementData.motionProcessorContext;
    double lastMotionX = movementData.physicsMotionX;
    double lastMotionZ = movementData.physicsMotionZ;
    boolean jumped = false;
    boolean sprinting = movementData.sprintingAllowed() || movementData.hasSprintSpeed;
    if (movementData.lastOnGround && !movementData.denyJump()) {
      double motionY = movementData.motionY();
      jumped = Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpMotion();
      if (jumped && sprinting) {
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

    boolean inventoryOpen = inventoryData.inventoryOpen();
    if (!inventoryOpen && directionPredictionError(differenceX, differenceZ, yaw) > REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED) {
      movementData.physicsJumped = false;
      movementData.keyForward = -2;
      movementData.keyStrafe = -2;
      motionVector.resetTo(movementData);
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
      return Simulation.invalid();
    }
    MovementConfiguration configuration = MovementConfiguration.empty();
    // keys
    int directionPrediction = directionFrom(differenceX, differenceZ, yaw);
    configuration = configuration.withKeyPress(forwardKeyFrom(directionPrediction), strafeKeyFrom(directionPrediction));
    // jump
    if (jumped) {
      configuration = configuration.withJump();
    }
    // active hand
    if (inventoryData.handActive()) {
      configuration = configuration.withActiveHand();
    }
    // reducing
    if (!AttackDispatcher.REDUCING_DISABLED && movementData.sprintingAllowed() && movementData.pastPlayerAttackPhysics == 0) {
      configuration = configuration.withReducing();
    }
    // block omisprint
    if (sprinting && configuration.forward() != 1) {
      configuration = configuration.withoutKeypress();
    } else if (sprinting) {
      configuration = configuration.withSprinting();
    }
    // block inventory move
    if (inventoryOpen) {
      configuration = configuration.withoutSprinting();
      configuration = configuration.withoutKeypress();
    }
    movementData.physicsJumped = jumped;
    motionVector.resetTo(movementData);
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulation = simulator.performSimulation(user, motionVector, configuration);
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulation;
  }

  private int directionFrom(double differenceX, double differenceZ, float yaw) {
    if (Hypot.fast(differenceX, differenceZ) > 0.001) {
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
    if (Hypot.fast(differenceX, differenceZ) > 0.001) {
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

  private final static int[] forwardKeys = {1, 1, 0, -1, -1, -1, 0, 1, 1};
  private final static int[] strafeKeys = {0, -1, -1, -1, 0, 1, 1, 1, 0};

  private static int forwardKeyFrom(int direction) {
    return direction == -1 ? 0 : forwardKeys[direction];
  }

  private static int strafeKeyFrom(int direction) {
    return direction == -1 ? 0 : strafeKeys[direction];
  }

  private Simulation simulateMovementLastKeyBiased(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_LK_BIA.start();
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    Motion motion = movementData.motionProcessorContext;

    int keyForward = movementData.lastKeyForward;
    int keyStrafe = movementData.lastKeyStrafe;
    boolean inventoryOpen = inventoryData.inventoryOpen();

    // return if prediction bias already has calculated this keys
    if (!inventoryOpen && keyForward == movementData.keyForward && keyStrafe == movementData.keyStrafe) {
      Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      return Simulation.invalid();
    }
    MovementConfiguration configuration = MovementConfiguration.empty();
    // keys
    configuration = configuration.withKeyPress(keyForward, keyStrafe);
    // reducing
    if (!AttackDispatcher.REDUCING_DISABLED && movementData.sprintingAllowed() && user.meta().movement().pastPlayerAttackPhysics == 0) {
      configuration = configuration.withReducing();
    }
    boolean sprinting = movementData.sprintingAllowed();
    // jump
    if (movementData.lastOnGround && !movementData.denyJump()) {
      double motionY = movementData.motionY();
      if (Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpMotion()) {
        configuration = configuration.withJump();
      }
    }
    if (movementData.inWater && !movementData.denyJump()) {
      if (movementData.motionY() > 0.0) {
        configuration = configuration.withJump();
      }
    }
    // hand active
    if (inventoryData.handActive()) {
      configuration = configuration.withActiveHand();
    }
    // block invalid sprint
    if (sprinting && keyForward != 1) {
      configuration = configuration.withoutKeypress();
    } else if (sprinting) {
      configuration = configuration.withSprinting();
    }
    // block inventory move
    if (inventoryData.inventoryOpen()) {
      configuration = configuration.withoutKeypress();
    }
    movementData.physicsJumped = configuration.isJumping();
    motion.resetTo(movementData);
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulationResult = simulator.performSimulation(user, motion, configuration);
    Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulationResult;
  }

  private final static boolean[] ALWAYS = new boolean[]{true};
  private final static boolean[] OPTIMISTIC = new boolean[]{true, false};
  private final static boolean[] PESSIMISTIC = new boolean[]{false, true};
  private final static boolean[] NEVER = new boolean[]{false};

  private final static int[][] KEYS_USAGE_ORDERED = {{1, 0}, {0, 0}, {1, -1}, {1, 1}, {0, -1}, {0, 1}, {-1, -1}, {-1, 0}, {-1, 1}};

  private SimulationStack simulateMovementIterative(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_ITR.start();
    MetadataBundle meta = user.meta();
    InventoryMetadata inventoryData = meta.inventory();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    SimulationStack simulationStack = SimulationStack.of(user);
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater;
    boolean lastOnGround = movementData.lastOnGround;
    boolean estimatedJump = Math.abs(movementData.motionY() - (1 - user.sizeOf(movementData.pose()).height() % 1)) < 1e-5 || movementData.motionY() == movementData.jumpMotion();
    boolean skipUseItem = !clientData.sprintWhenHandActive() && movementData.sprinting;

    int iterativeRuns = 0;
    int nearestForwardKey = -2, nearestStrafeKey = -2;
    double nearestKeyDistance = Double.MAX_VALUE;

    SIMULATION:
    for (boolean sprinting : movementData.sprintingAllowed() || movementData.hasSprintSpeed ? /* surprisingly pessimistic */ PESSIMISTIC : NEVER) {
      movementData.refreshFriction(sprinting);
      for (boolean useItemState : inventoryData.handActive() ? OPTIMISTIC : PESSIMISTIC) {
        if (skipUseItem && useItemState) {
          continue;
        }
        IterativeStudy.USE_ITEM_ITERATOR.run();
        for (boolean attackReduce : PESSIMISTIC) {
          if (attackReduce && (movementData.pastPlayerAttackPhysics >= 1 || AttackDispatcher.REDUCING_DISABLED)) {
            continue;
          }
          IterativeStudy.ATTACK_REDUCE_ITERATOR.run();
          for (boolean jumped : estimatedJump ? OPTIMISTIC : PESSIMISTIC) {
            // Jumps are only allowed on the ground :(
            if (jumped && !lastOnGround && !inLava && !inWater) {
              continue;
            }
            if (jumped && movementData.denyJump()) {
              continue;
            }
            IterativeStudy.JUMP_ITERATOR.run();
            boolean hasKeyEstimate = nearestKeyDistance < 1;
            for (int i = (hasKeyEstimate ? -1 : 0); i < 9; i++) {
              int keyForward;
              int keyStrafe;
              if (i >= 0) {
                int[] keyPair = KEYS_USAGE_ORDERED[i];
                keyForward = keyPair[0];
                keyStrafe = keyPair[1];
                if (hasKeyEstimate && keyForward == nearestForwardKey && keyStrafe == nearestStrafeKey) {
                  continue;
                }
              } else {
                keyForward = nearestForwardKey;
                keyStrafe = nearestStrafeKey;
              }
              if (sprinting && keyForward != 1) {
                continue;
              }
              iterativeRuns++;
              MovementConfiguration movementConfiguration = MovementConfiguration.select(
                keyForward, keyStrafe, attackReduce, sprinting, jumped, useItemState
              );
              Simulation simulation = simulateAndAppend(
                user, simulator,
                simulationStack,
                movementConfiguration,
                false
              );
              double distance = simulation.accuracy(movementData.motion());
              if (distance < nearestKeyDistance) {
                nearestKeyDistance = distance;
                nearestForwardKey = keyForward;
                nearestStrafeKey = keyStrafe;
              }
              if (simulationStack.smallestDistance() <= (movementData.recentlyEncounteredFlyingPacket(2) ? REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT : REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT)) {
                break SIMULATION;
              }
            }
          }
        }
      }
    }
    if (simulationStack.noMatch()) {
      simulateAndAppend(
        user, simulator,
        simulationStack,
        MovementConfiguration.empty(),
        true
      );
    }
    IterativeStudy.USE_ITEM_ITERATOR.pass();
    IterativeStudy.ATTACK_REDUCE_ITERATOR.pass();
    IterativeStudy.JUMP_ITERATOR.pass();
    IterativeStudy.enterTrials(iterativeRuns);
    Timings.CHECK_PHYSICS_PROC_ITR.stop();
    return simulationStack;
  }

  private Simulation simulateAndAppend(
    User user,
    Simulator simulator,
    SimulationStack result,
    MovementConfiguration movementConfiguration,
    boolean forceApply
  ) {
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    Motion motion = movementData.motionProcessorContext;
    motion.resetTo(movementData);
    Simulation simulation = simulator.performSimulation(
      user, motion, movementConfiguration
    );
    double distance = simulation.accuracy(movementData.motion());
    if (forceApply || inventoryData.handActive() == movementConfiguration.isHandActive() || distance < 0.001) {
      simulation = simulation.reusableCopy();
      result.tryAppendToState(simulation, distance);
    }
    return simulation;
  }
}