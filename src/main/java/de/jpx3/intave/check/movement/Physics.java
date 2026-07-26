/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.collision.modifier.PowderSnowCollisionModifier;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.evaluation.DefaultSimulationEvaluator;
import de.jpx3.intave.check.movement.physics.evaluation.EvaluationTag;
import de.jpx3.intave.check.movement.physics.evaluation.SimulationEvaluator;
import de.jpx3.intave.check.movement.physics.search.RedoSimulationSearch;
import de.jpx3.intave.check.movement.physics.search.SimulationSearch;
import de.jpx3.intave.check.movement.physics.search.ThreeTickSimulationSearch;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.diagnostic.KeyPressStudy;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.report.PhysicsReport;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.user.storage.ViolationBufferStorage;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.check.movement.physics.search.PostTickMotionType.SENT_OFFSET_MOTION;
import static de.jpx3.intave.diagnostic.message.MessageCategory.SIMFLT;
import static de.jpx3.intave.diagnostic.message.MessageCategory.SIMFUL;
import static de.jpx3.intave.math.MathHelper.*;
import static de.jpx3.intave.share.ClientMath.floor;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL;

public final class Physics extends Check {
  private static final double VL_DECREMENT_PER_VALID_MOVE = 0.08;
  private static final double VELOCITY_VL_THRESHOLD = 6;

  private static final long TOTAL_RESET = 1000 * 60 * 60;
  private static final int AVAILABLE_POINTS = 8;
  private static final long BURST_WINDOW = 8000;
  private static final long BURST_CONGESTION = 2;

	private final CheckViolationLevelDecrementer decrementer;
  private final SimulationSearch simulationSearch;
  private final SimulationEvaluator simulationEvaluator;
  private final boolean highToleranceMode;
  private final boolean resetItemUsage;
  private final boolean closeInventory;
  private final boolean closeInventorySilentMode;
  private final boolean refreshNearbyBlocks;

  public Physics() {
    super("Physics", "physics");
	  this.decrementer = new CheckViolationLevelDecrementer(this, VL_DECREMENT_PER_VALID_MOVE * 20);

    CheckSettings settings = configuration().settings();
    this.highToleranceMode = settings.boolBy("high-tolerance", false);
    if (settings.has("on-detection")) {
      this.resetItemUsage = settings.boolBy("on-detection.reset-item-usage", true);
      String inventoryCloseMode;
      try {
        inventoryCloseMode = settings.stringBy("on-detection.close-inventory", "true");
      } catch (Exception exception) {
        inventoryCloseMode = settings.boolBy("on-detection.close-inventory", true) ? "true" : "false";
      }
      this.closeInventory = inventoryCloseMode.equalsIgnoreCase("true") || inventoryCloseMode.equalsIgnoreCase("silent");
      this.closeInventorySilentMode = inventoryCloseMode.equalsIgnoreCase("silent");
      this.refreshNearbyBlocks = settings.boolBy("on-detection.refresh-nearby-blocks", true);
    } else {
      this.resetItemUsage = settings.boolBy("reset-item-usage", true);
      String inventoryCloseMode;
      try {
        inventoryCloseMode = settings.stringBy("inventory-close-mode", "true");
      } catch (Exception exception) {
        inventoryCloseMode = settings.boolBy("inventory-close-mode", true) ? "true" : "false";
      }
      this.closeInventory = inventoryCloseMode.equalsIgnoreCase("true") || inventoryCloseMode.equalsIgnoreCase("silent");
      this.closeInventorySilentMode = inventoryCloseMode.equalsIgnoreCase("silent");
      this.refreshNearbyBlocks = settings.boolBy("refresh-nearby-blocks-on-detection", true);
    }

    boolean detectNoSlowdown = settings.boolBy("enforce-item-slowdown", true);
    this.simulationEvaluator = new DefaultSimulationEvaluator();

    SimulationSearch search = new ThreeTickSimulationSearch(resetItemUsage, detectNoSlowdown);
    search = RedoSimulationSearch.of(search, simulationEvaluator);
//    search = RollbackSimulationSearch.of(search, simulationEvaluator);
    this.simulationSearch = search;

    setDefaultMitigationStrategy(MitigationStrategy.CAREFUL);
  }

  @DispatchTarget
  public void receiveMovement(User user, boolean withMovement, boolean withRotation) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
	  Simulator simulator = selectSimulator(user);
    movementData.setSimulator(simulator);
    movementData.setStepHeight(simulator.stepHeight());

    /*
     * Run simulatePreTick on last base motion
     */
    SimulationEnvironment firstTickBranch = movementData.mutableView();
    Motion previousBaseMotion = firstTickBranch.mutableBaseMotionCopy();
    Motion preTickMotion = simulator.simulatePreTick(user, previousBaseMotion.copy(), firstTickBranch);
    firstTickBranch.setBaseMotion(preTickMotion);

    /*
     * Run simulatePreTick on all postTickMotionCandidates, discarding the environment changes
     */
    List<Motion> candidates = movementData.postTickMotionCandidates();
    if (!candidates.isEmpty()) {
      // micro optimization
      if (candidates.size() == 1 && candidates.get(0).equals(previousBaseMotion)) {
        movementData.setPostTickMotionCandidates(
          Collections.singletonList(preTickMotion)
        );
      } else {
        List<Motion> newCandidates = new ArrayList<>();
        for (Motion candidate : candidates) {
          newCandidates.add(
            simulator.simulatePreTick(user, candidate.copy(), movementData.mutableView())
          );
        }
        movementData.setPostTickMotionCandidates(newCandidates);
      }
    }

    firstTickBranch.commitTo(movementData);

    movementData.treatThisFlyPacketAsMovePacket = false;

    Timings.CHECK_PHYSICS_PROC.start();
    // simulation
    Simulation simulation;

    SimulationEnvironment simulationEnvironment = movementData.mutableView();

    try {
      simulation = simulationSearch.greedyFuzzyTickSearch(user, simulationEnvironment, simulator);
    } catch (IllegalStateException exception) {
      user.kick("Exception while simulating movement");
      exception.printStackTrace();
      return;
    }

    if (simulation == Simulation.invalid()) {
      user.kick("Invalid simulation result");
      return;
    }

    // since the simulation-search possibly modified its mutableView(),
    // we pull the fresh version from the best simulation and commit it to
    // the active movementData
    simulationEnvironment = simulation.environment();

    KeyPressStudy.enterKeyPressFrom(simulation.configuration());
    boolean reinterpretToMovePacket = !withMovement && simulationEnvironment.tryMoveReinterpretation(
      simulation, user.meta().protocol().flyingPacketUncertaintyRadius()
    );
    if (!withMovement && !reinterpretToMovePacket) {
      Timings.CHECK_PHYSICS_PROC.stop();
      movementData.setBaseMotion(previousBaseMotion);
      updateOnGroundIfFlying(user);
      return;
    }

