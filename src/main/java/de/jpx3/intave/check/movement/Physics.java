package de.jpx3.intave.check.movement;

import com.comphenix.protocol.PacketType;
import de.jpx3.intave.IntaveLogger;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.analytics.GlobalStatisticsRecorder;
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
import de.jpx3.intave.check.movement.physics.*;
import de.jpx3.intave.check.movement.physics.evaluation.EvaluationTag;
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
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.movement.physics.MoveMetric.*;
import static de.jpx3.intave.diagnostic.message.MessageCategory.SIMFLT;
import static de.jpx3.intave.diagnostic.message.MessageCategory.SIMFUL;
import static de.jpx3.intave.math.MathHelper.*;
import static de.jpx3.intave.share.ClientMath.floor;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL;

public final class Physics extends Check {
  private static final double VL_DECREMENT_PER_VALID_MOVE = 0.08;
  private static final double VELOCITY_VL_THRESHOLD = 6;

  // Cascade breaker: a deviation this small can be produced by latency alone, and
  // this many consecutive flagged ticks of it mean we are the ones out of sync.
  private static final double CASCADE_PLAUSIBLE_DESYNC_DISTANCE = 0.3;
  private static final int CASCADE_REANCHOR_TICKS = 30;

  // null on server versions without bubble columns
  private static final Material BUBBLE_COLUMN_MATERIAL = Material.getMaterial("BUBBLE_COLUMN");
  private static final Material SOUL_SAND_MATERIAL = Material.getMaterial("SOUL_SAND");
  private static final Material MAGMA_MATERIAL = Material.getMaterial("MAGMA_BLOCK");

