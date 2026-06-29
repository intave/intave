package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostic.IterativeStudy;
import de.jpx3.intave.diagnostic.KeyPressStudy;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import static de.jpx3.intave.check.movement.physics.MoveMetric.*;

public final class PredictiveSimulationProcessor implements SimulationProcessor {

  /*
   * this class is rather messy
   * please refactor
   * */
  private final boolean itemUsageReset;
  private final boolean detectNoSlowdown;

  public PredictiveSimulationProcessor(boolean itemUsageReset, boolean detectNoSlowdown) {
    this.itemUsageReset = itemUsageReset;
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

    Motion motion = movementData.mutableBaseMotionCopy();

    MovementConfiguration configuration = MovementConfiguration.select(
      forward, strafe, 0,
      movementData.sprintingAllowed(),
      jumped, meta.inventory().handActive(), false
    );
	  return simulator.simulateTick(user, motion, movementData, configuration);
  }

  private static final double REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT = 0.002;
  private static final double REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT = 0.008;

  private Simulation performKeySearchSimulation(User user, Simulator simulator) {
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
//      if (simulationStack.trials() >= 8) {
        simulation.append("i" + simulationStack.trials());
//      }
    }
    KeyPressStudy.enterKeyPress(movementData.keyForward, movementData.keyStrafe);
    return simulation;
  }

  private void enterIterativeSimulationStack(User user, SimulationStack simulationStack) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();
    ProtocolMetadata protocol = meta.protocol();
//    if (movementData.past(PLAYER_REDUCE_ATTACK_PHYSICS) == 0 && simulationStack.sprinted()/*movementData.sprinting*/ && !simulationStack.reduced()) {
//      movementData.ignoredAttackReduce = true;
//    }
    /* misplaced - please solve this otherwise */
    boolean movementSuggestsHandIsActive = simulationStack.handActive();
    boolean packetsSuggestsHandIsActive = inventoryData.handActive();
    if (packetsSuggestsHandIsActive && !movementSuggestsHandIsActive) {
      boolean releaseHandConditions = Hypot.fast(movementData.motionX(), movementData.motionZ()) > 0.3 || movementData.ticksPast(TELEPORT) >= 2;
      boolean itemIsBow = ItemProperties.isBow(meta.inventory().activeItemType()) || ItemProperties.isBow(meta.inventory().offhandItemType());
      boolean viaVersionBlockReplacement = meta.protocol().viaVersionShieldBlockReplacement();
      if (releaseHandConditions && (!itemIsBow || (inventoryData.handActiveTicks > 3 && !viaVersionBlockReplacement)) && itemUsageReset) {
        meta.inventory().releaseItemNextTick();

        if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
          user.player().sendMessage(IntavePlugin.prefix() + "Requesting item usage reset as " + ChatColor.RED + "movement/state discrepancy ");
        }
      }
    }

    boolean canExpectCorrectReduce = !protocol.combatUpdate() && movementData.ticksPast(VELOCITY) > 1 && movementData.motion().horizontalLength() > 0.2;
    boolean invalidReduceTicks = simulationStack.reduceTicks() != movementData.reduceTicks;
    if (canExpectCorrectReduce && invalidReduceTicks) {
      movementData.invalidReduceVL = Math.min(movementData.invalidReduceVL + 1, 10);
    } else if (movementData.invalidReduceVL > 0) {
      movementData.invalidReduceVL -= 0.25;
    }
    movementData.forceCorrectReduce = movementData.invalidReduceVL > 5;

    movementData.keyForward = simulationStack.forward();
    movementData.keyStrafe = simulationStack.strafe();
    movementData.physicsJumped = simulationStack.jumped();
  }

  private static final double REQUIRED_PREDICTION_ACCURACY_FOR_PRED_BIAS_PROCEED = 0.1;

  private Simulation simulateMovementKeyPredictionBiased(User user, Simulator simulator) {
    Timings.CHECK_PHYSICS_PROC_BIA.start();
    Timings.CHECK_PHYSICS_PROC_PRED_BIA.start();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();
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
      movementData.keyForward = 0;
      movementData.keyStrafe = 0;
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_PRED_BIA.stop();
      return Simulation.invalid();
    }
    MovementConfiguration configuration = MovementConfiguration.blank();
    // keys
    configuration = configuration.withKeypress(forwardKeyFrom(direction), strafeKeyFrom(direction));
    // jump
    if (jumped) {
      configuration = configuration.withJump();
    }
    // active hand
    if (inventoryData.handActive() && (ItemProperties.canItemBeUsed(user.player(), inventoryData.heldItem()) || ItemProperties.canItemBeUsed(user.player(), inventoryData.offhandItem()))) {
      configuration = configuration.withActiveHand();
    }
    // reducing
    configuration = configuration.withReduceTicks(movementData.reduceTicks);
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
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulation = simulator.simulateTick(
      user, movementData.mutableBaseMotionCopy(),
      movementData.unmodifiable(), configuration
    );
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
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();

    int keyForward = movementData.lastKeyForward;
    int keyStrafe = movementData.lastKeyStrafe;
    boolean inventoryOpen = inventoryData.inventoryOpen();

    // return if prediction bias already has calculated this keys
    if (!inventoryOpen && keyForward == movementData.keyForward && keyStrafe == movementData.keyStrafe) {
      Timings.CHECK_PHYSICS_PROC_LK_BIA.stop();
      Timings.CHECK_PHYSICS_PROC_BIA.stop();
      return Simulation.invalid();
    }
    MovementConfiguration configuration = MovementConfiguration.blank();
    // keys
    configuration = configuration.withKeypress(keyForward, keyStrafe);
    // reducing
    configuration = configuration.withReduceTicks(movementData.reduceTicks);
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
      configuration = configuration.withSprinting();
    }
    // block inventory move
    if (inventoryData.inventoryOpen()) {
      configuration = configuration.withoutKeypress();
    }
    movementData.physicsJumped = configuration.isJumping();
    movementData.keyForward = configuration.forward();
    movementData.keyStrafe = configuration.strafe();
    movementData.refreshFriction(sprinting);
    Simulation simulationResult = simulator.simulateTick(
      user, movementData.mutableBaseMotionCopy(),
      movementData.unmodifiable(), configuration
    );
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
    AbilityMetadata abilities = meta.abilities();
    InventoryMetadata inventoryData = meta.inventory();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocol = meta.protocol();
    SimulationStack simulationStack = SimulationStack.of(user);
    boolean inLava = movementData.inLava();
    boolean inWater = movementData.inWater();
    boolean lastOnGround = movementData.lastOnGround();
    boolean estimatedJump = Math.abs(movementData.motionY() - (1 - user.sizeOf(movementData.pose()).height() % 1)) < 1e-5 || Math.abs(movementData.motionY() - movementData.jumpMotion()) < 0.0001;
    boolean skipUseItem = (!protocol.sprintWhenHandActive() && movementData.sprinting && !protocol.viaVersionShieldBlockReplacement())
      || !inventoryData.usableItemInEitherHand();
    // dont require use item for bows
    boolean requireUseItem = !protocol.combatUpdate() && inventoryData.handActive() && inventoryData.pastHotBarSlotChange > 20
      && (inventoryData.heldItem() == null || inventoryData.heldItem().getType() != Material.BOW)
    ;