    // commit happens here!
    simulationEnvironment.commitTo(movementData);
    movementData.assumeOccurred(simulation);
    Timings.CHECK_PHYSICS_EVAL.start();
    // evaluation
    evaluateBestSimulation(user, simulation);
    Timings.CHECK_PHYSICS_EVAL.stop();
    Timings.CHECK_PHYSICS_PROC.stop();
    if (withRotation) {
      if (movementData.rotationYaw != movementData.lastRotationYaw || movementData.rotationPitch != movementData.lastRotationPitch) {
        movementData.lastRotation = System.currentTimeMillis();
      }
    }
    movementData.lastKeyStrafe = simulation.configuration().strafe();
    movementData.lastKeyForward = simulation.configuration().forward();
    if (movementData.ticksPast(RIPTIDE_SPIN) > 40) {
      movementData.highestLocalRiptideLevel = 0;
    }
    movementData.inactiveTick(RIPTIDE_SPIN);
  }

  private Simulator selectSimulator(User user) {
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata protocol = user.meta().protocol();
    boolean clientVehicleMovement = MinecraftVersions.VER1_9_0.atOrAbove() && protocol.combatUpdate();

    if (movementData.isInVehicle() && clientVehicleMovement) {
      Entity entity = movementData.ridingEntity();
      return entity.typeData().isBoat() ? Simulators.BOAT : Simulators.HORSE;
    } else {
      boolean inLava = movementData.inLava();
      boolean inWater = movementData.inWater();
      if (movementData.gliding && !inWater && !inLava) {
        return Simulators.ELYTRA;
      }
    }
    return Simulators.PLAYER;
  }

  @DispatchTarget
  public void endMovement(User user, boolean hasMovement, boolean hasRotation) {
	  MovementMetadata movementData = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();

    Motion usedMotion = movementData.sentOffsetMotion();
//    SimulationResult lastSimulationResult = movementData.simulationResult();
//    if (lastSimulationResult != null && lastSimulationResult.offsetMotionDiffersFromActualMotion()) {
//      usedMotion = lastSimulationResult.actualMotion();
//    }

    usedMotion = usedMotion.copiedOverrideIfNotNaN(
      movementData.endMotionXOverride,
      movementData.endMotionYOverride,
      movementData.endMotionZOverride
    );

    Simulator simulator = movementData.simulator();
    if (hasMovement) {
      if (movementData.ticksPast(VELOCITY) == 0) {
        if (movementData.physicsJumped && movementData.lastVelocityApplicableForJumpDenial()) {
          movementData.physicsJumpedOverrideVL++;
        } else if (movementData.physicsJumpedOverrideVL > 0) {
          movementData.physicsJumpedOverrideVL = Math.max(0, movementData.physicsJumpedOverrideVL - 0.5);
        }
      }

//      Motion afterTickMotion = simulator.simulateAfterTick(
//        user,
//        movementData,
//        MovementConfiguration.blank(),
//        movementData.position(),
//        usedMotion
//      );

      Motion beforeBaseMotion = movementData.mutableBaseMotionCopy();
      Motion afterBaseMotion;

      try {
        List<Motion> candidates = simulationSearch.afterTickMotionCandidates(
          user, movementData, simulator,
          movementData.position(), SENT_OFFSET_MOTION
        );
//        ActionBar.sendActionBar(user.player(), ChatColor.YELLOW + "Candidates: " + candidates);
        movementData.setPostTickMotionCandidates(candidates);
        afterBaseMotion = movementData.mutableBaseMotionCopy();
      } catch (Exception exception) {
        user.kick("Exception while simulating after movement");
        exception.printStackTrace();
        return;
      }

      if (!violationMetadata.isInActiveTeleportBundle) {
        PacketLogging logging = Modules.tracker().packetLogging();
	      logging.logSystemMessage(user, () -> "MOTION LOGIC: Base afterTickMotion override: " + afterBaseMotion.motionX + " " + afterBaseMotion.motionY + " " + afterBaseMotion.motionZ);
//        movementData.setBaseMotion(afterBaseMotion);
      } else {
        movementData.setBaseMotion(beforeBaseMotion);
        movementData.clearPostTickMotionCandidates();
      }
      movementData.inactiveTick(
        FLYING_PACKET_ACCURATE,
        FLYING_PACKET_CLIENT,
        NEARBY_COLLISION_INACCURACY,
        ENTITY_USE,
        ATTACK_REDUCE,
        WATERFLOW_PUSH,
        SLIME_BLOCK
      );
      if (movementData.onGround()) {
        movementData.resetPhysicsPacketRelinkFlyVL();
      }
      Material type = VolatileBlockAccess.typeAccess(user, movementData.position());
      boolean climbingInPowderSnow = type == BlockTypeAccess.POWDER_SNOW && PowderSnowCollisionModifier.canWalkOnPowderSnow(user);
      movementData.tick(IN_POWDER_SNOW, climbingInPowderSnow);
      movementData.inactiveTick(EDGE_SNEAKING_TICK_GRANTS);
    } else if (hasRotation || movementData.treatThisFlyPacketAsMovePacket) {
      Position newPosition = movementData.lastPosition().mutable().add(usedMotion.copy());
//      Motion motion = simulator.simulateAfterTick(
//        user, movementData,
//        MovementConfiguration.blank(),
//        newPosition, usedMotion.copy()
//      );
//      movementData.setBaseMotion(motion);

      try {
        List<Motion> candidates = simulationSearch.afterTickMotionCandidates(
          user, movementData, simulator, newPosition, SENT_OFFSET_MOTION
        );
        movementData.setPostTickMotionCandidates(candidates);
      } catch (Exception exception) {
        user.kick("Exception while simulating after movement");
        exception.printStackTrace();
        return;
      }
    }
    movementData.endMotionXOverride = Double.NaN;
    movementData.endMotionYOverride = Double.NaN;
    movementData.endMotionZOverride = Double.NaN;
  }

  @DispatchTarget
  public void updateOnGroundIfFlying(User user) {
    MovementMetadata movementData = user.meta().movement();
    double physicsMotionX = movementData.baseMotionX;
    double physicsMotionY = movementData.baseMotionY;
    double physicsMotionZ = movementData.baseMotionZ;
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
    SimpleColliderResult colliderResult = Colliders.simplifiedCollision(
      user.player(),
      movementData,
      movementData.verifiedLastPositionX, movementData.verifiedLastPositionY, movementData.verifiedLastPositionZ,
      motionX, motionY, motionZ
    );
    movementData.onGround = colliderResult.onGround();
  }

  /**
   * This method is too big, please refactor
   */
  private void evaluateBestSimulation(User user, Simulation simulation) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    boolean spectator = player.getGameMode() == GameMode.SPECTATOR;

    MovementMetadata movementData = meta.movement();
    InventoryMetadata inventory = meta.inventory();
    ProtocolMetadata protocol = meta.protocol();
    ViolationMetadata violationLevelData = meta.violationLevel();
    AbilityMetadata abilityData = meta.abilities();
    BlockCache blockStateAccess = user.blockCache();
    PhysicsReport physicsReport = null;

    SimulationResult expectedMovement = simulation.result();
    Motion offsetMotion = expectedMovement.offsetMotion();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.probablyFlying() || abilityData.allowFlying();
    StringBuilder key = new StringBuilder(resolveKeysFromInput(keyForward, keyStrafe));

    double receivedOffsetMotionX = movementData.offsetMotionX();
    double receivedOffsetMotionY = movementData.offsetMotionY();
    double receivedOffsetMotionZ = movementData.offsetMotionZ();
    double predictedOffsetX = offsetMotion.motionX();
    double predictedOffsetY = offsetMotion.motionY();
    double predictedOffsetZ = offsetMotion.motionZ();
    double differenceX = predictedOffsetX - receivedOffsetMotionX;
    double differenceY = predictedOffsetY - receivedOffsetMotionY;
    double differenceZ = predictedOffsetZ - receivedOffsetMotionZ;
    double distance = MathHelper.hypot3d(differenceX, differenceY, differenceZ);
    double receivedPositionX = movementData.positionX();
    double receivedPositionY = movementData.positionY();
    double receivedPositionZ = movementData.positionZ();
    double positionX = movementData.verifiedLastPositionX();
    double positionY = movementData.verifiedLastPositionY();
    double positionZ = movementData.verifiedLastPositionZ();
    Motion actualMotion = simulation.actualMotion();

    boolean onLadderCurrent = MovementCharacteristics.onClimbable(user, positionX, positionY, positionZ);
    boolean onLadder = onLadderCurrent || movementData.onLadderLast;
    movementData.onLadderLast = onLadderCurrent;

    // Entity collision check
    boolean collidedWithBoat = movementData.collidedWithBoat();
    boolean skipVLCalculation = distance <= 0.00005;

    Set<EvaluationTag> verticalTags = EnumSet.noneOf(EvaluationTag.class);
    Set<EvaluationTag> horizontalTags = EnumSet.noneOf(EvaluationTag.class);

    double verticalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateVerticalViolationIncrease(
      user, movementData, predictedOffsetY,
      onLadder, collidedWithBoat, verticalTags
    );
    double horizontalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateHorizontalViolationIncrease(
      user, movementData,
      predictedOffsetX, predictedOffsetZ,
      receivedOffsetMotionX, receivedOffsetMotionZ,
      onLadder, collidedWithBoat, horizontalTags
    );

    if (IntaveControl.NO_TOLERANCE_PHYSICS) {
      if (verticalViolationIncrease > 1) {
        verticalViolationIncrease = 10000;
      }
      if (horizontalViolationIncrease > 1) {
        horizontalViolationIncrease = 10000;
      }
    }

    if (onLadder) {
      movementData.artificialFallDistance = 0;
    }

    double biasedDistance = MathHelper.hypot3d(differenceX, differenceY * 2, differenceZ);
    violationLevelData.physicsOffset += biasedDistance;
    violationLevelData.physicsOffset -= movementData.receivedFlyingPacketIn(2) && movementData.sentOffsetMotion().length() < 0.1 ? Math.min(0.03, biasedDistance) : 0;
    violationLevelData.physicsOffset -= violationLevelData.physicsOffset > 0.6 ? 0.002 : 0.001;
    violationLevelData.physicsOffset -= movementData.ticksPast(ELYTRA_FLYING) < 3 ? 0.025 : 0;

    // clamp the offset
    if (violationLevelData.physicsOffset > 1.0) {
      violationLevelData.physicsOffset = 1.0;
    }
    if (violationLevelData.physicsOffset < 0) {
      violationLevelData.physicsOffset = 0;
    }

    boolean velocityDetected = false;
    boolean checkVelocity = !skipVLCalculation
      && movementData.ticksPast(IN_WEB) > 5
      && !movementData.inWater()
      && !movementData.collidedWithBoat();

    if (checkVelocity && !movementData.gliding && movementData.ticksPast(EXTERNAL_VELOCITY) < 10 && !movementData.receivedFlyingPacketIn(2)) {
      boolean actuallyMoved = (Math.abs(predictedOffsetX) > 0.01 || Math.abs(predictedOffsetZ) > 0.01);

      boolean noCollisionOnHighVersion = !(protocol.cavesAndCliffsUpdate()
        && Collision.present(user, movementData, movementData.boundingBox().growHorizontally(0.3)));

      if (distance > 0.005 && horizontalViolationIncrease > 0.001 && !onLadder && noCollisionOnHighVersion) {
        if (actuallyMoved) {
          boolean aggressive = violationLevelData.physicsVelocityVL++ >= VELOCITY_VL_THRESHOLD || movementData.ticksPast(EXTERNAL_VELOCITY) == 0;
          if (aggressive || distance > 0.01) {
            if (aggressive) {
              horizontalViolationIncrease = Math.max(2, horizontalViolationIncrease);
              velocityDetected = true;
            }
            horizontalViolationIncrease *= 20.0;
          }
        } else {
          if (Math.abs(differenceY) < 0.015 && movementData.ticksPast(EXTERNAL_VELOCITY) < 2) {
            verticalViolationIncrease = 0;
          }
        }
      }
    }