  private static final long TOTAL_RESET = 1000 * 60 * 60;
  private static final int AVAILABLE_POINTS = 8;
  private static final long BURST_WINDOW = 8000;
  private static final long BURST_CONGESTION = 2;

  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  private final SimulationProcessor simulationProcessor;
  private final SimulationEvaluator simulationEvaluator;
  private final boolean highToleranceMode;
  private final boolean resetItemUsage;
  private final boolean closeInventory;
  private final boolean closeInventorySilentMode;
  private final boolean refreshNearbyBlocks;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
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
    this.simulationProcessor = new PredictiveSimulationProcessor(resetItemUsage, detectNoSlowdown);
    this.simulationEvaluator = new SimulationEvaluator();
    setDefaultMitigationStrategy(MitigationStrategy.CAREFUL);
  }

  @DispatchTarget
  public void receiveMovement(User user, boolean withMovement, boolean withRotation) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
	  Simulator simulator = selectSimulator(user);
    movementData.setSimulator(simulator);
    movementData.stepHeight = simulator.stepHeight();

    Motion baseMotion = movementData.mutableBaseMotionCopy();
    simulator.simulatePreTick(user, baseMotion, movementData);
    movementData.setBaseMotion(baseMotion);

    Timings.CHECK_PHYSICS_PROC_TOT.start();
    predictFlyingPacketBeforeVelocity(user);
    // simulation
    Simulation simulation;
    try {
      simulation = simulationProcessor.simulate(user, simulator);
    } catch (IllegalStateException exception) {
      user.kick("Exception while simulating movement");
      exception.printStackTrace();
      return;
    }
    movementData.assumeOccurred(simulation);

    Timings.CHECK_PHYSICS_PROC_TOT.stop();
    Timings.CHECK_PHYSICS_EVAL.start();
    // evaluation
    evaluateBestSimulation(user, simulation);
    Timings.CHECK_PHYSICS_EVAL.stop();
    if (withMovement && movementData.motion().length() > 0.1) {
      movementData.lastMovement = System.currentTimeMillis();
    }
    if (withRotation) {
      if (movementData.rotationYaw != movementData.lastRotationYaw || movementData.rotationPitch != movementData.lastRotationPitch) {
        movementData.lastRotation = System.currentTimeMillis();
      }
    }
    movementData.lastKeyStrafe = movementData.keyStrafe;
    movementData.lastKeyForward = movementData.keyForward;
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
      if (movementData.elytraFlying && !inWater && !inLava) {
        return Simulators.ELYTRA;
      }
    }
    return Simulators.PLAYER;
  }

  @DispatchTarget
  public void endMovement(User user, boolean hasMovement) {
	  MovementMetadata movementData = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();

    double motionX = !Double.isNaN(movementData.endMotionXOverride) ? movementData.endMotionXOverride : movementData.motionX();
    double motionY = !Double.isNaN(movementData.endMotionYOverride) ? movementData.endMotionYOverride : movementData.motionY();
    double motionZ = !Double.isNaN(movementData.endMotionZOverride) ? movementData.endMotionZOverride : movementData.motionZ();
    if (hasMovement) {
      Simulator simulator = movementData.simulator();
      if (movementData.ticksPast(VELOCITY) == 0) {
        if (movementData.physicsJumped && movementData.lastVelocityApplicableForJumpDenial()) {
          movementData.physicsJumpedOverrideVL++;
        } else if (movementData.physicsJumpedOverrideVL > 0) {
          movementData.physicsJumpedOverrideVL = Math.max(0, movementData.physicsJumpedOverrideVL - 0.5);
        }
      }
      Motion motion = new Motion(motionX, motionY, motionZ);
      simulator.simulateAfterTick(
        user,
        movementData,
        movementData.position(),
        motion
      );

      if (!violationMetadata.isInActiveTeleportBundle) {
        if (violationMetadata.doNotVerifyBaseMotion) {
          violationMetadata.doNotVerifyBaseMotion = false;
        } else {
          PacketLogging logging = Modules.tracker().packetLogging();
          logging.logSystemMessage(user, () -> "MOTION LOGIC: Base motion override: " + motion.motionX + " " + motion.motionY + " " + motion.motionZ);
          movementData.setBaseMotion(motion);
        }
      }
      movementData.inactiveTick(
        FLYING_PACKET_ACCURATE,
        FLYING_PACKET_CLIENT,
        NEARBY_COLLISION_INACCURACY,
        ENTITY_USE,
        ATTACK_REDUCE,
        WATERFLOW_PUSH
      );
      if (movementData.onGround()) {
        movementData.resetPhysicsPacketRelinkFlyVL();
      }
      Material type = VolatileBlockAccess.typeAccess(user, movementData.position());
      boolean climbingInPowderSnow = type == BlockTypeAccess.POWDER_SNOW && PowderSnowCollisionModifier.canWalkOnPowderSnow(user);
      movementData.tick(IN_POWDER_SNOW, climbingInPowderSnow);
      movementData.inactiveTick(EDGE_SNEAKING_TICK_GRANTS);
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

  private void predictFlyingPacketBeforeVelocity(User user) {
    MovementMetadata movementData = user.meta().movement();
    if (movementData.ticksPast(VELOCITY) != 0) {
      return;
    }
    double motionX = movementData.baseMotionXBeforeVelocity * 0.91f;
    double motionY = (movementData.baseMotionYBeforeVelocity - 0.08) * 0.98f;
    double motionZ = movementData.baseMotionZBeforeVelocity * 0.91f;
    if (motionX != 0 && motionY != 0 && motionZ != 0) {
      SimpleColliderResult colliderResult = Colliders.simplifiedCollision(
        user.player(),
        movementData,
        movementData.verifiedLastPositionX, movementData.verifiedLastPositionY, movementData.verifiedLastPositionZ,
        motionX, motionY, motionZ
      );
      motionX = colliderResult.motionX();
      motionY = colliderResult.motionY();
      motionZ = colliderResult.motionZ();

      if (colliderResult.onGround() || movementData.onGround) {
        double distance = motionX * motionX + motionY * motionY + motionZ * motionZ;
        if (distance < 0.009) {
          movementData.physicsUnpredictableVelocityExpected = true;
          movementData.setPast(FLYING_PACKET_ACCURATE, 0);
        }
      }
    }
  }

  /**
   * True if the player's bounding box (grown generously, extra for high ping) is
   * within push range of any traced, client-synchronized entity. Deliberately more
   * lenient than {@link BaseSimulator}'s exact-tick push detection: it only needs to
   * know that a collision COULD be happening client-side, so the physics check can
   * tolerate the resulting motion instead of flagging it. Cheap: runs once per tick
   * over the already-tracked nearby-entity set (fewer iterations than the per-search
   * push pass in the simulator).
   */
  private boolean nearPushableEntity(User user, MovementMetadata movementData) {
    if (!user.meta().protocol().combatUpdate()) {
      return false; // pre-1.9 clients don't push off entities
    }
    List<Entity> entities = user.meta().connection().tracedEntities();
    if (entities.isEmpty()) {
      return false;
    }
    // Base slack ~ one entity width; add up to ~0.5 more as ping grows, since the
    // client and server disagree on entity positions by roughly latency worth of ticks.
    double margin = 0.35 + Math.min(0.5, user.latency() / 1000.0);
    BoundingBox selfBox = movementData.boundingBox().grow(margin);
    for (Entity entity : entities) {
      // NB: deliberately NOT gated on clientSynchronized. An entity that is tracked
      // but momentarily unsynchronized is exactly the case where the client's view of
      // it differs from ours -- i.e. where a push we cannot reproduce is most likely,
      // not least. Requiring it left the tolerance unarmed on entities the client was
      // being shoved by (tEntOk=0 while tEnt>0 in the traces).
      if (!entity.tracingEnabled()) {
        continue;
      }
      if (!entity.hasTypeData() || entity.typeData().isArmorStand()) {
        continue; // armor stands don't push (matches performGlobalEntityPush)
      }
      if (entity.boundingBox().intersectsWith(selfBox)) {
        return true;
      }
    }
    return false;
  }

  /**
   * True if a bubble column is inside or right next to the player's box. Riding one is
   * unsteerable and only possible where the server placed the blocks, so the physics
   * check can tolerate the impulse instead of flagging a player it fails to reproduce
   * (the column needs the block, the water above it and its "drag" state to all
   * resolve correctly on this server version). Only called on ticks that mispredict,
   * so the block scan costs nothing during normal play.
   */
  private boolean nearBubbleColumn(User user, MovementMetadata movementData) {
    Material bubbleColumn = BUBBLE_COLUMN_MATERIAL;
    if (bubbleColumn == null) {
      return false;
    }
    // The column blocks themselves only exist while the water above the source block
    // is there; if we resolve them wrongly (which is exactly when this tolerance is
    // needed) we would never find one, so the source blocks count as well -- but only
    // with water involved, so soul sand on dry land stays fully checked.
    boolean acceptSourceBlocks = movementData.inWater() || movementData.ticksPast(IN_WATER) < 40;
    BoundingBox box = movementData.boundingBox();
    int minX = floor(box.minX - 0.5);
    int maxX = floor(box.maxX + 0.5);
    int minZ = floor(box.minZ - 0.5);
    int maxZ = floor(box.maxZ + 0.5);
    // one below (standing on the soul sand) up to one above the head (the column
    // continues, and the client may already be reacting to the next block up)
    int minY = floor(box.minY - 1);
    int maxY = floor(box.maxY + 1);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), x, y, z);
          if (type == bubbleColumn) {
            return true;
          }
          if (acceptSourceBlocks && (type == SOUL_SAND_MATERIAL || type == MAGMA_MATERIAL)) {
            return true;
          }
        }
      }
    }
    return false;
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

    ColliderResult expectedMovement = simulation.collider();
    Motion context = expectedMovement.motion();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.probablyFlying() || abilityData.allowFlying();
    StringBuilder key = new StringBuilder(resolveKeysFromInput(keyForward, keyStrafe));

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
    double predictedX = context.motionX();
    double predictedY = context.motionY();
    double predictedZ = context.motionZ();
    double differenceX = predictedX - receivedMotionX;
    double differenceY = predictedY - receivedMotionY;
    double differenceZ = predictedZ - receivedMotionZ;
    double distance = MathHelper.hypot3d(differenceX, differenceY, differenceZ);
    double receivedPositionX = movementData.positionX();
    double receivedPositionY = movementData.positionY();
    double receivedPositionZ = movementData.positionZ();
    double positionX = movementData.verifiedLastPositionX();
    double positionY = movementData.verifiedLastPositionY();
    double positionZ = movementData.verifiedLastPositionZ();

    boolean onLadderCurrent = MovementCharacteristics.onClimbable(user, positionX, positionY, positionZ);
    boolean onLadder = onLadderCurrent || movementData.onLadderLast;
    movementData.onLadderLast = onLadderCurrent;

    // Entity collision check
    boolean collidedWithBoat = movementData.collidedWithBoat();
    boolean skipVLCalculation = distance <= 0.00005;

    // Arm the entity-push tolerance window while in (or recently near) contact with
    // another entity. Player-vs-player/mob shoving imparts up to ~0.05/axis that the
    // client resolves against ITS view of entity positions -- desynced from ours by
    // the round-trip latency -- so we can't reproduce it exactly and must tolerate it
    // for the whole contact period rather than only on ticks our server-side boxes
    // happened to intersect. Window scales with ping (2 ticks/100ms) so laggy combat,
    // where the desync is worst, gets proportionally more slack.
    if (movementData.pushedByEntity || nearPushableEntity(user, movementData)) {
      int window = 8 + Math.min(24, user.latency() / 50);
      if (movementData.entityPushToleranceTicks < window) {
        movementData.entityPushToleranceTicks = window;
      }
    }

    // Bubble columns: scanned only on ticks that already disagree, and the window is
    // long enough to cover the ride out of the water, where the column keeps pushing
    // from the air block above the surface.
    if (distance > 0.001 && movementData.bubbleColumnToleranceTicks <= 5
      && nearBubbleColumn(user, movementData)) {
      movementData.bubbleColumnToleranceTicks = 40;
    }

    // True while a known, bounded client/server disagreement is in flight: a
    // knockback whose application tick we cannot pin down, entity contact, or an
    // ambiguous prone state. The deviation such a window produces is expected, so it
    // must not feed any of the escalating mechanics below (accumulated offset, motion
    // override, velocity multiplier) -- that feedback is what turned single
    // mispredicted ticks into minute-long detection storms.
    boolean toleranceEngaged = movementData.velocityToleranceTicks > 0
      || movementData.entityPushToleranceTicks > 0
      || movementData.pushedByEntity
      || movementData.proneAmbiguityTicks > 0
      || movementData.sleeping
      || movementData.sleepToleranceTicks > 0
      // A piston moves the player positionally, so the displacement lands in our base
      // motion as if it were velocity and mispredicts the tick after it too.
      || movementData.pistonMotionToleranceRemaining > 0;

    Set<EvaluationTag> verticalTags = EnumSet.noneOf(EvaluationTag.class);
    Set<EvaluationTag> horizontalTags = EnumSet.noneOf(EvaluationTag.class);

    double verticalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateVerticalViolationLevelIncrease(user, predictedY, onLadder, collidedWithBoat, verticalTags);
    double horizontalViolationIncrease = skipVLCalculation ? 0 : simulationEvaluator.calculateHorizontalViolationIncrease(user, predictedX, predictedZ, onLadder, collidedWithBoat, horizontalTags);

    if (onLadder) {
      movementData.artificialFallDistance = 0;
    }

    double biasedDistance = MathHelper.hypot3d(differenceX, differenceY * 2, differenceZ);
    // Deviation collected while a tolerance window is open barely counts: it is
    // mostly latency, not evidence. Without this the accumulated offset saturates
    // after a few seconds of laggy combat and then gates in every later detection.
    violationLevelData.physicsOffset += toleranceEngaged ? Math.min(biasedDistance * 0.2, 0.01) : biasedDistance;
    violationLevelData.physicsOffset -= movementData.receivedFlyingPacketIn(2) && movementData.motion().length() < 0.1 ? Math.min(0.03, biasedDistance) : 0;
    violationLevelData.physicsOffset -= violationLevelData.physicsOffset > 0.6 ? 0.002 : 0.001;
    // Clean ticks have to be able to pay off a burst in seconds rather than half a
    // minute, otherwise one disturbance keeps the detection gate open long after the
    // player is being predicted perfectly again.
    violationLevelData.physicsOffset -= biasedDistance < 0.005 ? 0.006 : 0;
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
      && !movementData.inWater
      && !movementData.collidedWithBoat();

    if (checkVelocity && !movementData.elytraFlying && movementData.ticksPast(EXTERNAL_VELOCITY) < 10 && !movementData.receivedFlyingPacketIn(2)) {
      boolean actuallyMoved = (Math.abs(predictedX) > 0.01 || Math.abs(predictedZ) > 0.01);

      boolean noCollisionOnHighVersion = !(protocol.cavesAndCliffsUpdate()
        && Collision.present(user, movementData, movementData.boundingBox().growHorizontally(0.3)));

      // Ignoring knockback means moving as if the velocity had never been applied, so
      // the deviation is on the order of the knockback itself. Deviations far below
      // that are timing noise -- the client applies the velocity when the packet
      // lands, we apply it when the anchoring transaction is acknowledged -- and
      // multiplying those by 20 is what produced the +300/+1000 detections in
      // ordinary high-ping combat. Require a share of the actual knockback before
      // treating the difference as an attempt to ignore it.
      double knockbackNoiseFloor = Math.max(0.05, movementData.velocityToleranceMagnitude * 0.35);
      boolean ignoresKnockback = distance > knockbackNoiseFloor;

      if (distance > 0.005 && !onLadder && noCollisionOnHighVersion) {
        if (actuallyMoved) {
          boolean aggressive = ignoresKnockback
            && (violationLevelData.physicsVelocityVL++ >= VELOCITY_VL_THRESHOLD || movementData.ticksPast(EXTERNAL_VELOCITY) == 0);
          if (aggressive || (distance > 0.01 && ignoresKnockback)) {
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
//      player.sendMessage(differenceY + " " + Math.abs(predictedX) + "/" + Math.abs(predictedZ) + " @" +Math.abs(predictedY - movementData.jumpMotion()) + " " + movementData.receivedFlyingPacketIn(6) + " " + movementData.past(FLYING_PACKET_ACCURATE));
//    }
    boolean flyingJump = false;
    if ((Math.abs(predictedX) < 0.1 && Math.abs(predictedZ) < 0.1) && Math.abs(predictedY - movementData.jumpMotion()) < 0.05 &&
      differenceY > 0.01 && differenceY < 0.03 /* only allow positive differenceY */ && (movementData.lastOnGround() || movementData.onGround()) /*&& movementData.receivedFlyingPacketIn(6)*/) {
//      player.sendMessage(ChatColor.RED + "Flying jump detected, " + movementData.past(FLYING_PACKET_ACCURATE));
      flyingJump = true;
      verticalViolationIncrease = 0;

      movementData.endMotionYOverride = predictedY;
    }

    // Rewriting the motion the player carries into the next tick is only correct if
    // the deviation was really the player's doing. While a tolerance window is open it
    // is ours, and replacing the client's momentum with our (smaller) prediction makes
    // the NEXT tick deviate even more -- the self-feeding loop that turned one bad
    // tick into a violation level pinned at its cap.
    boolean expectProblems = toleranceEngaged
      || movementData.ticksPast(ELYTRA_FLYING) <= 2
      || movementData.ticksPast(IN_WATER) <= 2;

    if (distance > 0.01 && !expectProblems && (verticalViolationIncrease > 5 || horizontalViolationIncrease > 5)) {
      if (Math.abs(receivedMotionX) > 0.15 && differenceX > 0.025) {
        movementData.endMotionXOverride = predictedX * 0.98;
      }
      if (Math.abs(receivedMotionY) > 0.1 && differenceY > 0.1) {
        movementData.endMotionYOverride = (predictedY - 0.08) * 0.98;
      }
      if (Math.abs(receivedMotionZ) > 0.15 && differenceZ > 0.025) {
        movementData.endMotionZOverride = predictedZ * 0.98;
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
      boolean smallMovement = (Math.abs(movementData.motionX()) < 0.08 || Math.abs(movementData.motionZ()) < 0.08) && movementData.onGround();
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
      if (IntaveLogger.traceEnabled()) {
        // Log EVERY input to the disputed prediction on one line, gated on the
        // exact failure condition (a rate-limited log would sample passing
        // ticks and hide the bug). "primPred" is what actually fired the VL --
        // the displayed DET "actual:" value is the setback reassigned below.
        Motion base = movementData.mutableBaseMotionCopy();
        // Diagnostics for the open false-positive classes. allEnt vs tEnt vs tEntOk
        // pinpoints WHERE entity-push detection dies on Folia (nothing tracked / not
        // selected into the traced set / traced but failing the push gate); nrEnt is
        // the nearest pushable entity so a too-small margin is distinguishable from a
        // truly empty set. pose/bbH/inWater/inLava discriminate crawl / trapdoor-prone
        // / bubble-column launch.
        List<Entity> tracedForDebug = user.meta().connection().tracedEntities();
        int debugPushable = 0;
        double debugNearest = Double.NaN;
        for (Entity debugEntity : tracedForDebug) {
          if (!debugEntity.tracingEnabled() || !debugEntity.clientSynchronized
            || !debugEntity.hasTypeData() || debugEntity.typeData().isArmorStand()) {
            continue;
          }
          debugPushable++;
          double debugDx = movementData.positionX() - debugEntity.position.posX;
          double debugDz = movementData.positionZ() - debugEntity.position.posZ;
          double debugDistance = Math.sqrt(debugDx * debugDx + debugDz * debugDz);
          if (Double.isNaN(debugNearest) || debugDistance < debugNearest) {
            debugNearest = debugDistance;
          }
        }
        IntaveLogger.trace("[FAIL-DEBUG] " + player.getName()
          + " kFwd=" + keyForward + " kStrafe=" + keyStrafe
          + " inFwd=" + movementData.input.forwardKey() + " inStrafe=" + movementData.input.sidewaysKey()
          + " extKey=" + movementData.externalKeyApply
          + " fric=" + movementData.friction()
          + " aiG=" + movementData.aiMoveSpeed(false) + " aiS=" + movementData.aiMoveSpeed(true)
          + " sprintAllow=" + movementData.sprintingAllowed()
          + " lastOnGnd=" + movementData.lastOnGround() + " onGnd=" + movementData.onGround()
          + " base=(" + formatDouble(base.motionX, 3) + "," + formatDouble(base.motionZ, 3) + ")"
          + " primPred=(" + formatDouble(predictedX, 3) + "," + formatDouble(predictedZ, 3) + ")"
          + " primPredY=" + formatDouble(predictedY, 4)
          + " recv=(" + formatDouble(receivedMotionX, 3) + "," + formatDouble(receivedMotionZ, 3) + ")"
          + " recvY=" + formatDouble(receivedMotionY, 4)
          + " dist=" + formatDouble(distance, 4)
          + " hInc=" + formatDouble(horizontalViolationIncrease, 2)
          + " vInc=" + formatDouble(verticalViolationIncrease, 2)
          + " vl=" + formatDouble(violationLevelData.physicsVL, 2)
          + " off=" + formatDouble(violationLevelData.physicsOffset, 3)
          // Velocity/knockback context: a transaction-anchored velocity that is
          // applied late (or never) leaves knockback out of baseMotion and
          // mispredicts every tick until the ack lands -- correlate tVel/pend
          // with ping to spot it.
          + " tVel=" + movementData.ticksPast(VELOCITY)
          + " tExtVel=" + movementData.ticksPast(EXTERNAL_VELOCITY)
          + " pendVel=" + movementData.pendingVelocityPackets.get()
          + " lastVel=(" + formatDouble(movementData.lastVelocity.motionX, 3)
          + "," + formatDouble(movementData.lastVelocity.motionY, 3)
          + "," + formatDouble(movementData.lastVelocity.motionZ, 3) + ")"
          + " ping=" + user.latency()
          // Entity-push window state: if a residual flag survives while pushEnt>0 or
          // pushTol>0, the collision tolerance is engaged and this is expected slack.
          + " pushEnt=" + movementData.pushedByEntity
          + " pushTol=" + movementData.entityPushToleranceTicks
          // Tolerance windows engaged for this tick: a residual flag while any of
          // them is open is expected slack, not a missing fix. crawl/proneAmb tell
          // the "client scaled its input by the sneaking speed" case apart from a
          // genuine mismatch, desync counts the cascade breaker's progress.
          + " velTol=" + movementData.velocityToleranceTicks
          + " velMag=" + formatDouble(movementData.velocityToleranceMagnitude, 3)
          + " crawl=" + movementData.crawling()
          + " proneAmb=" + movementData.proneAmbiguityTicks
          + " desync=" + violationLevelData.physicsDesyncTicks
          + " bubbleTol=" + movementData.bubbleColumnToleranceTicks
          + " inBlkTol=" + movementData.insideBlockToleranceTicks
          + " pistonTol=" + movementData.pistonMotionToleranceRemaining
          + " sleep=" + movementData.sleeping + " sleepTol=" + movementData.sleepToleranceTicks
          // Which blocks we think the player is standing on / inside, plus whether the
          // simulation stepped: a step, pose or friction that disagrees with the client
          // usually means we resolved one of these two block shapes differently.
          + " blkOn=" + movementData.frictionMaterial()
          + " blkIn=" + movementData.collideMaterial()
          + " step=" + movementData.step
          + " collH=" + movementData.collidedHorizontally
          + " collV=" + movementData.collidedVertically
          + " ladder=" + onLadder
          + " pose=" + movementData.pose()
          + " bbH=" + formatDouble(movementData.height(), 2)
          + " inWater=" + movementData.inWater() + " inLava=" + movementData.inLava()
          + " allEnt=" + user.meta().connection().entityIds().size()
          + " tEnt=" + tracedForDebug.size() + " tEntOk=" + debugPushable
          + " nrEnt=" + formatDouble(debugNearest, 2));
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
      Motion lastVelocity = movementData.sneakPatchVelocity;
      if (movementData.isSneaking() &&
        !movementData.onGround() &&
        lastVelocity != null
      ) {
        predictedX = Math.abs(setbackMotion.motionX) < 0.05 ? setbackMotion.motionX + MathHelper.minmax(-0.05, lastVelocity.motionX, 0.05) : setbackMotion.motionX;
        predictedY = setbackMotion.motionY;
        predictedZ = Math.abs(setbackMotion.motionZ) < 0.05 ? setbackMotion.motionZ + MathHelper.minmax(-0.05, lastVelocity.motionZ, 0.05) : setbackMotion.motionZ;
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

    // A sleeping player does not move: the position they report is the one the server
    // put them in. There is nothing here worth checking, and the two moves that bracket
    // a night -- into the bed and back out -- are server-driven and unannounced, so let
    // the verified location follow the client instead of accumulating the difference.
    if (movementData.sleeping || movementData.sleepToleranceTicks > 0) {
      violationLevelIncrease = 0;
      violationLevelData.physicsInvalidMovementsInRow = 0;
      violationLevelData.physicsDesyncTicks = 0;
    }

    if (violationLevelData.physicsInsignificantBufferVL < 3 &&
      violationLevelData.physicsVL + violationLevelIncrease > 50 &&
      violationLevelIncrease > 0 && !movementData.inWeb && !movementData.inWater &&
      distance > 0.001
    ) {
      boolean predictedNoHorizontalMovement = Math.abs(predictedX) < 0.05 && Math.abs(predictedZ) < 0.05;
      boolean horizontalFasterThanExpected = Math.abs(predictedX) < Math.abs(receivedMotionX) - 0.05 || Math.abs(predictedZ) < Math.abs(receivedMotionZ) - 0.05;

      double gainMultiplier = 1;
      if (predictedNoHorizontalMovement) {
        gainMultiplier *= 1.5;
      }
      if (horizontalFasterThanExpected) {
        gainMultiplier *= 0.5;
      }

      if (Math.abs(differenceY) < 0.1 && receivedMotionY < predictedY + 0.01 &&
        Math.abs(differenceX) < 0.15 * gainMultiplier && Math.abs(differenceZ) < 0.15 * gainMultiplier &&
        Math.abs(differenceX) + Math.abs(differenceZ) < 0.2 * gainMultiplier &&
        distance < 0.25
      ) {
        violationLevelData.physicsInsignificantBufferVL += (distance < 0.05 ? 0.5 : 1);
        violationLevelIncrease = 0;
      }
    }

    // Cascade breaker: while we keep flagging, the verified location stops advancing
    // and every detection sets the player back, which desyncs them further and
    // produces the next flag. If the deviation never grows past what a desync can
    // explain, the sustained disagreement is ours, not the player's: forget the
    // stacking state and re-anchor on the client's position so the chain restarts
    // from a state both sides agree on. The violation level itself is NOT reset --
    // an actual cheat keeps accumulating it, it just cannot inflate itself anymore.
    boolean reAnchor = false;
    if (violationLevelIncrease > 0) {
      if (distance < CASCADE_PLAUSIBLE_DESYNC_DISTANCE) {
        reAnchor = ++violationLevelData.physicsDesyncTicks >= CASCADE_REANCHOR_TICKS;
      } else {
        violationLevelData.physicsDesyncTicks = 0;
      }
    } else {
      violationLevelData.physicsDesyncTicks = 0;
    }
    if (reAnchor) {
      violationLevelData.physicsDesyncTicks = 0;
      violationLevelData.physicsInvalidMovementsInRow = 0;
      // zeroing the increase also lets the verified location advance below
      violationLevelIncrease = 0;
      violationLevelData.physicsOffset = Math.min(violationLevelData.physicsOffset, 0.55);
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
    // A player standing inside a block is a legitimate vanilla state: closing a
    // trapdoor on yourself, a block placed into you, a chunk arriving late. Blocks do
    // not push entities out, so the client keeps moving normally while our simulation
    // resolves collisions against a box the player is already inside -- everything it
    // predicts from here is a guess. Tolerate it for the duration (and a moment after)
    // instead of flagging every tick of it.
    if (boundingBoxIntersectionCurrent || boundingBoxIntersectionLast) {
      movementData.insideBlockToleranceTicks = 10;
    }
    // "Moved into" means moved: phasing through a block requires movement, while a
    // block closing around a standing player does not. Without this, the trapdoor a
    // player closes on themselves produced a setback.
    boolean actuallyMovedIn = movementData.motion().length() > 0.05;
    boolean movedIntoBlock = !boundingBoxIntersectionLast && boundingBoxIntersectionCurrent && actuallyMovedIn;
    if (boundingBoxIntersectionCurrent && !spectator) {
      List<BoundingBox> intersectionBoundingBoxesCurrent = Collision.__INVALID__resolveBoxes__OnlyForBoxIntersectionChecks__(player, currentBoundingBox);
      BoundingBox firstIntersection = intersectionBoundingBoxesCurrent.isEmpty()
        ? null : intersectionBoundingBoxesCurrent.get(0);
      // Landing on a block whose collision box is taller than it looks -- walls and
      // fences are 1.5 high -- puts the player's feet inside it for the tick the
      // client needs to resolve the landing. That is not phasing into a block, and
      // setting them back for it is what produced the "moved into stonebrickwall"
      // rubber-band while running along walls.
      boolean landingOnTop = firstIntersection != null
        && movementData.motionY() <= 0
        && receivedPositionY >= firstIntersection.maxY - 0.3;
      if (movedIntoBlock && firstIntersection != null && !landingOnTop) {
        movementData.invalidMovement = true;
        BoundingBox boundingBox = firstIntersection;
        double blockPositionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double blockPositionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
        double blockPositionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        Block block = VolatileBlockAccess.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
        boolean currentlyInOverride = blockStateAccess.currentlyInOverride(floor(blockPositionX), floor(blockPositionY), floor(blockPositionZ));
        boolean altered = BlockTypeAccess.hasTranslation(user, BlockTypeAccess.typeAccess(block));

        String colliderName;
        if (!Collision.blockInsideBorder(user, player.getWorld(), blockPositionX, blockPositionZ)) {
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
          blockStateAccess.invalidateAll();
        }
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(player).withMessage(message).withDetails(details).withVL(0).build();
        Modules.violationProcessor().processViolation(violation);
        Motion emulationMotion = new Motion(predictedX, predictedY, predictedZ);
        Modules.mitigate().movement().emulationSetBack(player, emulationMotion, 2, true);
      }
    }

    if (!boundingBoxIntersectionCurrent && !boundingBoxIntersectionLast) {
      movementData.currentlyInBlock = false;
    }

    // Update the player's verified location. Refusing to anchor inside a block keeps
    // setbacks from teleporting a player into one, but a player who legitimately
    // STAYS inside one (that trapdoor again) would otherwise keep an ever more stale
    // anchor -- and the setback that eventually fires yanks them back to wherever they
    // were when it froze. Once they were already inside at the anchor too, there is
    // nothing safer to hold on to, so let it follow.
    boolean anchorBlockedByCollision = boundingBoxIntersectionCurrent && !boundingBoxIntersectionLast;
    if (spectator || (violationLevelIncrease == 0 && !anchorBlockedByCollision)) {
      Location location = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
      movementData.setVerifiedLocation(location);
    }

    if (violationLevelIncrease > 0) {
      boolean uncommonArea = //movementData.past(WATER_MOVEMENT) < 20
        movementData.collidedHorizontally
        || movementData.collidedWithBoat()
        || movementData.inWeb
        || movementData.ticksPast(ELYTRA_FLYING) < 20;
      if (uncommonArea) {
        violationLevelIncrease /= 2;
      } else if (protocol.waterUpdate()) {
        violationLevelIncrease /= 2;
      }
      violationLevelIncrease = Math.min(200.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL + violationLevelIncrease, 200);
      violationLevelData.physicsInvalidMovementsInRow += (distance < 0.01 ? 0.25 : (distance < 0.05 ? 0.5 : 1));
      if (violationLevelData.physicsVL > 20) {
        if (!IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT) {
          blockStateAccess.invalidateAll();
        }
      }
    } else {
      if (violationLevelData.physicsInvalidMovementsInRow >= 0) {
        violationLevelData.physicsInvalidMovementsInRow *= 0.95;
        violationLevelData.physicsInvalidMovementsInRow -= movementData.motion().horizontalLength() > 0.1 ? .15 : .05;
      }
      statisticApply(user, CheckStatistics::increasePasses);
    }

    boolean setback = false;
    double latantDistance = 0.7;
    boolean offsetRequirement = violationLevelData.physicsOffset > latantDistance && distance > 0.001;

    PacketLogging logging = Modules.tracker().packetLogging();
    double finalVerticalViolationIncrease = verticalViolationIncrease;
    double finalHorizontalViolationIncrease = horizontalViolationIncrease;
    logging.logSystemMessage(user, () -> "MOVEMENT PROCESS: " + receivedMotionX + " " + receivedMotionY + " " + receivedMotionZ + " vl" + violationLevelData.physicsVL + " acc/off" +  violationLevelData.physicsOffset + " d" + distance + " h/v:" + finalHorizontalViolationIncrease +"/" + finalVerticalViolationIncrease + " spec" + spectator + " fly" + flying  + " " + verticalTags + " " + horizontalTags);

    // santiy checks
    performMovementSanityChecks(user, receivedMotionX, receivedMotionY, receivedMotionZ);

    if (offsetRequirement && !spectator && violationLevelData.physicsVL > 50 && violationLevelIncrease > 0) {
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);
      String message = "moved incorrectly";
      String details = received + " actual: " + expected;

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
      granularDebugs.put("distance", formatDouble(distance, 3));
      granularDebugs.put("pose", movementData.pose().name());
      granularDebugs.put("vehicle", movementData.isInVehicle() ? (movementData.isInRidingVehicle() ? "riding" : "passive") : "none");
      granularDebugs.put("insig", formatDouble(violationLevelData.physicsInsignificantBufferVL, 1));
      granularDebugs.put("acc/off", formatDouble(violationLevelData.physicsOffset, 2));
      granularDebugs.put("s/c v", MinecraftVersion.current().getVersion() + " / " + user.protocolVersion());
      BlockShape collShape = Collision.shape(user, movementData, currentBoundingBox);
      granularDebugs.put("coll", collShape.toString());
      granularDebugs.put("coll_out", collShape.outline().toCompactString());
      granularDebugs.put("v/tags", verticalTags.stream().map(EvaluationTag::toString).map(String::toUpperCase).distinct().collect(Collectors.joining(",")));
      granularDebugs.put("h/tags", horizontalTags.stream().map(EvaluationTag::toString).map(String::toUpperCase).distinct().collect(Collectors.joining(",")));

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
      double distanceMoved = MathHelper.hypot3d(movementData.motionX(), movementData.motionY(), movementData.motionZ());

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
          if (receivedMotionY > Math.max(0.42f, movementData.jumpMotion()) + 0.01) {
            setback = true;
          }
          manualOverrideDistance = 0.75;
          break;
        case LENIENT:
          setback = deepPitchViolationOverflow || (highPitchViolationOverflow && (freeOfColliders || violationLevelIncrease > 50) && (violationLevelAfter > 30 || highPitchAggressiveViolationOverflow || user.justJoined()));
          if (receivedMotionY > Math.max(0.42f, movementData.jumpMotion()) + 0.01) {
            setback = true;
          }
          manualOverrideDistance = 0.75;
          break;
        case BARELY:
          boolean flagAnywayss = freeOfColliders && ((isMidAir && violationLevelAfter > 60) || (verticalViolationIncrease >= 100 && predictedY < 0 && violationLevelAfter >= 100));
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
        Math.abs(predictedX - receivedMotionX) < 0.25 &&
        Math.abs(predictedY - receivedMotionY) < 0.25 &&
        Math.abs(predictedZ - receivedMotionZ) < 0.25 &&
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
        simulator.setback(user, movement, predictedX, predictedY, predictedZ);
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

    GlobalStatisticsRecorder recorder = plugin.analytics().recorderOf(GlobalStatisticsRecorder.class);
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
      if (fly) {
        debug += ChatColor.STRIKETHROUGH;
      }
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

      debug += " y:" + formatDouble(movementData.motionY(), 4) + "@" + decimalPlacesOf(receivedPositionY, 4);

//      debug += " x:" + formatDouble(movementData.motionX(), 4) + " z:" + formatDouble(movementData.motionZ(), 4);
      if (!simulation.details().isEmpty()) {
        debug += ChatColor.ITALIC + " " + simulation.details() + chatColor;
      }
      if (simulation.resultsInFlyingPacket(movementData, 0.03)) {
        debug += " nwbf";
      }
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
      if (inventory.handActive()) {
        debug += ChatColor.ITALIC + " hnd:" + inventory.handActiveTicks + chatColor;
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
      if (movementData.inWater) {
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
        debug += "; V" + verticalTags.stream().map(EvaluationTag::toString).map(String::toLowerCase).distinct().collect(Collectors.joining(","));
      }
      if (!horizontalTags.isEmpty()) {
        debug += "; H" + horizontalTags.stream().map(EvaluationTag::toString).map(String::toLowerCase).distinct().collect(Collectors.joining(","));
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
//          debug += " yexp:" + formatDouble(predictedY, 4) + "@" + decimalPlacesOf(movementData.verifiedPositionY(), 4);
//        }

      Map<String, Double> serverDebugData = simulation.collider().debugData();
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
//        player.sendMessage("Expected " + formatPosition(predictedX, predictedY, predictedZ) + " received " + formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ));
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

      String distanceOutput = formatDouble(distance, distance < 0.1 && violationLevelIncrease > 0 ? 9 : 3);
      if (movementData.receivedFlyingPacketIn(1)) {
        distanceOutput = "~" + distanceOutput;
      } else if (distance >= 0.01 && violationLevelIncrease == 0) {
        distanceOutput = ChatColor.STRIKETHROUGH + distanceOutput + chatColor;
      }
      debug += " d:" + distanceOutput;

      // horizontal and vertical distance difference
//      debug += " h:" + formatDouble(Math.abs(differenceX) + Math.abs(differenceZ), 3);
//      debug += " v:" + formatDouble(Math.abs(differenceY), 3);


      if (debug.startsWith(" ")) {
        debug = debug.substring(1);
      }
      String finalDebug = debug;
      if (!anyDebugRequested) {
        user.sendMessage(finalDebug);
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
  }

  private void refreshNearbyBlocks(User user, double x, double y, double z) {
    if (!refreshNearbyBlocksOnDetection()) {
      return;
    }
    BoundingBox box = BoundingBox.fromPosition(user, user.meta().movement(), x, y, z).grow(1.2);
    Player player = user.player();
    List<Position> positions = Collision.collectCollidingPositions(player, box, 16, Collectors.toList());
    Synchronizer.synchronize(user, () -> {
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
      Synchronizer.synchronize(user, () -> {
        movementData.dealCustomFallDamage = true;
//        fallDamageApplier.dealFallDamage(player, 8);
        movementData.dealCustomFallDamage = false;
      });
    }
  }

  private void performMovementSanityChecks(User user, double receivedMotionX, double receivedMotionY, double receivedMotionZ) {
    MovementMetadata movementData = user.meta().movement();
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

  /**
   * Entering and leaving a bed moves the player without a teleport packet: the server
   * repositions them onto the bed and the client does the same move on its own, so
   * there is nothing for the movement engine to anchor on and it sees a several-block
   * jump out of nowhere. While asleep the client then holds a position we never
   * predicted, and every tick reports the whole offset as motion.
   * <p>
   * MONITOR priority so a listener that cancels the bed entry is respected, and the
   * flag is read on the packet thread instead of {@code Player#isSleeping()}, which
   * would touch the live entity handle off its region.
   */
  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBedEnter(PlayerBedEnterEvent event) {
    markSleeping(event.getPlayer(), true);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR)
  public void onBedLeave(PlayerBedLeaveEvent event) {
    markSleeping(event.getPlayer(), false);
  }

  private void markSleeping(Player player, boolean sleeping) {
    User user = UserRepository.userOf(player);
    if (user == null) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    movementData.sleeping = sleeping;
    // Covers the move onto the bed and the one back out of it, plus the round trip it
    // takes for the two sides to agree on where that left the player.
    movementData.sleepToleranceTicks = 20 + Math.min(40, user.latency() / 25);
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  public boolean highToleranceMode() {
    return highToleranceMode;
  }

  public boolean resetItemUsageOnDetection() {
    return resetItemUsage;
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
