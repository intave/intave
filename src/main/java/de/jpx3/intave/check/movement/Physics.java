package de.jpx3.intave.check.movement;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.analytics.GlobalStatisticsRecorder;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.annotate.refactoring.SplitMeUp;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.fluid.LegacyWaterflow;
import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.physics.*;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.Superposition;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.player.collider.Collider;
import de.jpx3.intave.player.collider.complex.ColliderSimulationResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.math.MathHelper.formatPosition;
import static de.jpx3.intave.shade.ClientMathHelper.floor;

@Relocate
public final class Physics extends Check {
  private final static double VL_DECREMENT_PER_VALID_MOVE = 0.05;
  private final static double VELOCITY_VL_THRESHOLD = 6;

  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  private final SimulationProcessor simulationProcessor;
  private final SimulationEvaluator simulationEvaluator;
  private final FallDamageApplier fallDamageApplier;
  private final boolean highToleranceMode, resetItemUsage;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, VL_DECREMENT_PER_VALID_MOVE * 20);
    this.highToleranceMode = configuration().settings().boolBy("high-tolerance", false);
    this.resetItemUsage = configuration().settings().boolBy("reset-item-usage", true);
    this.simulationProcessor = new PredictiveSimulationProcessor(resetItemUsage);
    this.simulationEvaluator = new SimulationEvaluator();
    setDefaultMitigationStrategy(MitigationStrategy.CAREFUL);
    this.fallDamageApplier = new FallDamageApplier();
    linkCheckToPoseSimulators();
  }

  private void linkCheckToPoseSimulators() {
    for (Simulator simulator : Simulators.simulators()) {
      simulator.enterLinkage(this);
    }
  }

  @DispatchTarget
  public void receiveMovement(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    Simulator simulator = selectSimulator(user);
    movementData.setSimulator(simulator);
    movementData.stepHeight = simulator.stepHeight();
    if (clientData.waterUpdate() && movementData.sneaking && movementData.inWater) {
      handleSneakInWater(user);
    }
    updateAquatics(user);
    simulateMotionClamp(user);
    Timings.CHECK_PHYSICS_PROC_TOT.start();
    predictFlyingPacketBeforeVelocity(user);
    // simulation
    Simulation simulation;
    try {
      simulation = simulationProcessor.simulate(user, movementData.simulator());
    } catch (IllegalStateException exception) {
      user.synchronizedDisconnect("An error occurred processing a movement packet");
      exception.printStackTrace();
      return;
    }
    ColliderSimulationResult collider = simulation.collider();
    movementData.onGround = collider.onGround();
    movementData.collidedHorizontally = collider.collidedHorizontally();
    movementData.collidedVertically = collider.collidedVertically();
    movementData.physicsResetMotionX = collider.resetMotionX();
    movementData.physicsResetMotionZ = collider.resetMotionZ();
    movementData.step = collider.step();
    Timings.CHECK_PHYSICS_PROC_TOT.stop();
    Timings.CHECK_PHYSICS_EVAL.start();
    // evaluation
    evaluateBestSimulation(user, simulation);
    Timings.CHECK_PHYSICS_EVAL.stop();
    movementData.lastMovement = System.currentTimeMillis();
    movementData.lastKeyStrafe = movementData.keyStrafe;
    movementData.lastKeyForward = movementData.keyForward;
    movementData.pastRiptideSpin++;
  }

  private void simulateMotionClamp(User user) {
    MovementMetadata movementData = user.meta().movement();
    double resetMotion = movementData.resetMotion();
    if (Math.abs(movementData.physicsMotionX) < resetMotion) {
      movementData.physicsMotionX = 0.0;
    }
    if (Math.abs(movementData.physicsMotionY) < resetMotion) {
      movementData.physicsMotionY = 0.0;
    }
    if (Math.abs(movementData.physicsMotionZ) < resetMotion) {
      movementData.physicsMotionZ = 0.0;
    }
  }

  private final static int BOAT_ID = 41;

  private Simulator selectSimulator(User user) {
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata protocol = user.meta().protocol();
    boolean clientVehicleMovement = MinecraftVersions.VER1_9_0.atOrAbove() && protocol.combatUpdate();

    if (movementData.isInVehicle() && clientVehicleMovement) {
      EntityShade entityShade = movementData.ridingEntity();
      int typeId = entityShade.typeData().identifier();
      return typeId == BOAT_ID ? Simulators.BOAT : Simulators.HORSE;
    } else {
      boolean inLava = movementData.inLava();
      boolean inWater = movementData.inWater();
      Pose pose = movementData.pose();
      if (pose == Pose.FALL_FLYING && !inWater && !inLava) {
        return Simulators.ELYTRA;
      }
    }
    return Simulators.PLAYER;
  }

  @DispatchTarget
  public void endMovement(User user, boolean hasMovement) {
    MovementMetadata movementData = user.meta().movement();
    double motionX = movementData.motionX();
    double motionY = movementData.motionY();
    double motionZ = movementData.motionZ();
    if (hasMovement) {
      Simulator simulator = movementData.simulator();
      if (movementData.pastVelocity == 0) {
        if (movementData.physicsJumped && movementData.lastVelocityApplicableForJumpDenial()) {
          movementData.physicsJumpedOverrideVL++;
        } else if (movementData.physicsJumpedOverrideVL > 0) {
          movementData.physicsJumpedOverrideVL--;
        }
      }
      simulator.prepareNextTick(user, movementData.positionX, movementData.positionY, movementData.positionZ, motionX, motionY, motionZ);
    }
  }

  @DispatchTarget
  public void updateOnGroundIfFlying(User user) {
    MovementMetadata movementData = user.meta().movement();
    double physicsMotionX = movementData.physicsMotionX;
    double physicsMotionY = movementData.physicsMotionY;
    double physicsMotionZ = movementData.physicsMotionZ;
    if (Math.abs(physicsMotionX) < movementData.resetMotion()) {
      physicsMotionX = 0;
    }
    if (Math.abs(physicsMotionY) < movementData.resetMotion()) {
      physicsMotionY = 0;
    }
    if (Math.abs(physicsMotionZ) < movementData.resetMotion()) {
      physicsMotionZ = 0;
    }
    double motionX = physicsMotionX * 0.91f;
    double motionY = (physicsMotionY - 0.08) * 0.98f;
    double motionZ = physicsMotionZ * 0.91f;
    SimpleColliderSimulationResult colliderResult = Collider.simplifiedCollision(
      user.player(),
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      motionX, motionY, motionZ
    );
    movementData.onGround = colliderResult.onGround();
  }

  private void predictFlyingPacketBeforeVelocity(User user) {
    MovementMetadata movementData = user.meta().movement();
    if (movementData.pastVelocity != 0) {
      return;
    }
    double motionX = movementData.physicsMotionXBeforeVelocity * 0.91f;
    double motionY = (movementData.physicsMotionYBeforeVelocity - 0.08) * 0.98f;
    double motionZ = movementData.physicsMotionZBeforeVelocity * 0.91f;
    if (motionX != 0 && motionY != 0 && motionZ != 0) {
      SimpleColliderSimulationResult colliderResult = Collider.simplifiedCollision(
        user.player(),
        movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
        motionX, motionY, motionZ
      );
      motionX = colliderResult.motionX();
      motionY = colliderResult.motionY();
      motionZ = colliderResult.motionZ();

      if (colliderResult.onGround() || movementData.onGround) {
        double distance = motionX * motionX + motionY * motionY + motionZ * motionZ;
        if (distance < 0.009) {
          movementData.physicsUnpredictableVelocityExpected = true;
          movementData.setPastFlyingPacketAccurate(0);
        }
      }
    }
  }

  public void updateAquatics(User user) {
    MovementMetadata movementData = user.meta().movement();
    updateInWater(user);
    movementData.updateEyesInWater();
  }

  private void handleSneakInWater(User user) {
    MovementMetadata movementData = user.meta().movement();
    movementData.physicsMotionY -= 0.04F;
  }

  private void updateInWater(User user) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    MovementMetadata movementData = meta.movement();
    if (clientData.waterUpdate()) {
      movementData.inWater = Fluids.handleFluidAcceleration(user, movementData.boundingBox());
    } else {
      BoundingBox entityBoundingBox = movementData.boundingBox();
      BoundingBox checkableBoundingBox = entityBoundingBox
        .grow(0.0D, -0.4000000059604645D, 0.0D)
        .contract(0.001D, 0.001D, 0.001D);
      movementData.inWater = LegacyWaterflow.handleMaterialAcceleration(user, checkableBoundingBox);
    }
    if (movementData.inWater) {
      movementData.pastWaterMovement = 0;
      movementData.artificialFallDistance = 0;
    }
  }

  /**
   * This method is too big, please refactor
   */
  @SplitMeUp
  private void evaluateBestSimulation(User user, Simulation simulation) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    boolean spectator = player.getGameMode() == GameMode.SPECTATOR;

    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocolMetadata = meta.protocol();
    ViolationMetadata violationLevelData = meta.violationLevel();
    AbilityMetadata abilityData = meta.abilities();
    BlockStateAccess blockStateAccess = user.blockStates();

    ColliderSimulationResult expectedMovement = simulation.collider();
    Motion context = expectedMovement.motion();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.probablyFlying() || abilityData.allowFlying();
    String key = resolveKeysFromInput(keyForward, keyStrafe);

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
    double predictedX = context.motionX;
    double predictedY = context.motionY;
    double predictedZ = context.motionZ;
    double differenceX = predictedX - receivedMotionX;
    double differenceY = predictedY - receivedMotionY;
    double differenceZ = predictedZ - receivedMotionZ;
    double distance = MathHelper.hypot3d(differenceX, differenceY, differenceZ);
    double receivedPositionX = movementData.positionX;
    double receivedPositionY = movementData.positionY;
    double receivedPositionZ = movementData.positionZ;
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onLadderCurrent = MovementHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean onLadder = onLadderCurrent | movementData.onLadderLast;
    movementData.onLadderLast = onLadderCurrent;

    // Entity collision check
    boolean collidedWithBoat = movementData.collidedWithBoat();
    boolean skipVLCalculation = distance <= 0.00001;
    double verticalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateVerticalViolationLevelIncrease(user, predictedY, onLadder, collidedWithBoat);
    double horizontalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateHorizontalViolationIncrease(user, predictedX, predictedZ, onLadder, collidedWithBoat);

    if (onLadder) {
      movementData.artificialFallDistance = 0;
    }

    boolean velocityDetected = false;
    boolean checkVelocity = !skipVLCalculation
      && movementData.pastInWeb > 5
      && !movementData.inWater
      && !movementData.collidedWithBoat();

    if (checkVelocity && movementData.pastExternalVelocity < 10 && !movementData.recentlyEncounteredFlyingPacket(2)) {
      if (distance > 0.008 && !onLadder) {
        boolean aggressive = violationLevelData.physicsVelocityVL++ >= VELOCITY_VL_THRESHOLD;
        if (aggressive || distance > 0.01) {
          if (aggressive) {
            horizontalViolationIncrease = Math.max(2, horizontalViolationIncrease);
            velocityDetected = true;
          }
          horizontalViolationIncrease *= 10.0;
        }
      }
    }

    if (violationLevelData.physicsVelocityVL > 10) {
      violationLevelData.physicsVelocityVL = 10;
    }
    if (violationLevelData.physicsVelocityVL > 0) {
      violationLevelData.physicsVelocityVL -= 0.005;
    }

    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;
    if (movementData.simulator() == Simulators.HORSE && !IntaveControl.GOMME_MODE) {
      violationLevelIncrease = 0;
    }
    if (distance > 1e-3) {
      movementData.suspiciousMovement = true;
      for (Superposition<?> superposition : movementData.superpositions()) {
        superposition.collapseVariation(0);
      }
      Simulation otherSimulation;
      if (IntaveControl.SETBACK_WITH_PRESSED_KEYS) {
        otherSimulation = simulationProcessor.simulateWithKeyPress(user, selectSimulator(user), movementData.keyForward, movementData.keyStrafe, false);
      } else {
        otherSimulation = simulationProcessor.simulateWithoutKeyPress(user, selectSimulator(user));
      }
      Motion setbackMotion = otherSimulation.motion();
      /*
       * This will patch the hit-player-sneaking-on-a-block-edge bug (https://youtu.be/ONGnOwhQyac)
       */
      Vector lastVelocity = movementData.sneakPatchVelocity;
      if (
        movementData.isSneaking() &&
        !movementData.onGround() &&
        lastVelocity != null
      ) {
        predictedX = Math.abs(setbackMotion.motionX) < 0.05 ? setbackMotion.motionX + MathHelper.minmax(-0.05, lastVelocity.getX(), 0.05) : setbackMotion.motionX;
        predictedY = setbackMotion.motionY;
        predictedZ = Math.abs(setbackMotion.motionZ) < 0.05 ? setbackMotion.motionZ + MathHelper.minmax(-0.05, lastVelocity.getZ(), 0.05) : setbackMotion.motionZ;
        movementData.sneakPatchVelocity = null;
      } else {
        predictedX = setbackMotion.motionX;
        predictedY = setbackMotion.motionY;
        predictedZ = setbackMotion.motionZ;
      }
    }

    if (flying || spectator) {
      violationLevelIncrease = 0;
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.990;
      violationLevelData.physicsVL -= 0.012;
    }

    Location verifiedLocation = movementData.verifiedLocation();
    BoundingBox verifiedBoundingBox = BoundingBox.fromPosition(user, verifiedLocation);
    BoundingBox currentBoundingBox = BoundingBox.fromPosition(user, receivedPositionX, receivedPositionY, receivedPositionZ);

    boolean boundingBoxIntersectionLast = Collision.present(user.player(), verifiedBoundingBox);
    boolean boundingBoxIntersectionCurrent = Collision.present(user.player(), currentBoundingBox);
    boolean movedIntoBlock = !boundingBoxIntersectionLast && boundingBoxIntersectionCurrent;
    if (boundingBoxIntersectionCurrent && !spectator) {
      List<BoundingBox> intersectionBoundingBoxesCurrent = Collision.__INVALID__resolveBoxes(user.player(), currentBoundingBox);
      if (movedIntoBlock && !intersectionBoundingBoxesCurrent.isEmpty()) {
        movementData.invalidMovement = true;
        BoundingBox boundingBox = intersectionBoundingBoxesCurrent.get(0);
        double blockPositionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double blockPositionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
        double blockPositionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        Block block = VolatileBlockAccess.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
        boolean currentlyInOverride = blockStateAccess.currentlyInOverride(floor(blockPositionX), floor(blockPositionY), floor(blockPositionZ));
        boolean altered = BlockTypeAccess.hasTranslation(user, BlockTypeAccess.typeAccess(block));

        String colliderName;
        if (!Collision.blockInsideBorder(player.getWorld(), blockPositionX, blockPositionZ)) {
          colliderName = "world border";
        } else {
          String prefix = (currentlyInOverride ? "emulated" : "") + " " + (altered ? "altered" : "") + " ";
          Material type = VolatileBlockAccess.typeAccess(user, block.getLocation());
          String typeName = shortenTypeName(type);
          colliderName = prefix + typeName + " block";
        }
        String message = "moved into " + colliderName.trim();
        boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
        String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");
        if (!IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT) {
          blockStateAccess.invalidateAll();
        }
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(player).withMessage(message).withDetails(details).withVL(0).build();
        Modules.violationProcessor().processViolation(violation);
        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        Modules.mitigate().movement().emulationSetBack(player, emulationMotion, 2, true);
      }
    }

    if (!boundingBoxIntersectionCurrent && !boundingBoxIntersectionLast) {
      movementData.currentlyInBlock = false;
    }

    // Update the player's verified location
    if (spectator || violationLevelIncrease == 0 && !boundingBoxIntersectionCurrent) {
      Location location = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
      movementData.setVerifiedLocation(location, "Movement validation (normal)");
    }

    if (violationLevelIncrease > 0) {
      boolean uncommonArea = movementData.pastWaterMovement < 20
        || movementData.collidedHorizontally
        || movementData.collidedWithBoat()
        || movementData.inWeb
        || movementData.pastElytraFlying < 20;
      if (uncommonArea) {
        violationLevelIncrease /= 2;
      } else if (protocolMetadata.waterUpdate()) {
        violationLevelIncrease /= 2;
      }
      violationLevelIncrease = Math.min(200.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL + violationLevelIncrease, 200);
      violationLevelData.physicsInvalidMovementsInRow++;
      if (violationLevelData.physicsVL > 20) {
        if (!IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT) {
          blockStateAccess.invalidateAll();
        }
      }
      // resend attributes
      statisticApply(user, CheckStatistics::increaseFails);
    } else {
      violationLevelData.physicsInvalidMovementsInRow = 0;
      statisticApply(user, CheckStatistics::increasePasses);
    }

    if (!spectator && violationLevelData.physicsVL > 50 && violationLevelIncrease > 0) {
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);
      String message = "moved incorrectly";
      String details = received + " est: " + expected;

      if (velocityDetected) {
        details += ", strict";
      }

      double vl = violationLevelIncrease / (highToleranceMode ? 75 : (violationLevelData.physicsVL >= 100 ? 20 : 50));
      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(player)
        .withMessage(message)
        .withDetails(details)
        .withVL(vl)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);

      // a few helpful states
      boolean isMidAir = !movementData.onGround && !movementData.collidedHorizontally && !movementData.collidedVertically;
      boolean isOnGround = movementData.onGround;
      double distanceMoved = MathHelper.hypot3d(movementData.motionX(), movementData.motionY(), movementData.motionZ());

      boolean deepPitchViolationOverflow = violationContext.shouldCounterThreat();
      int highPitchLimit = trustFactorSetting("pa-override-threshold", player);
      boolean highPitchViolationOverflow = violationLevelData.physicsVL > highPitchLimit;
      boolean highPitchAggressiveViolationOverflow = violationLevelData.physicsVL >= Math.max(highPitchLimit, 150);

      double violationLevelBefore = violationContext.violationLevelBefore();
      double violationLevelAfter = violationContext.violationLevelAfter();

      MitigationStrategy mitigationStrategy = mitigationStrategy();

      boolean setback = false;
      double manualOverrideDistance = 0;
      switch (mitigationStrategy) {
        case AGGRESSIVE:
          setback = deepPitchViolationOverflow || (!highToleranceMode && highPitchViolationOverflow);
          manualOverrideDistance = 0.75;
          break;
        case CAREFUL:
          setback = deepPitchViolationOverflow || (highPitchViolationOverflow && (violationLevelAfter > 20 || highPitchAggressiveViolationOverflow || !user.trustFactor().atLeast(TrustFactor.YELLOW) || user.justJoined()));
          if (receivedMotionY > Math.max(0.42f, movementData.jumpMotion()) + 0.01) {
            setback = true;
          }
          manualOverrideDistance = 0.75;
          break;
        case LENIENT:
          boolean flagAnyways = (verticalViolationIncrease >= 100 && predictedY < 0 && violationLevelAfter > 30)/* || (verticalViolationIncrease >= 100)*/;
          setback = (distanceMoved > (violationLevelAfter > 30 ? 0.4 : 0.6) || violationLevelAfter > 200 || user.justJoined() || flagAnyways) && deepPitchViolationOverflow && (highPitchAggressiveViolationOverflow || violationLevelAfter > 100);
          manualOverrideDistance = 0.75;
          break;
        case SILENT:
          setback = false;
          manualOverrideDistance = 1.25;
          break;
      }

      // Apply manual setback override when the deviation is greater than a certain amount of blocks
      if (distance > manualOverrideDistance) {
        setback = true;
      }

      if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
        setback = false;
      }

      if (setback) {
        Simulator simulator = user.meta().movement().simulator();
        simulator.setback(user, predictedX, predictedY, predictedZ);
        movementData.invalidMovement = true;
      }
    }

    statisticApply(user, CheckStatistics::increaseTotal);

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL < 1) {
      decrementer.decrement(user, VL_DECREMENT_PER_VALID_MOVE);
    }

    violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL, 100);

    Pose pose = movementData.pose();
    if (movementData.onLadderLast || pose == Pose.FALL_FLYING || flying) {
      movementData.artificialFallDistance = 0;
    }

    if (movementData.inLava()) {
      movementData.artificialFallDistance *= 0.5;
    }

    GlobalStatisticsRecorder recorder = plugin.analytics().recorderOf(GlobalStatisticsRecorder.class);