//    if (differenceY > 0.01/* && differenceY < 0.03*/ && (movementData.lastOnGround() || movementData.onGround())) {
//      player.sendMessage(differenceY + " " + Math.abs(predictedOffsetX) + "/" + Math.abs(predictedOffsetZ) + " @" +Math.abs(predictedOffsetY - movementData.jumpMotion()) + " " + movementData.receivedFlyingPacketIn(6) + " " + movementData.past(FLYING_PACKET_ACCURATE));
//    }
    boolean flyingJump = false;
    if ((Math.abs(predictedOffsetX) < 0.1 && Math.abs(predictedOffsetZ) < 0.1) && Math.abs(predictedOffsetY - movementData.jumpMotion()) < 0.05 &&
      differenceY > 0.01 && differenceY < 0.03 /* only allow positive differenceY */ && (movementData.lastOnGround() || movementData.onGround()) /*&& movementData.receivedFlyingPacketIn(6)*/) {
//      player.sendMessage(ChatColor.RED + "Flying jump detected, " + movementData.past(FLYING_PACKET_ACCURATE));
      flyingJump = true;
      verticalViolationIncrease = 0;

      movementData.endMotionYOverride = predictedOffsetY;
    }

    boolean expectProblems = movementData.ticksPast(ELYTRA_FLYING) <= 2 || movementData.ticksPast(IN_WATER) <= 2;

    if (distance > 0.01 && !expectProblems && (verticalViolationIncrease > 5 || horizontalViolationIncrease > 5)) {
      if (Math.abs(receivedOffsetMotionX) > 0.15 && differenceX > 0.025) {
        movementData.endMotionXOverride = predictedOffsetX * 0.98;
      }
      if (Math.abs(receivedOffsetMotionY) > 0.1 && differenceY > 0.1) {
        movementData.endMotionYOverride = (predictedOffsetY - 0.08) * 0.98;
      }
      if (Math.abs(receivedOffsetMotionZ) > 0.15 && differenceZ > 0.025) {
        movementData.endMotionZOverride = predictedOffsetZ * 0.98;
      }
    }

    if (movementData.ticksPast(VEHICLE_ATTACHMENT) <= 1) {
      movementData.endMotionXOverride = 0;
      movementData.endMotionYOverride = 0;
      movementData.endMotionZOverride = 0;
    }

    // TODO: 05/28/22 check if this worked, and deal with adjustments
    // trustfactor limit is just temporary
    boolean suspectSafeWalk = user.trustFactor().atOrBelow(TrustFactor.YELLOW);
    if (distance > 0.008 && suspectSafeWalk && movementData.ticksPast(BLOCK_PLACEMENT) <= 8 && horizontalViolationIncrease > 0.1 && !movementData.isSneaking()) {
      boolean smallMovement = (Math.abs(movementData.offsetMotionX()) < 0.08 || Math.abs(movementData.offsetMotionZ()) < 0.08) && movementData.onGround();
      if (smallMovement && !movementData.receivedFlyingPacketIn(3)) {
        horizontalViolationIncrease = Math.max(100, horizontalViolationIncrease * 50);
      }
    }

    if (violationLevelData.physicsInsignificantBufferVL > 0) {
      violationLevelData.physicsInsignificantBufferVL -= 0.0008;
    }

    if (violationLevelData.physicsVelocityVL > 10) {
      violationLevelData.physicsVelocityVL = 10;
    }
    if (violationLevelData.physicsVelocityVL > 0) {
      violationLevelData.physicsVelocityVL -= 0.005;
    }

    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;
    if (movementData.simulator() == Simulators.HORSE) {
      violationLevelIncrease = 0;
    }
    if (distance > 0.001) {
      movementData.suspiciousMovement = true;

      Simulator simulator = selectSimulator(user);
      Motion motion = movementData.mutableBaseMotionCopy();
      MovementConfiguration config = MovementConfiguration.blank();
      if (IntaveControl.SETBACK_WITH_PRESSED_KEYS) {
        config = config.withKeypress(movementData.lastKeyForward, movementData.lastKeyStrafe);
      }
      Simulation otherSimulation = simulator.simulateTick(user, motion, user.meta().movement().immutableView(), config);

      Motion setbackMotion = otherSimulation.offsetMotion();
      /*
       * This will patch the hit-player-sneaking-on-a-block-edge bug (https://youtu.be/ONGnOwhQyac)
       */
      Motion lastVelocity = movementData.sneakPatchVelocity;
      if (movementData.isSneaking() &&
        !movementData.onGround() &&
        lastVelocity != null
      ) {
//        predictedOffsetX = Math.abs(setbackMotion.motionX) < 0.05 ? setbackMotion.motionX + MathHelper.minmax(-0.05, lastVelocity.motionX, 0.05) : setbackMotion.motionX;
//        predictedOffsetY = setbackMotion.motionY;
//        predictedOffsetZ = Math.abs(setbackMotion.motionZ) < 0.05 ? setbackMotion.motionZ + MathHelper.minmax(-0.05, lastVelocity.motionZ, 0.05) : setbackMotion.motionZ;
//        movementData.sneakPatchVelocity = null;
      } else {
        predictedOffsetX = setbackMotion.motionX;
        predictedOffsetY = setbackMotion.motionY;
        predictedOffsetZ = setbackMotion.motionZ;
      }
    }

    if (flying || spectator) {
      violationLevelIncrease = 0;
    }

    if (violationLevelData.physicsInsignificantBufferVL < 3 &&
      violationLevelData.physicsVL + violationLevelIncrease > 50 &&
      violationLevelIncrease > 0 && !movementData.inWeb() && !movementData.inWater() &&
      distance > 0.001
    ) {
      boolean predictedNoHorizontalMovement = Math.abs(predictedOffsetX) < 0.05 && Math.abs(predictedOffsetZ) < 0.05;
      boolean horizontalFasterThanExpected = Math.abs(predictedOffsetX) < Math.abs(receivedOffsetMotionX) - 0.05 || Math.abs(predictedOffsetZ) < Math.abs(receivedOffsetMotionZ) - 0.05;

      double gainMultiplier = 1;
      if (predictedNoHorizontalMovement) {
        gainMultiplier *= 1.5;
      }
      if (horizontalFasterThanExpected) {
        gainMultiplier *= 0.5;
      }

      if (Math.abs(differenceY) < 0.1 && receivedOffsetMotionY < predictedOffsetY + 0.01 &&
        Math.abs(differenceX) < 0.15 * gainMultiplier && Math.abs(differenceZ) < 0.15 * gainMultiplier &&
        Math.abs(differenceX) + Math.abs(differenceZ) < 0.2 * gainMultiplier &&
        distance < 0.25
      ) {
        violationLevelData.physicsInsignificantBufferVL += (distance < 0.05 ? 0.5 : 1);
        violationLevelIncrease = 0;
      }
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.990;
      violationLevelData.physicsVL -= 0.012;
    }

    Location verifiedLocation = movementData.verifiedLocation();
    BoundingBox verifiedBoundingBox = BoundingBox.fromPosition(user, movementData, verifiedLocation);
    BoundingBox currentBoundingBox = BoundingBox.fromPosition(user, movementData, receivedPositionX, receivedPositionY, receivedPositionZ);

    boolean boundingBoxIntersectionLast = Collision.present(user, movementData, verifiedBoundingBox);
    boolean boundingBoxIntersectionCurrent = Collision.present(user, movementData, currentBoundingBox);
    boolean movedIntoBlock = !boundingBoxIntersectionLast && boundingBoxIntersectionCurrent;
    if (boundingBoxIntersectionCurrent && !spectator) {
      List<BoundingBox> intersectionBoundingBoxesCurrent = Collision.__INVALID__resolveBoxes__OnlyForBoxIntersectionChecks__(player, currentBoundingBox);
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
        if (!Collision.blockInsideBorder(movementData, blockPositionX, blockPositionZ)) {
          colliderName = "world border";
        } else {
          String prefix = (currentlyInOverride ? "emulated " : "") + (altered ? "altered " : "");
          Material type = VolatileBlockAccess.typeAccess(user, block.getLocation());
          String typeName = shortenTypeName(type);
          colliderName = prefix + typeName + " block";
        }
        String message = "moved into " + colliderName.trim();
        boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
        String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");
        if (!IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT) {
          physicsReport = new PhysicsReport(user);
          blockStateAccess.invalidateAll();
        }
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(player).withMessage(message).withDetails(details).withVL(0).build();
        Modules.violationProcessor().processViolation(violation);
        Motion emulationMotion = new Motion(predictedOffsetX, predictedOffsetY, predictedOffsetZ);
        Modules.mitigate().movement().emulationSetBack(player, emulationMotion, 2, true);
      }
    }

    if (!boundingBoxIntersectionCurrent && !boundingBoxIntersectionLast) {
      movementData.currentlyInBlock = false;
    }

    // Update the player's verified location
    if (spectator || violationLevelIncrease == 0 && !boundingBoxIntersectionCurrent) {
      Location location = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
      movementData.setVerifiedLocation(location);
    }

    double latantDistance = 0.7;
    if (violationLevelIncrease > 0) {
      boolean uncommonArea = //movementData.past(WATER_MOVEMENT) < 20
        movementData.collidedHorizontally
        || movementData.collidedWithBoat()
        || movementData.inWeb
        || movementData.ticksPast(ELYTRA_FLYING) < 20;
      if (uncommonArea) {
        violationLevelIncrease /= 2;
      } else if (protocol.aquaticUpdate()) {
        violationLevelIncrease /= 2;
      }
      violationLevelIncrease = Math.min(200.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL + violationLevelIncrease, 200);
      violationLevelData.physicsInvalidMovementsInRow += (distance < 0.01 ? 0.25 : (distance < 0.05 ? 0.5 : 1));
      if (
        violationLevelData.physicsOffset > latantDistance
          && distance > 0.001
          && !spectator
          && violationLevelData.physicsVL > 50
      ) {
        if (physicsReport == null) {
          physicsReport = new PhysicsReport(user);
        }
      }
      if (violationLevelData.physicsVL > 20) {
        if (!IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT) {
          blockStateAccess.invalidateAll();
        }
      }
    } else {
      if (violationLevelData.physicsInvalidMovementsInRow >= 0) {
        violationLevelData.physicsInvalidMovementsInRow *= 0.95;
        violationLevelData.physicsInvalidMovementsInRow -= movementData.sentOffsetMotion().horizontalLength() > 0.1 ? .15 : .05;
      }
      statisticApply(user, CheckStatistics::increasePasses);
    }

    boolean setback = false;
    boolean offsetRequirement = violationLevelData.physicsOffset > latantDistance && distance > 0.001;

    PacketLogging logging = Modules.tracker().packetLogging();
    double finalVerticalViolationIncrease = verticalViolationIncrease;
    double finalHorizontalViolationIncrease = horizontalViolationIncrease;
    logging.logSystemMessage(user, () -> "MOVEMENT PROCESS: " + receivedOffsetMotionX + " " + receivedOffsetMotionY + " " + receivedOffsetMotionZ + " vl" + violationLevelData.physicsVL + " acc/off" +  violationLevelData.physicsOffset + " d" + distance + " h/v:" + finalHorizontalViolationIncrease +"/" + finalVerticalViolationIncrease + " spec" + spectator + " fly" + flying  + " " + verticalTags + " " + horizontalTags);

    // santiy checks
    performMovementSanityChecks(user, receivedOffsetMotionX, receivedOffsetMotionY, receivedOffsetMotionZ);

    if (offsetRequirement && !spectator && violationLevelData.physicsVL > 50 && violationLevelIncrease > 0) {
      String received = formatPosition(receivedOffsetMotionX, receivedOffsetMotionY, receivedOffsetMotionZ);
      String expected = formatPosition(predictedOffsetX, predictedOffsetY, predictedOffsetZ);
      String actual = formatPosition(actualMotion.motionX, actualMotion.motionY, actualMotion.motionZ);

//      PhysicsReport report = physicsReport == null ? new PhysicsReport(user) : physicsReport;
//      try {
//        ReportFileWriter.writeNew(
//          IntavePlugin.singletonInstance().getDataFolder().toPath().resolve("physicsreports"),
//          "intave-physicsreport",
//          report
//        );
//      } catch (IOException | RuntimeException exception) {
//        IntaveLogger.logger().error(
//          "Unable to write physics report: "
//            + exception.getClass().getSimpleName()
//            + ": "
//            + exception.getMessage()
//        );
//      }

	    String message = "moved incorrectly";
//      String details = received + " actual: " + expected;
      String details = "";

      details += formatDouble(distance, 6) + " / ";

      String vlInfo = "";
      if (verticalViolationIncrease > 500) {
        vlInfo += "mv";
      } else if (verticalViolationIncrease < 0.1) {
        vlInfo += "0v";
      } else {
        vlInfo += formatDouble(verticalViolationIncrease, 1) + "v";
      }
      if (horizontalViolationIncrease > 500) {
        vlInfo += "mh";
      } else if (horizontalViolationIncrease < 0.1) {
        vlInfo += "0h";
      } else {
        vlInfo += formatDouble(horizontalViolationIncrease, 1) + "h";
      }
      details += vlInfo + " / ";

      details += simulation.blueDetails().replace('/', '_');
//      details += formatDouble(-differenceX, 4) + "dx " + formatDouble(-differenceY, 4) + "dy " + formatDouble(-differenceZ, 4) + "dz";

      if (velocityDetected) {
        details += ", strict";
      }

      if (movementData.forceCorrectReduce) {
        details += velocityDetected ? "&" : ",";
        details += " reduce force";
        user.nerf(AttackNerfStrategy.BLOCKING, "46");
      }

      Map<String, String> granularDebugs = new LinkedHashMap<>();
      granularDebugs.put("received", received);
      granularDebugs.put("expected", expected);
      granularDebugs.put("actual", actual);
      granularDebugs.put("distance", formatDouble(distance, 3));
      granularDebugs.put("pose", movementData.pose().name());
      if (movementData.isInVehicle()) {
        granularDebugs.put("vehicle", movementData.isInVehicle() ? (movementData.isInRidingVehicle() ? "riding" : "passive") : "none");
      }
      granularDebugs.put("insig", formatDouble(violationLevelData.physicsInsignificantBufferVL, 1));
      granularDebugs.put("acc/off", formatDouble(violationLevelData.physicsOffset, 2));
      granularDebugs.put("s/c v", MinecraftVersion.current().getVersion() + " / " + user.protocolVersion());
      BlockShape collShape = Collision.shape(user, movementData, currentBoundingBox);
      granularDebugs.put("coll", collShape.toString());
      granularDebugs.put("coll_out", collShape.outline().toCompactString());
      granularDebugs.put("simtgx", simulation.result().tagString());
      granularDebugs.put("config", simulation.configuration().toCompactString());
      granularDebugs.put("v/tags", evalTagsToString(verticalTags));
      granularDebugs.put("h/tags", evalTagsToString(horizontalTags));

      double vl = violationLevelIncrease / (violationLevelData.physicsVL >= 100 && !highToleranceMode() ? 20 : 50);
      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(player)
        .withMessage(message)
        .withDetails(details)
        .withGranulars(granularDebugs)
        .withVL(vl)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);

      if (violationContext.shouldCounterThreat()) {
        // testing
        String I_EXIST_FOR_THE_BREAKPOINT = "I_EXIST_FOR_THE_BREAKPOINT";
        int val = I_EXIST_FOR_THE_BREAKPOINT.length();
      }

      // a few helpful states
      boolean isMidAir = !movementData.onGround && !movementData.collidedHorizontally && !movementData.collidedVertically;
      boolean isOnGround = movementData.onGround;
      double distanceMoved = MathHelper.hypot3d(movementData.offsetMotionX(), movementData.offsetMotionY(), movementData.offsetMotionZ());

      boolean deepPitchViolationOverflow = violationContext.shouldCounterThreat();
      int highPitchLimit = trustFactorSetting("pa-override-threshold", player);
      boolean highPitchViolationOverflow = violationLevelData.physicsVL > highPitchLimit;
      boolean highPitchAggressiveViolationOverflow = violationLevelData.physicsVL >= Math.max(highPitchLimit, 150);

      double violationLevelBefore = violationContext.violationLevelBefore();
      double violationLevelAfter = violationContext.violationLevelAfter();

      boolean freeOfColliders = !Collision.nearSolidBlock(user, currentBoundingBox.grow(1));

      MitigationStrategy mitigationStrategy = mitigationStrategy();

      double manualOverrideDistance = 0;
      switch (mitigationStrategy) {
        case AGGRESSIVE:
          setback = deepPitchViolationOverflow || (!highToleranceMode() && highPitchViolationOverflow);
          manualOverrideDistance = 0.75;
          break;
        case CAREFUL:
          setback = deepPitchViolationOverflow || (highPitchViolationOverflow && (violationLevelAfter > 20 || highPitchAggressiveViolationOverflow || user.justJoined()));
          if (receivedOffsetMotionY > Math.max(0.42f, movementData.jumpMotion()) + 0.01) {
            setback = true;
          }
          manualOverrideDistance = 0.75;
          break;
        case LENIENT:
          setback = deepPitchViolationOverflow || (highPitchViolationOverflow && (freeOfColliders || violationLevelIncrease > 50) && (violationLevelAfter > 30 || highPitchAggressiveViolationOverflow || user.justJoined()));
          if (receivedOffsetMotionY > Math.max(0.42f, movementData.jumpMotion()) + 0.01) {
            setback = true;
          }
          manualOverrideDistance = 0.75;
          break;
        case BARELY:
          boolean flagAnywayss = freeOfColliders && ((isMidAir && violationLevelAfter > 60) || (verticalViolationIncrease >= 100 && predictedOffsetY < 0 && violationLevelAfter >= 100));
          boolean velocityFlag = velocityDetected && violationLevelAfter > 30 && (verticalViolationIncrease >= 100 || horizontalViolationIncrease >= 100);
          setback =
            (distanceMoved > (violationLevelAfter > 80 ? 0.5 : 0.7) || violationLevelAfter > 200 || flagAnywayss || velocityFlag)
              && deepPitchViolationOverflow && (highPitchAggressiveViolationOverflow || violationLevelAfter > 200 || user.justJoined());
          manualOverrideDistance = 1;
          break;
        case SILENT:
          setback = false;
          manualOverrideDistance = 1.5;
          if (violationLevelAfter > 20 && closeInventorySilentMode && user.meta().inventory().inventoryOpen()) {
            player.closeInventory();
          }
          break;
      }

      if (distance > 5) {
        violationLevelData.lastMovementDebugRequest = System.currentTimeMillis();
      }

      // reduce setbacks
      if (
        setback && !velocityDetected &&
          Math.abs(predictedOffsetX - receivedOffsetMotionX) < 0.25 &&
          Math.abs(predictedOffsetY - receivedOffsetMotionY) < 0.25 &&
          Math.abs(predictedOffsetZ - receivedOffsetMotionZ) < 0.25 &&
          distance < 0.4 &&
          movementData.ticksPast(BLOCK_PLACEMENT) >= 8 &&
          user.trustFactor().atLeast(TrustFactor.ORANGE) &&
          violationLevelAfter < 100
      ) {
        ViolationBufferStorage buffer = user.storageOf(ViolationBufferStorage.class);
        // check for reset
        buffer.checkReset(name(), AVAILABLE_POINTS, TOTAL_RESET);
        if (buffer.trySpendPoint(name(), BURST_WINDOW, BURST_CONGESTION)) {
          setback = false;
//          Synchronizer.synchronize(() -> {
//            player.sendMessage(ChatColor.YELLOW + "Spent point");
//          });
        }
      }