//    boolean requireUseItem = inventoryData.handActive() && inventoryData.pastHotBarSlotChange > 20 && (!protocol.combatUpdate() || inventoryData.heldItemType() != Material.BOW);

    if (requireUseItem && movementData.ticksPast(ENTITY_USE) <= inventoryData.handActiveTicks) {
      requireUseItem = false;
    }

    // if we are under blocks, this gives us extra simulations, with smaller inputs (reduces false positives)
    if (requireUseItem || user.sizeOf(movementData.pose()).height() <= 1) {
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

    boolean[] sprintSelector;
    if (protocol.combatUpdate()) {
      sprintSelector = movementData.sprintingAllowed() || movementData.hasSprintSpeed ? /* surprisingly pessimistic */ PESSIMISTIC : NEVER;
    } else {
      boolean certain = movementData.ticksPast(SPRINT_CHANGE) > 1;
      sprintSelector = movementData.sprinting ? (certain ? ALWAYS : OPTIMISTIC) : (certain ? NEVER : PESSIMISTIC);
    }


    SIMULATION:
    for (boolean sprinting : sprintSelector) {
      if (sprinting && abilities.foodLevel < 6) {
        continue;
      }
      movementData.refreshFriction(sprinting);
      for (boolean useItemState : inventoryData.handActive() ? OPTIMISTIC : PESSIMISTIC) {
        if (skipUseItem && useItemState) {
          continue;
        }
        if (requireUseItem && !useItemState) {
          continue;
        }
        if (sprinting && useItemState && !protocol.combatUpdate()) {
          continue;
        }
        IterativeStudy.USE_ITEM_ITERATOR.run();
        boolean canExpectCorrectReduce = !protocol.combatUpdate() && movementData.ticksPast(VELOCITY) > 1 && movementData.motion().horizontalLength() > 0.2;
        boolean enforceCorrectReduction = movementData.forceCorrectReduce && canExpectCorrectReduce;
        for (int reduceIndex = 0; reduceIndex <= Math.min(movementData.reduceTicks, 3); reduceIndex++) {
//              if (enforceCorrectReduction && reduceIndex > movementData.reduceTicks) {
//                continue;
//              }
//              if (!sprinting && reduceIndex > 0) {// && !protocol.combatUpdate()) {
//                continue;
//              }
          for (boolean reduceBefore : (reduceIndex > 0 ? PESSIMISTIC : NEVER)) {
            IterativeStudy.ATTACK_REDUCE_ITERATOR.run();
            for (boolean jumped : estimatedJump ? OPTIMISTIC : PESSIMISTIC) {
              // Jumps are only allowed on the ground :(
              if (jumped && !lastOnGround && !inLava && !inWater) {
                continue;
              }
              if (jumped && movementData.denyJump()) {
                continue;
              }
              if (sprinting && movementData.isSneaking() && !jumped /* temporary -> */&& !protocol.combatUpdate()) {
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
                  keyForward, keyStrafe, reduceIndex, sprinting, jumped, useItemState, reduceBefore
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
                double requiredAccuracy = movementData.receivedFlyingPacketIn(2) &&
                  protocol.flyingPacketUncertaintyRadius() > 0.001 ?
                  REQUIRED_ACCURACY_FOR_FLYING_PROC_EXIT :
                  REQUIRED_ACCURACY_FOR_QUICK_PROC_EXIT;

                if (simulationStack.smallestDistance() < requiredAccuracy) {
                  break SIMULATION;
                }
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
        MovementConfiguration.blank(),
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
    Simulation simulation = simulator.simulateTick(
      user, movementData.mutableBaseMotionCopy(),
      movementData.unmodifiable(), configuration
    );
    double distance = simulation.accuracy(movementData.motion());
    if (forceApply || inventoryData.handActive() == configuration.isHandActive() || distance < 0.001) {
      simulation = simulation.reusableCopy();
      result.tryAppendToState(simulation, distance);
    }
    return simulation;
  }
}