//    recorder.recordMovement();
    recorder.recordBlockMoved(Hypot.fast(movementData.motionX(), movementData.motionZ()));

    if (IntaveControl.DEBUG_MOVEMENT) {
      ChatColor chatColor = violationLevelIncrease == 0 ? ChatColor.GRAY : ChatColor.YELLOW;
//      String poseName = movementData.pose().name();
      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 4);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 3);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 3);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 3);

      String violationLevelInfo;
      if (violationLevelIncrease > 0) {
        violationLevelInfo = "g:" + displayPhysicsVL + ",c:" + displayViolationIncrease + "(" + displayHorizontalVL + "," + displayVerticalVL + ")";
      } else {
        violationLevelInfo = "g:" + displayPhysicsVL;
      }
      String debug = String.valueOf(chatColor);
      debug += movementData.simulator().debugName();
      debug += " ";
      if (movementData.recentlyEncounteredFlyingPacket(0)) {
        debug += "f";
      }
      debug += "(" + key + ")";
      debug += " " + violationLevelInfo;
//      debug += " frt" + movementData.fireworkRocketsTicks;
//      debug += " web (a: " + shortenBoolean(movementData.inWeb) + ", r: " + shortenBoolean(collidesWeb(user, currentBoundingBox)) + ")";
//      debug += "cia " + movementData.pastNearbyCollisionInaccuracy;
//      debug += " ai ?" + movementData.aiMoveSpeed();
//      debug += " sprint " + shortenBoolean(movementData.sprinting) + "/" + shortenBoolean(movementData.hasSprintSpeed);
//      debug += " (sneak " + movementData.sneaking + "/"+movementData.actualSneaking()+")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += " hand=" + shortenBoolean(meta.inventory().handActive());
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.pastFlyingPacketAccurate;
//      debug += " gliding:" + shortenBoolean(movementData.elytraFlying);
//      debug += " y:" + formatDouble(movementData.motionY(),4);

      List<String> tags = new ArrayList<>();

      tags.add("dist=" + (movementData.recentlyEncounteredFlyingPacket(1) ? "~" + formatDouble(distance, 8) : formatDouble(distance, 8)));
      if (collidedWithBoat) {
        tags.add("boat");
      }
      if (violationLevelData.isInActiveTeleportBundle) {
        tags.add("atb");
      }
      if (movedIntoBlock) {
        tags.add("bb-intersection");
      }
      if (movementData.physicsJumped) {
        tags.add("jump");
      }
      if (velocityDetected) {
        tags.add("velocity?");
      }