//      if (movementData.allowRespawnLeniency) {
//        double horizontalDistance = MathHelper.resolveHorizontalDistance(receivedPositionX, receivedPositionZ, movementData.lastRespawnX, movementData.lastRespawnZ);
//        boolean notTooFarAway = horizontalDistance < 2;
//        boolean notTooFastHorizontally = Math.abs(movementData.motionX()) < 0.4 && Math.abs(movementData.motionZ()) < 0.4;
//        boolean falling = movementData.motionY() <= 0.01;
//        if (notTooFarAway && notTooFastHorizontally && falling) {
//          setback = false;
//        }
//      }

      // Apply manual setback override when the deviation is greater than a certain amount of blocks
      if (distance > manualOverrideDistance) {
        setback = true;
      }

      if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
        setback = false;
      }

      if (setback) {
        // resend attributes
        statisticApply(user, CheckStatistics::increaseFails);

        MovementMetadata movement = user.meta().movement();
        Simulator simulator = movement.simulator();
        simulator.setback(user, movement, predictedOffsetX, predictedOffsetY, predictedOffsetZ);
        refreshNearbyBlocks(user, positionX, positionY, positionZ);
        movementData.invalidMovement = true;
      }
    }

    if (setback && !protocol.combatUpdate() && simulation.wasSprinting()
      && System.currentTimeMillis() - movementData.lastSimulationSprintResetAttempt > 10_000
    ) {
      movementData.lastSimulationSprintResetAttempt = System.currentTimeMillis();
      user.refreshSprintState();
    }

    statisticApply(user, CheckStatistics::increaseTotal);

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL < 1) {
      decrementer.decrement(user, VL_DECREMENT_PER_VALID_MOVE);
    }

    violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL, 150);

    Pose pose = movementData.pose();
    if (movementData.onLadderLast || pose == Pose.FALL_FLYING || flying) {
      movementData.artificialFallDistance = 0;
    }

    if (movementData.inLava()) {
      movementData.artificialFallDistance *= 0.5F;
    }

