package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.diagnostic.IterativeStudy;
import de.jpx3.intave.diagnostic.KeyPressStudy;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.dispatch.AttackDispatcher;
import de.jpx3.intave.module.feedback.Superposition;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.List;

@Relocate
public final class PredictiveSimulationProcessor implements SimulationProcessor {

  /*
   * this class is rather messy
   * please refactor
   * */
  private final boolean itemUsageReset;
  private final boolean useSuperpositions;
  private final boolean detectNoSlowdown;

  public PredictiveSimulationProcessor(boolean itemUsageReset, boolean useSuperpositions, boolean detectNoSlowdown) {
    this.itemUsageReset = itemUsageReset;
    this.useSuperpositions = useSuperpositions;
    this.detectNoSlowdown = detectNoSlowdown;
  }

  @Override
  public Simulation simulate(User user, Simulator simulator) {
    MovementMetadata movementData = user.meta().movement();
    boolean searchKeys = simulator.affectedByMovementKeys();

    if (movementData.externalKeyApply) {
      // vehicles sent us the keys
      return simulateWithKeyPress(user, simulator, movementData.clientForwardKey, movementData.clientStrafeKey, movementData.clientPressedJump);
    } else if (searchKeys) {
      // we must search and guess the keys
      return performKeySearchSimulation(user, simulator);
    } else {
      // keys don't matter
      return simulateWithKeyPress(user, simulator, 0, 0, false);
    }
  }

  @Override
  public Simulation simulateWithKeyPress(
    User user, Simulator simulator, int forward, int strafe, boolean jumped
  ) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    jumped &= movementData.lastOnGround;
    movementData.keyForward = forward;
    movementData.keyStrafe = strafe;
    movementData.physicsJumped = jumped;
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);

//    List<Superposition<?>> superpositions = movementData.superpositions();
//    for (Superposition<?> superposition : superpositions) {
//      superposition.applyVariation(0);
//    }

    Motion motion = movementData.motionProcessorContext.copy();
    motion.setToBaseMotionFrom(movementData);
    MovementConfiguration configuration = MovementConfiguration.select(
      forward, strafe,
      false, movementData.sprintingAllowed(),
      jumped, meta.inventory().handActive()
    );
    Simulation simulate = simulator.simulate(user, motion, movementData, configuration);

    // what to do here?
//    for (Superposition<?> superposition : superpositions) {
//      superposition.resetVariation(0);
//      superposition.collapseVariation(0);
//    }
    return simulate;
  }

  private static final double REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT = 0.002;
  private static final double REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT = 0.02;

  private Simulation performKeySearchSimulation(User user, Simulator simulator) {
    MovementMetadata movementData = user.meta().movement();

    List<Superposition<?>> superpositions = movementData.superpositions();
    for (Superposition<?> superposition : superpositions) {
      superposition.applyVariation(0);// assume first variation is correct
    }

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
    for (Superposition<?> superposition : superpositions) {
      superposition.resetVariation(0);
    }
    boolean iterativeAllowed = /* misplaced - please solve this otherwise */ !user.meta().inventory().inventoryOpen();
    if (biasedSimulationFailed && iterativeAllowed) {
      SimulationStack simulationStack = simulateMovementIterative(user, simulator);
      simulation = simulationStack.bestSimulation();
      enterIterativeSimulationStack(user, simulationStack);
//      if (simulationStack.trials() >= 8) {
        simulation.append("i" + simulationStack.trials());
//      }
    } else {
      for (Superposition<?> superposition : superpositions) {
        superposition.collapseVariation(0);
      }
    }
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulation;
  }

  private void enterIterativeSimulationStack(User user, SimulationStack simulationStack) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();
    if (movementData.pastPlayerAttackPhysics == 0 && simulationStack.sprinted()/*movementData.sprinting*/ && !simulationStack.reduced()) {
      movementData.ignoredAttackReduce = true;
    }
    /* misplaced - please solve this otherwise */
    boolean movementSuggestsHandIsActive = simulationStack.handActive();
    boolean packetsSuggestsHandIsActive = inventoryData.handActive();
    if (packetsSuggestsHandIsActive && !movementSuggestsHandIsActive) {
      boolean releaseHandConditions = Hypot.fast(movementData.motionX(), movementData.motionZ()) > 0.3 || movementData.lastTeleport >= 2;
      boolean itemIsBow = ItemProperties.isBow(meta.inventory().activeItemType()) || ItemProperties.isBow(meta.inventory().offhandItemType());
      if (releaseHandConditions && !itemIsBow && itemUsageReset) {
        meta.inventory().releaseItemNextTick();
      }
    }
    movementData.keyForward = simulationStack.forward();
    movementData.keyStrafe = simulationStack.strafe();
    movementData.physicsJumped = simulationStack.jumped();