//      tags.add("riding:" + movementData.hasRidingEntity());

      debug += " " + String.join(" ", tags);
      String finalDebug = debug;
      player.sendMessage(finalDebug);
//      Synchronizer.synchronize(() -> player.sendMessage(finalDebug));
    }
  }

  private static boolean collidesWeb(User user, BoundingBox boundingBox) {
    // boundingbox from last tick!
    int blockPositionStartX = floor(boundingBox.minX + 0.001);
    int blockPositionStartY = floor(boundingBox.minY + 0.001);
    int blockPositionStartZ = floor(boundingBox.minZ + 0.001);
    int blockPositionEndX = floor(boundingBox.maxX - 0.001);
    int blockPositionEndY = floor(boundingBox.maxY - 0.001);
    int blockPositionEndZ = floor(boundingBox.maxZ - 0.001);

    for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
      for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
        for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
          Material material = VolatileBlockAccess.typeAccess(user, x, y, z);
          if (material == BlockTypeAccess.WEB) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static String shortenBoolean(boolean bool) {
    return bool ? "1" : "0";
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

  @IdoNotBelongHere
  public void applyFallDamageUpdate(User user) {
    if (!user.hasPlayer()) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    if (movementData.artificialFallDistance > 3.0F) {
      float fallDistance = movementData.artificialFallDistance;
      Synchronizer.synchronize(() -> {
        Player player = user.player();
        movementData.allowFallDamage = true;
        fallDamageApplier.dealFallDamage(player, fallDistance);
        movementData.allowFallDamage = false;
      });
      movementData.artificialFallDistance = 0F;
    }
  }

  private boolean containsBoundingBoxAny(
    List<BoundingBox> intersectionBoundingBoxesCurrent,
    BoundingBox compareBoundingBox
  ) {
    for (BoundingBox boundingBox : intersectionBoundingBoxesCurrent) {
      boolean sameX = boundingBox.minX == compareBoundingBox.minX && boundingBox.maxX == compareBoundingBox.maxX;
      boolean sameY = boundingBox.minY == compareBoundingBox.minY && boundingBox.maxY == compareBoundingBox.maxY;
      boolean sameZ = boundingBox.minZ == compareBoundingBox.minZ && boundingBox.maxZ == compareBoundingBox.maxZ;
      if (sameX && sameY && sameZ) {
        return true;
      }
    }
    return false;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  public SimulationProcessor simulationService() {
    return simulationProcessor;
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public boolean performLinkage() {
    return true;
  }
}