//    recorder.recordMovement();
//    recorder.recordBlockMoved(Hypot.fast(movementData.motionX(), movementData.motionZ()));

    boolean faultDebugRequested = DebugBroadcast.anyoneListeningTo(SIMFLT, player);
    boolean fullDebugRequested = DebugBroadcast.anyoneListeningTo(SIMFUL, player);
    boolean anyDebugRequested = !IntaveControl.DEBUG_MOVEMENT && (faultDebugRequested || fullDebugRequested);

    if (IntaveControl.DEBUG_MOVEMENT || anyDebugRequested || user.receives(MessageChannel.DEBUG_MOVEMENT) ) {
      ChatColor chatColor = ChatColor.GRAY;
      String symbol = "";

      if (setback) {
        chatColor = ChatColor.DARK_RED;
        symbol = "!! ";
      } else if (violationLevelIncrease > 0) {
        chatColor = ChatColor.RED;
        symbol = "! ";
      } /*else if (violationLevelData.physicsVL > 10) {
        chatColor = ChatColor.YELLOW;
        symbol = "? ";
      }*/

      String debug = chatColor + symbol;

      boolean fly = movementData.receivedFlyingPacketIn(0);
      while (key.length() < 2) {
        key.append(" ");
      }
      if (fly && distance >= 0.001) {
        debug += ChatColor.STRIKETHROUGH;
      }
      boolean moved = movementData.lastPosition().distance(movementData.position()) > 0.001;
      boolean rotated = movementData.lastRotation().distanceTo(movementData.rotation()) > 0.001;
//      debug += (moved ? "M" : "-") + (rotated ? "R" : "-") + " | ";


      String distanceOutput = formatDouble(distance, /*distance < 0.1 && violationLevelIncrease > 0 ? 9 : 3*/6);
      if (movementData.receivedFlyingPacketIn(1)) {
        distanceOutput = distanceOutput.substring(0, distanceOutput.length() - 1) + ChatColor.ITALIC + "f" + chatColor;
      } else if (distance >= 0.01 && violationLevelIncrease == 0) {
        distanceOutput = ChatColor.STRIKETHROUGH + distanceOutput + chatColor;
      }
      debug += distanceOutput + " ";
      debug += /*"(" +*/ key /*+ ")"*/;
      if (fly) {
        debug += chatColor;
      }
      if (pose != Pose.STANDING || movementData.sprinting) {
        String poseName = "";
        switch (pose) {
          case SLEEPING:
            poseName = "L";
            break;
          case FALL_FLYING:
            poseName = "E";
            break;
          case SWIMMING:
            poseName = "U";
            break;
          case CROUCHING:
            poseName = "C";
            if (movementData.sprinting) {
              poseName += "R";
            }
            break;
          case STANDING:
            poseName = "R";

            break;
        }
        debug += ChatColor.BOLD + poseName + chatColor;
      }

      debug += " y:" + formatDouble(movementData.offsetMotionY(), 4) + "@" + decimalPlacesOf(receivedPositionY, 4);

      if (verticalViolationIncrease > 0) {
        debug += "r" +formatDouble(simulation.offsetMotion().motionY, 4);
      }

//      debug += " x:" + formatDouble(movementData.motionX(), 4) + " z:" + formatDouble(movementData.motionZ(), 4);
      if (!simulation.blueDetails().isEmpty()) {
        debug += ChatColor.AQUA + " " + ChatColor.ITALIC + " " + simulation.blueDetails() + chatColor;
      }
      if (!simulation.purpleDetails().isEmpty()) {
        debug += ChatColor.LIGHT_PURPLE + " " + ChatColor.ITALIC + " " + simulation.purpleDetails() + chatColor;
      }

      if (simulation.resultsInFlyingPacket(movementData, protocol.flyingPacketUncertaintyRadius())) {
        debug += " nwbf";
      }

      sendPacketWithExperience(player, movementData.simulationRateLimiter.counter());

      if (movementData.ticksPast(FIREWORK_ROCKETS) < 100) {
        debug += ChatColor.ITALIC + " frt:" + movementData.ticksPast(FIREWORK_ROCKETS) + " frp: " + movementData.fireworkRocketsPower + chatColor;
      }
      if (movementData.shulkerXToleranceRemaining + movementData.shulkerYToleranceRemaining + movementData.shulkerZToleranceRemaining > 0) {
        debug += ChatColor.ITALIC + " slk:" + movementData.shulkerXToleranceRemaining + "," + movementData.shulkerYToleranceRemaining + "," + movementData.shulkerZToleranceRemaining + chatColor;
      }
//      debug += " web (a: " + shortenBoolean(movementData.inWeb) + ", r: " + shortenBoolean(collidesWeb(user, currentBoundingBox)) + ")";
//      if (movementData.past(NEARBY_COLLISION_INACCURACY) < 3) {
//        debug += ChatColor.ITALIC + " pci:" + movementData.past(NEARBY_COLLISION_INACCURACY) + chatColor;
//      }
      if (movementData.ticksPast(EDGE_SNEAKING) < 4) {
        debug += ChatColor.ITALIC + " esk:" + movementData.ticksPast(EDGE_SNEAKING) + chatColor;
      }
      if (movementData.ticksPast(RIPTIDE_SPIN) < 4) {
        debug += ChatColor.ITALIC + " rt:" + movementData.ticksPast(RIPTIDE_SPIN) + "@" + movementData.highestLocalRiptideLevel + chatColor;
      }
      if (simulation.offsetMotionDiffersFromActualMotionInXZ()) {
        debug += ChatColor.ITALIC + " om.xz!=am.xz" + chatColor;
      }
      if (inventory.handActive()) {
        debug += ChatColor.ITALIC + " hnd:" + inventory.handActiveTicks + chatColor;
      }
      if (movementData.isSleeping()) {
        debug += ChatColor.ITALIC + " slp" + chatColor;
      }
      if (velocityDetected) {
        // velocity low tolerance
        debug += ChatColor.ITALIC + " vlt:" + movementData.ticksPast(EXTERNAL_VELOCITY) + chatColor;
      }
      if (movementData.artificialFallDistance > 2) {
        debug += ChatColor.ITALIC + " fd:" + formatDouble(movementData.artificialFallDistance, 2) + chatColor;
      }
      if (flyingJump) {
        debug += ChatColor.ITALIC + " fjp" + chatColor;
      }
      if (!Double.isNaN(movementData.endMotionXOverride)) {
        debug += ChatColor.ITALIC + " emx:" + MathHelper.formatDouble(movementData.endMotionXOverride, 4) + chatColor;
      }
      if (!Double.isNaN(movementData.endMotionYOverride)) {
        debug += ChatColor.ITALIC + " emy:" + MathHelper.formatDouble(movementData.endMotionYOverride, 4) + chatColor;
      }
      if (!Double.isNaN(movementData.endMotionZOverride)) {
        debug += ChatColor.ITALIC + " emz:" + MathHelper.formatDouble(movementData.endMotionZOverride, 4) + chatColor;
      }
      if (movementData.step) {
        debug += ChatColor.ITALIC + " stp:" + formatDouble(movementData.stepHeightThisMove, 5) + chatColor;
      }
      if (movementData.inWeb) {
        debug += ChatColor.ITALIC + " web" + chatColor;
      }
      if (movementData.ticksPast(ENTITY_USE) < 5) {
        debug += ChatColor.ITALIC + " eu" + movementData.ticksPast(ENTITY_USE) + chatColor;
      }
      if (movementData.inWater()) {
        Fluid fluid = Fluids.fluidAt(user, positionX, positionY, positionZ);
        debug += ChatColor.ITALIC + " "+(fluid.falling() ? "falling" : "")+"water@" + MathHelper.formatDouble(fluid.height(),2) + "/"+fluid.level() + chatColor;
      }
      if (movementData.ticksPast(FLYING_PACKET_ACCURATE) < 5) {
        debug += ChatColor.ITALIC + " fpa:" + movementData.ticksPast(FLYING_PACKET_ACCURATE) + chatColor;
      }
      if (movementData.physicsJumped) {
        debug += ChatColor.ITALIC + " jmp" + chatColor;
      }
      if (violationLevelData.physicsInvalidMovementsInRow > 0.1) {
        debug += ChatColor.ITALIC + " ivm:" + formatDouble(violationLevelData.physicsInvalidMovementsInRow, 2) + chatColor;
      }

//      if (movementData.friction() < 0.08) {
//        debug += ChatColor.ITALIC +  " fric:" + formatDouble(movementData.friction(), 2) + "@" + movementData.frictionMaterial() + chatColor;
//      }

      if (violationLevelData.physicsOffset > 0.5) {
        debug += " off:" + ChatColor.YELLOW + formatDouble(violationLevelData.physicsOffset, 2) + chatColor;
      } else if (violationLevelData.physicsOffset > 0.1) {
        debug += " off:" + formatDouble(violationLevelData.physicsOffset, 2);
      }

      // display tags
      if (!verticalTags.isEmpty()) {
        debug += "; V" + evalTagsToString(verticalTags);
      }
      if (!horizontalTags.isEmpty()) {
        debug += "; H" + evalTagsToString(horizontalTags);
      }

//      if (Math.abs(movementData.motionY()) > 0.01) {
//        debug += simulation.configuration() + " ";
//      }

//      debug += " spr:" + (simulation.wasSprinting() ? 1 : 0);

//      debug += " ai ?" + movementData.aiMoveSpeed();
//      debug += " sprint " + (movementData.sprinting) + "/" + (movementData.hasSprintSpeed);
//      debug += " (sneak " + movementData.sneaking + "/"+movementData.actualSneaking()+")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += " hand=" + (meta.inventory().handActive());
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.past(FLYING_PACKET_ACCURATE);
//      debug += " gliding:" + shortenBoolean(movementData.elytraFlying);

//        if (violationLevelIncrease > 0) {
//          debug += " yexp:" + formatDouble(predictedOffsetY, 4) + "@" + decimalPlacesOf(movementData.verifiedPositionY(), 4);
//        }

      Map<String, Double> serverDebugData = simulation.result().debugData();
      Map<String, Double> clientDebugData = movementData.clientMovementDebugValues;
      if (!serverDebugData.isEmpty()) {
        debug += ChatColor.ITALIC + " " + serverDebugData.entrySet().stream().map(entry -> {
          String key1 = entry.getKey();
          double value = entry.getValue();
          return "S"+key1 + ":" + formatDouble(value, 4);
        }).collect(Collectors.joining(" ")) + chatColor;
      }
      if (!clientDebugData.isEmpty()) {
        debug += ChatColor.ITALIC + " " + clientDebugData.entrySet().stream().map(entry -> {
          String key1 = entry.getKey();
          double value = entry.getValue();
          return "C"+key1 + ":" + formatDouble(value, 4);
        }).collect(Collectors.joining(" ")) + chatColor;
      }

//      if (violationLevelIncrease > 0) {
//        player.sendMessage("Expected " + formatPosition(predictedOffsetX, predictedOffsetY, predictedOffsetZ) + " received " + formatPosition(receivedOffsetMotionX, receivedOffsetMotionY, receivedOffsetMotionZ));
//      }

//      List<String> tags = new ArrayList<>();
//      tags.add("d:" + (movementData.recentlyEncounteredFlyingPacket(1) ? "~" + formatDouble(distance, 6) : formatDouble(distance, 6)));
//      if (collidedWithBoat) {
//        tags.add("boat");
//      }
//      if (violationLevelData.isInActiveTeleportBundle) {
//        tags.add("atb");
//      }
//      if (movedIntoBlock) {
//        tags.add("bb-intersection");
//      }
//      if (movementData.physicsJumped) {
//        tags.add("jump");
//      }
//      if (velocityDetected) {
//        tags.add("velocity?");
//      }
//      tags.add("riding:" + movementData.hasRidingEntity());
//      debug += " " + String.join(" ", tags);

      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 1);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 1);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 1);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 1);

      if (violationLevelIncrease > 0) {
        debug += " g:" + displayPhysicsVL + "+" + displayViolationIncrease + "(H" + displayHorizontalVL + "V" + displayVerticalVL + ")";
      } else if (violationLevelData.physicsVL > 25) {
        debug += " g:" + ChatColor.YELLOW + displayPhysicsVL + chatColor;
      } else if (violationLevelData.physicsVL > 5) {
        debug += " g:" + displayPhysicsVL;
      }

      // horizontal and vertical distance difference
