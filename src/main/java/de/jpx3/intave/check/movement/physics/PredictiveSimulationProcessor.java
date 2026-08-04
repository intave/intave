package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostic.IterativeStudy;
import de.jpx3.intave.diagnostic.KeyPressStudy;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.Input;
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

    if (IntaveLogger.traceEnabled()) {
      IntaveLogger.trace("[BRANCH-DEBUG] " + user.player().getName()
        + " simulator=" + simulator.getClass().getSimpleName()
        + " searchKeys=" + searchKeys
        + " externalKeyApply=" + movementData.externalKeyApply
        + " elytraFlying=" + movementData.elytraFlying
        + " inWater=" + movementData.inWater
        + " inLava=" + movementData.inLava()
        + " isInVehicle=" + movementData.isInVehicle()
        + " inputFwd=" + movementData.input.forwardKey());
    }

    if (movementData.externalKeyApply) {
      // vehicles sent us the keys
      return simulateWithKeyPress(user, simulator, movementData.clientForwardKey, movementData.clientStrafeKey, movementData.clientPressedJump);
    } else if (searchKeys) {
      if (MinecraftVersions.VER1_21_3.atOrAbove()) {
        // 1.21.2+ clients report their movement keys directly via the
        // ServerboundPlayerInputPacket (mapped to STEER_VEHICLE here). It is only
        // resent when the input *changes*, so the current key state lives
        // persistently in movementData.input rather than the per-tick
        // externalKeyApply flag. Use it instead of brute-force guessing: the guess
        // path was resolving to "no keys pressed", so the prediction was pure
        // friction decay (~0.55x of the real speed) and every walking player got
        // flagged for moving too fast. The received motion is still validated
        // against the simulated result, so a speed cheat pressing forward is
        // caught the same way.
        //
        // NB: only the *movement* keys (forward/strafe) are taken from the input
        // packet -- those apply every tick while held, so they're accurate. The
        // jump bit is NOT usable directly: it reports "jump key held", not "jumped
        // this tick". A player holding jump while bunny-hopping reports jump=true
        // every tick, but only actually jumps on a ground-contact tick; feeding the
        // held bit in made us predict a jump (+0.42 Y, +0.2 sprint boost) on every
        // ground tick and over-predict -> false "moved incorrectly" flags. So the
        // jump is detected from the received vertical motion instead, exactly like
        // the legacy key-search path does.
        Input input = movementData.input;
        boolean jumped = detectJumpFromMotion(movementData);
        Simulation simulation = simulateWithKeyPress(user, simulator, input.forwardKey(), input.sidewaysKey(), jumped);
        simulation = resolveProneAmbiguity(user, simulator, simulation, input, jumped);
        simulation = resolveJumpAmbiguity(user, simulator, simulation, input, jumped);
        return resolveStaleInput(user, simulator, simulation);
      }
      // legacy clients: we must search and guess the keys
      return performKeySearchSimulation(user, simulator);
    } else {
      // keys don't matter
      return simulateWithKeyPress(user, simulator, 0, 0, false);
    }
  }

  /**
   * Detects whether the player jumped on this tick purely from the received
   * vertical motion, mirroring the legacy key-search paths
   * ({@link #simulateMovementKeyPredictionBiased} / {@link #simulateMovementLastKeyBiased}).
   * This is used instead of the client's (held) jump input bit for 1.21.2+.
   */
  private boolean detectJumpFromMotion(MovementMetadata movementData) {
    if (movementData.denyJump()) {
      return false;
    }
    if (movementData.inWater || movementData.inLava()) {
      // In liquids the held jump bit IS per-tick accurate: vanilla's
      // jumpInLiquid() adds +0.04 to motionY on EVERY tick the key is held
      // (even while sinking), exactly like movement keys apply while held.
      // The "held vs edge-triggered" problem only exists for ground jumps.
      // Detecting from motionY > 0 here was wrong: a player holding space
      // while descending in water still gets the +0.04 boost, so we
      // under-predicted Y by exactly 0.04 every tick -> false flags while
      // swimming.
      return movementData.input.jump();
    }
    if (movementData.lastOnGround) {
      double motionY = movementData.motionY();
      if (Math.abs(motionY - 0.2) < 1e-5 || motionY == movementData.jumpMotion()) {
        return true;
      }
      // A launch close to, but not exactly, our jumpMotion() is still a jump (jump
      // boost, a jump-strength attribute the server resolved differently). Anything
      // clearly below it is far more likely an auto-step -- the block edge the player
      // walked into lifts them without any vertical velocity (0.1875 onto a bottom
      // trapdoor, 0.3125 from there onto a slab, 0.5 onto a slab) -- and guessing
      // "jump" there costs a whole ballistic arc plus an airborne model for the ticks
      // after it. Whichever way this guess lands, resolveJumpAmbiguity retries the
      // other one when the movement is not reproduced.
      return motionY >= movementData.jumpMotion() * 0.9
        && motionY <= movementData.jumpMotion() + 0.05
        && movementData.ticksPast(EXTERNAL_VELOCITY) > 1;
    }
    return false;
  }

  private static final double AMBIGUITY_RETRY_ACCURACY = 0.002;
  private static final double STALE_INPUT_SEARCH_ACCURACY = 0.01;

  /**
   * Falls back to the brute-force key search when the client's reported input does not
   * explain the movement at all.
   * <p>
   * {@code ServerboundPlayerInputPacket} is only sent when the input CHANGES, so
   * {@link MovementMetadata#input} is persistent state rather than a per-tick fact. One
   * dropped, delayed or reordered packet leaves it wrong until the player next changes
   * keys, and every tick until then is predicted without the acceleration the client
   * actually applied -- a detection storm that starts and stops out of nowhere. Live
   * traces show it happening: ground ticks reporting no keys at all while the player
   * accelerates, received motion running 2x to 10x the prediction. The pre-1.21.2 path
   * never trusted a single hypothesis for exactly this reason, so borrow it whenever
   * ours does not fit; when the input is right (the overwhelming majority of ticks) the
   * search is never even entered.
   */
  private Simulation resolveStaleInput(User user, Simulator simulator, Simulation simulation) {
    MovementMetadata movementData = user.meta().movement();
    double accuracy = simulation.accuracy(movementData.motion());
    if (accuracy <= STALE_INPUT_SEARCH_ACCURACY) {
      return simulation;
    }
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    boolean physicsJumped = movementData.physicsJumped;
    Simulation previous = simulation.reusableCopy();
    Simulation searched = performKeySearchSimulation(user, simulator);
    if (searched.accuracy(movementData.motion()) < accuracy) {
      return searched;
    }
    // the search overwrote these while trying its candidates
    movementData.keyForward = keyForward;
    movementData.keyStrafe = keyStrafe;
    movementData.physicsJumped = physicsJumped;
    return previous;
  }

  /**
   * Resolves whether the client really jumped this tick.
   * <p>
   * Upward motion off the ground is not proof of a jump: the auto-step lifts the
   * player by the height of the block edge they walked into -- 0.1875 onto a bottom
   * trapdoor, 0.3125 from one onto a slab, 0.5 onto a slab -- while their vertical
   * velocity stays zero. Predicting a jump there produces a whole ballistic arc the
   * client never flies, and worse, it leaves our model airborne for the following
   * ticks, where none of the ground-based tolerances apply anymore. Conversely a real
   * jump that misses our jumpMotion() (block jump factor, jump boost) must not be
   * predicted as a step. Both directions are cheap to just try: simulate the other
   * assumption and keep whichever reproduces the received motion, which is what the
   * legacy key search does with the very same bit.
   */
  private Simulation resolveJumpAmbiguity(
    User user, Simulator simulator, Simulation simulation, Input input, boolean jumped
  ) {
    MovementMetadata movementData = user.meta().movement();
    double accuracy = simulation.accuracy(movementData.motion());
    if (accuracy <= AMBIGUITY_RETRY_ACCURACY) {
      return simulation;
    }
    boolean alternative = !jumped;
    if (alternative && (!movementData.lastOnGround || movementData.denyJump())
      && !movementData.inWater && !movementData.inLava()) {
      return simulation; // a jump was not possible here, nothing to try
    }
    Simulation previous = simulation.reusableCopy();
    Simulation retry = simulateWithKeyPress(
      user, simulator, input.forwardKey(), input.sidewaysKey(), alternative
    );
    return retry.accuracy(movementData.motion()) < accuracy ? retry : previous;
  }

  private static final double PRONE_AMBIGUITY_RETRY_ACCURACY = AMBIGUITY_RETRY_ACCURACY;

  /**
   * Resolves the one-tick ambiguity of the client's prone ("moving slowly") state.
   * <p>
   * The client scales its movement input by the sneaking-speed attribute while
   * crawling, using the pose it held at the START of the tick; our pose is derived
   * from the position the client has already moved to. Around every transition into
   * or out of the crawl the two therefore disagree for a tick or two, and that is
   * worth a factor of ~3 on the acceleration -- more than enough to flag. While the
   * state is ambiguous, re-run the tick with the opposite assumption and keep
   * whichever reproduces the received motion, exactly like the legacy key search
   * tries the plausible client states. The worst a cheater gains is walking speed
   * while prone, for the few ticks around a pose change.
   */
  private Simulation resolveProneAmbiguity(
    User user, Simulator simulator, Simulation simulation, Input input, boolean jumped
  ) {
    MovementMetadata movementData = user.meta().movement();
    double accuracy = simulation.accuracy(movementData.motion());
    if (accuracy <= PRONE_AMBIGUITY_RETRY_ACCURACY) {
      return simulation;
    }
    // Deliberately NOT limited to the transition window: our pose comes from a
    // collision test against our own block shapes, so a block we resolve wrongly
    // (a bottom trapdoor, a slab, a ladder) can make us believe a player is prone
    // for as long as they stand on it. Assuming the slowdown then under-predicts by
    // 3x for the whole time, which is worse than never having applied it. Whenever
    // the primary guess does not reproduce the movement, the other assumption gets a
    // chance -- picking the closer one can only ever lower the speed we accept for a
    // prone player, so it cannot be turned into an advantage.
    // the pooled simulation object is reused by the next simulateTick call
    Simulation previous = simulation.reusableCopy();
    boolean alternativeAccepted = false;
    movementData.proneSlowdownOverride = movementData.crawling() ? -1 : 1;
    try {
      Simulation alternative = simulateWithKeyPress(
        user, simulator, input.forwardKey(), input.sidewaysKey(), jumped
      );
      if (alternative.accuracy(movementData.motion()) < accuracy) {
        // the pose we derived disagreed with the client: keep the evaluator lenient
        // for a few ticks, the next ones are likely to disagree the same way, and
        // leave the override in place so the setback simulation for this tick uses
        // the assumption we just accepted (tickComplete clears it)
        alternativeAccepted = true;
        movementData.proneAmbiguityTicks = 4;
        return alternative;
      }
    } finally {
      if (!alternativeAccepted) {
        movementData.proneSlowdownOverride = 0;
      }
    }
    return previous;
  }

  @Override
  public Simulation simulateWithKeyPress(
    User user, Simulator simulator, int forward, int strafe, boolean jumped
  ) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    // A ground jump requires standing on the ground last tick, but a liquid
    // jump (+0.04/tick, see BaseSimulator's inWater/inLava branches) does not
    // -- lastOnGround is false while swimming, and masking with it here
    // silently dropped every liquid jump and under-predicted Y by 0.04/tick.
    jumped &= movementData.lastOnGround || movementData.inWater || movementData.inLava();
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
    // Every other simulateTick caller refreshes friction first (see the
    // before-velocity / locked-key / key-search paths); this direct path did not,
    // so the friction-influenced move speed stayed at the metadata's default 0 and
    // the simulation applied ZERO acceleration -> prediction was pure friction
    // decay and every moving player was flagged. Refresh it here too.
	  movementData.refreshFriction(configuration.isSprinting());
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