//    movementData.sprintMove = simulationStack.sprinted();
  }

  private static final double REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED = 0.1;

  private Simulation simulateMovementKeyPredictionBiased(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.start();
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    ProtocolMetadata protocol = user.meta().protocol();
    Motion motion = movementData.motionProcessorContext;
    double lastMotionX = movementData.baseMotionX;
    double lastMotionZ = movementData.baseMotionZ;
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
    double directionPrediction = directionFrom(differenceX, differenceZ, yaw);
    int direction = (int) Math.round(directionPrediction);

    if (!inventoryOpen && (directionPrediction < 0 || Math.abs(directionPrediction - direction) > REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED)) {
      movementData.physicsJumped = false;
      movementData.keyForward = -2;
      movementData.keyStrafe = -2;
      motion.setToBaseMotionFrom(movementData);
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
      return Simulation.invalid();
    }
    MovementConfiguration configuration = MovementConfiguration.empty();
    // keys
    configuration = configuration.withKeyPress(forwardKeyFrom(direction), strafeKeyFrom(direction));
    // jump
    if (jumped) {
      configuration = configuration.withJump();
    }
    // active hand
    if (inventoryData.handActive() && (ItemProperties.canItemBeUsed(user.player(), inventoryData.heldItem()) || ItemProperties.canItemBeUsed(user.player(), inventoryData.offhandItem()))) {
      configuration = configuration.withActiveHand();
    }
    // reducing
    if (!AttackDispatcher.REDUCING_DISABLED /*&& movementData.sprintingAllowed()*/ && movementData.pastPlayerAttackPhysics == 0) {
      configuration = configuration.withReducing();
    }
    // block omnisprint
    if (sprinting && configuration.forward() != 1) {
      configuration = configuration.withoutKeypress();
    } else if (sprinting) {
      if (movementData.isSneaking() && !configuration.isJumping()) {
        configuration = configuration.withoutSprinting();
      } else {
        configuration = configuration.withSprinting();
      }
    }
    // block inventory move
    if (inventoryOpen) {
      configuration = configuration.withoutSprinting();
      configuration = configuration.withoutKeypress();
    }
    movementData.physicsJumped = jumped;
//    movementData.sprintMove = sprinting;
    motion.setTo(movementData.baseMotion());
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulation = simulator.simulate(user, motion, movementData, configuration);
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulation;
  }

  private double directionFrom(double differenceX, double differenceZ, float yaw) {
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

  private static final int[] forwardKeys = {1, 1, 0, -1, -1, -1, 0, 1, 1};
  private static final int[] strafeKeys = {0, -1, -1, -1, 0, 1, 1, 1, 0};

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
//    boolean sprintRequirement = user.protocolVersion() > VER_1_8 ? movementData.sprintingAllowed() : movementData.isSprinting();
    if (!AttackDispatcher.REDUCING_DISABLED/* && movementData.sprintingAllowed()*/ && user.meta().movement().pastPlayerAttackPhysics == 0) {
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
    if (inventoryData.handActive() && (ItemProperties.canItemBeUsed(user.player(), inventoryData.heldItem()) || ItemProperties.canItemBeUsed(user.player(), inventoryData.offhandItem()))) {
      configuration = configuration.withActiveHand();
    }
    // block invalid sprint
    if (sprinting && keyForward != 1) {
      configuration = configuration.withoutKeypress();
    } else if (sprinting) {
      if (movementData.isSneaking() && !configuration.isJumping()) {
        configuration = configuration.withoutSprinting();
      } else {
        configuration = configuration.withSprinting();
      }
    }
    // block inventory move
    if (inventoryData.inventoryOpen()) {
      configuration = configuration.withoutKeypress();
    }
    movementData.physicsJumped = configuration.isJumping();
//    movementData.sprintMove = configuration.isSprinting();
    motion.setToBaseMotionFrom(movementData);
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulationResult = simulator.simulate(user, motion, movementData, configuration);
    Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
    Timings.CHECK_PHYSICS_PROC_BIA.stop();
    return simulationResult;
  }

  private static final boolean[] ALWAYS = new boolean[]{true};
  private static final boolean[] OPTIMISTIC = new boolean[]{true, false};
  private static final boolean[] PESSIMISTIC = new boolean[]{false, true};
  private static final boolean[] NEVER = new boolean[]{false};

  private static final int[][] KEYS_USAGE_ORDERED = {{1, 0}, {0, 0}, {1, -1}, {1, 1}, {0, -1}, {0, 1}, {-1, -1}, {-1, 0}, {-1, 1}};

  private SimulationStack simulateMovementIterative(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_ITR.start();
    MetadataBundle meta = user.meta();
    InventoryMetadata inventoryData = meta.inventory();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocol = meta.protocol();
    SimulationStack simulationStack = SimulationStack.of(user);
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater();
    boolean lastOnGround = movementData.lastOnGround();
    boolean estimatedJump = Math.abs(movementData.motionY() - (1 - user.sizeOf(movementData.pose()).height() % 1)) < 1e-5 || Math.abs(movementData.motionY() - movementData.jumpMotion()) < 0.0001;
    boolean skipUseItem = (!protocol.sprintWhenHandActive() && movementData.sprinting) || !inventoryData.usableItemInEitherHand();
    boolean requireUseItem = !protocol.combatUpdate() && inventoryData.handActive() && inventoryData.pastHotBarSlotChange > 20;

    if (requireUseItem && movementData.pastEntityUse <= inventoryData.handActiveTicks) {
      requireUseItem = false;
    }

    if (requireUseItem) {
//      user.player().sendMessage("Require use item " + inventoryData.handActive() + " " + inventoryData.pastHotBarSlotChange + " " + inventoryData.pastSlotSwitch);
      skipUseItem = false;
    }

    // if we are under blocks, this gives us extra simulations, with smaller inputs (reduces false positives)
    if (user.sizeOf(movementData.pose()).height() <= 1) {
      skipUseItem = false;
    }

    if ((requireUseItem || skipUseItem) && user.meta().inventory().couldChargeCrossbow()) {
      requireUseItem = false;
      skipUseItem = false;
    }

    if (!detectNoSlowdown) {
      skipUseItem = false;
      requireUseItem = false;
    }

    int iterativeRuns = 0;
    int nearestForwardKey = -2, nearestStrafeKey = -2;
    double nearestKeyDistance = Double.MAX_VALUE;

    List<Superposition<?>> superpositions = null;
    int[] correctSuperpositions = null;
    if (useSuperpositions) {
      superpositions = movementData.superpositions();
      correctSuperpositions = new int[superpositions.size()];
    }

    SIMULATION:
    for (int j = 0; j < (useSuperpositions ? superpositions.size() : 1); j++) {
      Superposition<?> superposition = useSuperpositions ? superpositions.get(j) : null;
      int variations = useSuperpositions ? Math.max(superposition.variationsCount(), 1) : 1;
      for (int variationIndex = 0; variationIndex < variations; variationIndex++) {
        if (useSuperpositions) {
          superposition.applyVariation(variationIndex);
        }
        boolean[] sprintSelector;
        if (protocol.combatUpdate()) {
          sprintSelector = movementData.sprintingAllowed() || movementData.hasSprintSpeed ? /* surprisingly pessimistic */ PESSIMISTIC : NEVER;
        } else {
          sprintSelector = movementData.sprinting ? ALWAYS : NEVER;
        }
        for (boolean sprinting : sprintSelector) {
          movementData.refreshFriction(sprinting);
          for (boolean useItemState : inventoryData.handActive() ? OPTIMISTIC : PESSIMISTIC) {
            if (skipUseItem && useItemState) {
              continue;
            }
            if (requireUseItem && !useItemState) {
              continue;
            }
            IterativeStudy.USE_ITEM_ITERATOR.run();
            for (boolean attackReduce : PESSIMISTIC) {
              if (attackReduce && (movementData.pastPlayerAttackPhysics >= 1 || AttackDispatcher.REDUCING_DISABLED)) {
                continue;
              }
//              if (attackReduce && sprinting && movementData.lastSprinting) {
//                continue;
//              }
              IterativeStudy.ATTACK_REDUCE_ITERATOR.run();
              for (boolean jumped : estimatedJump ? OPTIMISTIC : PESSIMISTIC) {
                // Jumps are only allowed on the ground :(
                if (jumped && !lastOnGround && !inLava && !inWater) {
                  continue;
                }
                if (jumped && movementData.denyJump()) {
                  continue;
                }
                if (sprinting && movementData.isSneaking() && !jumped) {
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
                  if (simulationStack.smallestDistance() <= (movementData.receivedFlyingPacketIn(2) ? REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT : REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT)) {
                    if (useSuperpositions) {
                      correctSuperpositions[j] = variationIndex;
                    }
                    break SIMULATION;
                  }
                }
              }
            }
          }
        }
        if (useSuperpositions) {
          superposition.resetVariation(variationIndex);
        }
      }
    }
    if (useSuperpositions) {
      for (int i = 0; i < superpositions.size(); i++) {
        Superposition<?> superposition = superpositions.get(i);
        superposition.collapseVariation(correctSuperpositions[i]);
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
    simulationStack.setTrials(iterativeRuns);
    Timings.CHECK_PHYSICS_PROC_ITR.stop();
    return simulationStack;
  }

  private Simulation simulateAndAppend(
    User user,
    Simulator simulator,
    SimulationStack result,
    MovementConfiguration configuration,
    boolean forceApply
  ) {
    MovementMetadata movementData = user.meta().movement();
    InventoryMetadata inventoryData = user.meta().inventory();
    Motion motion = movementData.motionProcessorContext;
    motion.setToBaseMotionFrom(movementData);
    Simulation simulation = simulator.simulate(
      user, motion, movementData, configuration
    );
    double distance = simulation.accuracy(movementData.motion());
    if (forceApply || inventoryData.handActive() == configuration.isHandActive() || distance < 0.001) {
      simulation = simulation.reusableCopy();
      result.tryAppendToState(simulation, distance);
    }
    return simulation;
  }
}