//      debug += " h:" + formatDouble(Math.abs(differenceX) + Math.abs(differenceZ), 3);
//      debug += " v:" + formatDouble(Math.abs(differenceY), 3);


      if (debug.startsWith(" ")) {
        debug = debug.substring(1);
      }
      String finalDebug = debug;
      if (!anyDebugRequested) {
        String finalFinalDebug = finalDebug;
        Synchronizer.synchronize(() -> player.sendMessage(finalFinalDebug));
      } else {
        finalDebug = ChatColor.stripColor(finalDebug);
        if (faultDebugRequested && violationLevelIncrease > 0) {
          DebugBroadcast.broadcast(player, SIMFLT, MessageSeverity.MEDIUM, finalDebug, finalDebug);
        } else if (fullDebugRequested) {
          DebugBroadcast.broadcast(player, SIMFUL, MessageSeverity.LOW, finalDebug, finalDebug);
        }
      }
//      Synchronizer.synchronize(() -> player.sendMessage(finalDebug));
    }

    if (user.receives(MessageChannel.DEBUG_MOTION)) {
      ChatColor chatColor = ChatColor.GRAY;
      String symbol = "";

      double distance1 = movementData.sentOffsetMotion().distance(simulation.offsetMotion());
      if (setback) {
        chatColor = ChatColor.DARK_RED;
        symbol = "!! ";
      } else if (violationLevelIncrease > 0) {
        chatColor = ChatColor.RED;
        symbol = "! ";
      } else if (distance1 > 0.1) {
	      chatColor = ChatColor.YELLOW;
	      symbol = "? ";
      }

      String string = chatColor + symbol + " rom:" + movementData.sentOffsetMotion().shortString()
        + " som:" + simulation.offsetMotion().shortString()
        + " sam:" + simulation.actualMotion().shortString();
//      List<Motion> candidates = movementData.postTickMotionCandidates();
//      if (!candidates.isEmpty() && candidates.size() > 1) {
//        string += " cands:" + candidates.stream().map(Motion::toString).collect(Collectors.joining(","));
//      }
      string += " " + formatDouble(distance1, 6);
      user.sendMessage(string);
    }
  }

  private String evalTagsToString(Set<EvaluationTag> horizontalTags) {
    StringJoiner result = new StringJoiner(",");
    Set<String> uniqueValues1 = new HashSet<>();
    for (EvaluationTag horizontalTag : horizontalTags) {
      String string = horizontalTag.toString();
      String upperCase = string.toUpperCase();
      if (uniqueValues1.add(upperCase)) {
        result.add(upperCase);
      }
    }
    return result.toString();
  }

  private void sendPacketWithExperience(Player player, int level) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.EXPERIENCE);
    packet.getFloat().write(0, 0f);
    packet.getIntegers().write(0, 0);
    packet.getIntegers().write(1, level);
    PacketSender.sendServerPacket(player, packet);
  }

  private void refreshNearbyBlocks(User user, double x, double y, double z) {
    if (!refreshNearbyBlocksOnDetection()) {
      return;
    }
    BoundingBox box = BoundingBox.fromPosition(user, user.meta().movement(), x, y, z).grow(1.2);
    Player player = user.player();
    List<Position> positions = Collision.collectCollidingPositions(player, box, 16, Collectors.toList());
    Synchronizer.synchronize(() -> {
      for (Position position : positions) {
        refreshBlock(player, position.toLocation(player.getWorld()));
      }
    });
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.blockAccess(location);
    Object handle = BlockVariantNativeAccess.nativeVariantAccess(block);
    WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
    com.comphenix.protocol.wrappers.BlockPosition position = new com.comphenix.protocol.wrappers.BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    PacketSender.sendServerPacket(player, packet);
  }

  private static String resolveKeysFromInput(int forward, int strafe) {
    String key = "";
    if (forward == 1) {
      key += "W";
    } else if (forward == -1) {
      key += "S";
    } else {
      key += " ";
    }
    if (strafe == 1) {
      key += "A";
    } else if (strafe == -1) {
      key += "D";
    } else {
      key += " ";
    }
    return key;
  }

  @BukkitEventSubscription
  public void onEnderpearlTeleport(PlayerTeleportEvent teleport) {
    Player player = teleport.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    if (teleport.getCause() == ENDER_PEARL) {
      Synchronizer.synchronize(() -> {
        movementData.dealCustomFallDamage = true;
//        fallDamageApplier.dealFallDamage(player, 8);
        movementData.dealCustomFallDamage = false;
      });
    }
  }

  private void performMovementSanityChecks(User user, double receivedMotionX, double receivedMotionY, double receivedMotionZ) {
	  ViolationMetadata violationMetadata = user.meta().violationLevel();

    if ((Double.isNaN(violationMetadata.physicsOffset) || Double.isInfinite(violationMetadata.physicsOffset)) && FaultKicks.POSITION_FAULTS) {
      user.kick("Intolerable position fault (sanity check #3)");
    }

    if ((Double.isNaN(violationMetadata.physicsVL) || Double.isInfinite(violationMetadata.physicsVL)) && FaultKicks.POSITION_FAULTS) {
      user.kick("Intolerable position fault (sanity check #4)");
    }

    // check received motion NaN/Infinite
    if ((Double.isNaN(receivedMotionX) || Double.isInfinite(receivedMotionX)) && FaultKicks.POSITION_FAULTS) {
      user.kick("Intolerable position fault (sanity check #5)");
    }

    if ((Double.isNaN(receivedMotionY) || Double.isInfinite(receivedMotionY)) && FaultKicks.POSITION_FAULTS) {
      user.kick("Intolerable position fault (sanity check #6)");
    }

    if ((Double.isNaN(receivedMotionZ) || Double.isInfinite(receivedMotionZ)) && FaultKicks.POSITION_FAULTS) {
      user.kick("Intolerable position fault (sanity check #7)");
    }
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  public boolean highToleranceMode() {
    return highToleranceMode;
  }

  public boolean closeInventoryOnDetection() {
    return closeInventory;
  }

  public boolean refreshNearbyBlocksOnDetection() {
    return refreshNearbyBlocks;
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
