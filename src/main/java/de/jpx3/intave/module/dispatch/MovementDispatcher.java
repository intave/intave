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

package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.packet.view.AnimationView;
import de.jpx3.intave.packet.view.EntityVelocityView;
import de.jpx3.intave.packet.view.FeedbackHandle;
import de.jpx3.intave.packet.view.MovementView;
import de.jpx3.intave.packet.view.PacketEventsAnimationView;
import de.jpx3.intave.packet.view.PacketEventsEntityVelocityView;
import de.jpx3.intave.packet.view.ProtocolLibEntityVelocityView;
import de.jpx3.intave.packet.view.PacketEventsFeedbackHandle;
import de.jpx3.intave.packet.view.PacketEventsMovementView;
import de.jpx3.intave.packet.view.PacketEventsPlayerActionView;
import de.jpx3.intave.packet.view.PacketEventsWorldBorderView;
import de.jpx3.intave.packet.view.PlayerActionView;
import de.jpx3.intave.packet.view.ProtocolLibAnimationView;
import de.jpx3.intave.packet.view.ProtocolLibFeedbackHandle;
import de.jpx3.intave.packet.view.ProtocolLibMovementView;
import de.jpx3.intave.packet.view.ProtocolLibPlayerActionView;
import de.jpx3.intave.packet.view.ProtocolLibWorldBorderView;
import de.jpx3.intave.packet.view.WorldBorderView;
import de.jpx3.intave.block.special.BedWakeupPositionSearch;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.update.MotionAddUpdate;
import de.jpx3.intave.check.movement.physics.update.MotionSetUpdate;
import de.jpx3.intave.check.movement.physics.update.PistonActionUpdate;
import de.jpx3.intave.check.movement.physics.update.ShulkerBoxActionUpdate;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.test.PhysicsTestRecorder;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.PistonSlimeAction;
import de.jpx3.intave.module.test.record.action.ShulkerBoxAction;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.converter.InputConverter;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.Particles;
import de.jpx3.intave.world.WorldHeight;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.DEBUG_MOVEMENT_IGNORE;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;

public final class MovementDispatcher extends Module {
  private static final long PISTON_SNAPSHOT_RETENTION_NANOS = TimeUnit.SECONDS.toNanos(2);

  private Physics physicsCheck;
  private TeleportController teleportController;
  private InteractionRaytrace interactionRaytraceCheck;
  private Timer timerCheck;
  private final Map<PistonSnapshotKey, PistonSnapshot> pistonSnapshots = new ConcurrentHashMap<>();
  private final AtomicLong nextPistonSnapshotCleanup = new AtomicLong(System.nanoTime());

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void capturePistonExtension(BlockPistonExtendEvent event) {
    capturePistonAction(event.getBlock(), event.getBlocks(), true);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void capturePistonRetraction(BlockPistonRetractEvent event) {
    capturePistonAction(event.getBlock(), event.getBlocks(), false);
  }

  private void capturePistonAction(Block piston, List<Block> movedBlocks, boolean extending) {
    long now = System.nanoTime();
    cleanupPistonSnapshots(now);

    List<BlockPosition> slimeSources = new ArrayList<>();
    for (Block movedBlock : movedBlocks) {
      if (movedBlock.getType() == Material.SLIME_BLOCK) {
        slimeSources.add(new BlockPosition(
          movedBlock.getX(), movedBlock.getY(), movedBlock.getZ()
        ));
      }
    }

    PistonSnapshotKey key = new PistonSnapshotKey(
      piston.getWorld().getUID(),
      new BlockPosition(piston.getX(), piston.getY(), piston.getZ()),
      extending
    );
    pistonSnapshots.put(key, new PistonSnapshot(slimeSources, now));
  }

  private void cleanupPistonSnapshots(long now) {
    long nextCleanup = nextPistonSnapshotCleanup.get();
    if (now < nextCleanup || !nextPistonSnapshotCleanup.compareAndSet(
      nextCleanup, now + PISTON_SNAPSHOT_RETENTION_NANOS
    )) {
      return;
    }
    pistonSnapshots.forEach((key, snapshot) -> {
      if (snapshot.expired(now)) {
        pistonSnapshots.remove(key, snapshot);
      }
    });
  }

  @Override
  public void enable() {
    CheckService checks = plugin.checks();
    this.physicsCheck = checks.searchCheck(Physics.class);
    this.interactionRaytraceCheck = checks.searchCheck(InteractionRaytrace.class);
    this.timerCheck = checks.searchCheck(Timer.class);
    this.teleportController = new TeleportController();
    this.teleportController.setup();
  }

  @BukkitEventSubscription(
    priority = EventPriority.MONITOR
  )
  public void receiveExternalTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketLogging logging = Modules.tracker().packetLogging();
    PlayerTeleportEvent.TeleportCause cause = event.getCause();
    if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || event.isCancelled()) {
      logging.logSystemMessage(user, () ->
        "TELEPORT CORRECTION SKIPPED id=" + Integer.toHexString(System.identityHashCode(event)) +
          " cause=" + cause + " cancelled=" + event.isCancelled()
      );
      return;
    }
    Location fromLocation = event.getFrom();
    Location toLocation = event.getTo();
    double teleportDistance = toLocation.getWorld() != player.getWorld() ? Double.MAX_VALUE : toLocation.distance(fromLocation);
//    if (toLocation.getWorld() != player.getWorld() || teleportDistance > 8) {
//      Location fixedLocation = fixLocation(user, toLocation);
//      event.setTo(fixedLocation);
//      logging.logSystemMessage(user, () ->
//        "TELEPORT CORRECTION APPLIED id=" + Integer.toHexString(System.identityHashCode(event)) +
//          " distance=" + teleportDistance +
//          " requested=" + MathHelper.formatPosition(toLocation) +
//          " corrected=" + MathHelper.formatPosition(fixedLocation)
//      );
//    } else {
      logging.logSystemMessage(user, () ->
        "TELEPORT CORRECTION NOT_REQUIRED id=" + Integer.toHexString(System.identityHashCode(event)) +
          " distance=" + teleportDistance +
          " requested=" + MathHelper.formatPosition(toLocation)
      );
//    }
    MovementMetadata movementData = user.meta().movement();
    movementData.artificialFallDistance = 0;
  }

  @BukkitEventSubscription
  public void worldChange(PlayerChangedWorldEvent worldChange) {
    Player player = worldChange.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    movementData.dismountRidingEntity();
  }

  @BukkitEventSubscription
  public void receiveRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ConnectionMetadata connection = meta.connection();
    MovementMetadata movementData = meta.movement();
    movementData.artificialFallDistance = 0;
    movementData.dismountRidingEntity();
    connection.lastRespawn = System.currentTimeMillis();
    FakePlayer fakePlayer = meta.attack().fakePlayer();
    if (fakePlayer != null) {
      fakePlayer.respawn();
    }
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR)
  public void postShift(PlayerRespawnEvent respawn) {
    Player player = respawn.getPlayer();
    User user = UserRepository.userOf(player);
    Location respawnLocation = respawn.getRespawnLocation().clone();
//    respawn.setRespawnLocation(fixLocation(user, respawnLocation));
  }

  private static final int BASE_SHIFTS = 8;

  private Location fixLocation(User user, Location location) {
    if (location == null) {
      return null;
    }
    boolean inLoadedChunk = VolatileBlockAccess.isInLoadedChunk(
      location.getWorld(), location.getBlockX(), location.getBlockZ()
    );
    if (!inLoadedChunk) {
      Modules.tracker().packetLogging().logSystemMessage(user, () ->
        "TELEPORT LOCATION FIX skipped=unloaded_chunk location=" + MathHelper.formatPosition(location)
      );
      return location;
    }

    MovementMetadata movement = user.meta().movement();
    int baseShifts = BASE_SHIFTS;
    Location fixedLocation = location.clone();
    World world = location.getWorld();
    int collisionShifts = 0;
    int clearanceShifts = 0;

    // A: move out of existing blocks
    BoundingBox bb = BoundingBox.fromPosition(user, movement, fixedLocation);
    boolean initiallyColliding = Collision.unsafePresent(world, user.player(), bb);
    while (fixedLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && baseShifts-- > 0 && Collision.unsafePresent(world, user.player(), bb) && Collision.unsafeNonePresent(world, user.player(), bb.offset(0, BASE_SHIFTS * 0.1, 0))) {
      fixedLocation.add(0, 0.101, 0);
      collisionShifts++;
      bb = BoundingBox.fromPosition(user, movement, fixedLocation).grow(0.1);
    }

    // B: if clear of blocks, move up 0.55 block
    baseShifts = 5;
    bb = BoundingBox.fromPosition(user, movement, fixedLocation);
    while (fixedLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && baseShifts-- > 0 && Collision.unsafeNonePresent(world, user.player(), bb)) {
      fixedLocation.add(0, 0.101, 0);
      clearanceShifts++;
      bb = BoundingBox.fromPosition(user, movement, fixedLocation).grow(0.1).expand(0.5, 0.45, 0.5);
    }
    int finalCollisionShifts = collisionShifts;
    int finalClearanceShifts = clearanceShifts;
    boolean finallyColliding = Collision.unsafePresent(world, user.player(), BoundingBox.fromPosition(user, movement, fixedLocation));
    Modules.tracker().packetLogging().logSystemMessage(user, () ->
      "TELEPORT LOCATION FIX loaded=true initially_colliding=" + initiallyColliding +
        " collision_shifts=" + finalCollisionShifts +
        " clearance_shifts=" + finalClearanceShifts +
        " finally_colliding=" + finallyColliding +
        " from=" + MathHelper.formatPosition(location) +
        " to=" + MathHelper.formatPosition(fixedLocation)
    );
    return fixedLocation;
  }

  @BukkitEventSubscription
  public void receiveWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    user.blockCache().invalidateAll();
    user.refreshSprintState();
  }

  @BukkitEventSubscription
  public void receiveVehicleMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    if (!movementData.isInVehicle()) {
      return;
    }
    Location location = event.getTo();
    ProtocolMetadata clientData = meta.protocol();
    if (clientData.protocolVersion() >= VER_1_9) {
      return;
    }
    movementData.lastPositionX = movementData.positionX;
    movementData.lastPositionY = movementData.positionY;
    movementData.lastPositionZ = movementData.positionZ;
    movementData.positionX = location.getX();
    movementData.positionY = location.getY();
    movementData.positionZ = location.getZ();
    movementData.lastRotationYaw = movementData.rotationYaw;
    movementData.lastRotationPitch = movementData.rotationPitch;
    movementData.rotationYaw = location.getYaw();
//    movementData.rotationPitch = location.getPitch();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      RESPAWN
    }
  )
  public void sentRespawn(PacketEvent event) {
    handleRespawn(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      RESPAWN
    }
  )
  public void sentRespawn(Player player) {
    if (player == null) {
      return;
    }
    handleRespawn(player);
  }

  /** Engine independent respawn handling; nothing is read off the packet, only the player. */
  private void handleRespawn(Player player) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    violationLevelData.physicsVelocityVL = 0;
    violationLevelData.physicsVL = Math.max(0, violationLevelData.physicsVL - 10);
    synchronizeRespawn(player);
  }

  private void synchronizeRespawn(Player player) {
    User user = UserRepository.userOf(player);
    user.tickFeedback(() -> {
      MetadataBundle meta = user.meta();
      MovementMetadata movement = meta.movement();
      ProtocolMetadata protocol = meta.protocol();
      InventoryMetadata inventory = meta.inventory();
      movement.sneaking = false;
      movement.sleepingBedPosition = null;
      movement.setSprinting(false);
      movement.setSwimming(false);
      if (protocol.protocolVersion() >= VER_1_16) {
        user.refreshSprintState();
      }
      Synchronizer.synchronize(user, inventory::releaseItemNextTick);
      movement.baseMotionX = 0;
      movement.baseMotionY = 0;
      movement.baseMotionZ = 0;
      movement.clearPostTickMotionCandidates();
      user.blockCache().invalidateAll();
      meta.potions().clearPotionEffects();
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    handleMovement(new ProtocolLibMovementView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketReceiveEvent event) {
    PacketEventsMovementView view = PacketEventsMovementView.of(event);
    if (view != null) {
      handleMovement(view);
    }
  }

  /** Engine independent movement handling; see {@link MovementView}. */
  private void handleMovement(MovementView view) {
    PacketLogging logging = Modules.tracker().packetLogging();

    Player player = view.player();
    if (player.isDead() || view.cancelled()) {
      logging.logSystemMessage(UserRepository.userOf(player), () -> "MOVEMENT IGNORED: Player is dead or event is cancelled");
      return;
    }

    

    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AttackMetadata attackData = meta.attack();
    InventoryMetadata inventoryData = meta.inventory();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ConnectionMetadata connectionData = meta.connection();
    ProtocolMetadata protocol = meta.protocol();

    boolean vehicleMove = view.isVehicleMove();
	  boolean hasMovement = view.hasMovement();
    boolean hasRotation = view.hasRotation();

    if (movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movement.setRotation(view.yaw(), view.pitch());
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Vehicle rotation only");
      view.release();
      return;
    }

    /**
     * The vehicle move only contains the boat rotation, which is incorrect.
     * We do receive a simple player look packet before the vehicle move, so we simply rely on that.
     */
    if (vehicleMove) {
      hasRotation = false;
    }

    boolean clientVehicleMovement = MinecraftVersions.VER1_9_0.atOrAbove() && protocol.combatUpdate();
    if (movement.isInRidingVehicle() && !vehicleMove && clientVehicleMovement && !movement.awaitTeleport) {
      movement.dismountRidingEntity("Client vehicle movement");
    }

    if (movement.isInRidingVehicle() && !vehicleMove && hasMovement) {
      if (movement.invalidVehiclePositionTicks++ > 10) {
        movement.dismountRidingEntity("Lower client vehicle movement");
      }
    }

    if (view.anyNaNOrInfiniteValue() && FaultKicks.POSITION_FAULTS) {
      user.kick("NaN/infinite in server-bound movement packet");
      return;
    }

    if (hasMovement || movement.isInVehicle() || movement.inRespawnScreen) {
      movement.lastPositionUpdate = 0;
    } else if (++movement.lastPositionUpdate > 20 && FaultKicks.MISSING_POSITION_UPDATE && !user.justJoined() && !user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      user.kick("Missing position update " + movement.vehicle());
    }

    // fix only works for 1.8
    if (movement.sprinting && movement.isSneaking() && movement.lastSneaking && !protocol.combatUpdate() && movement.acceptSneakFaults && FaultKicks.INVALID_PLAYER_ACTION && !user.justJoined() && !user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      movement.acceptSneakFaults = false;
      user.refreshSprintState(unused -> {
        movement.sprintSneakFaults++;
        movement.acceptSneakFaults = true;
      });
      if (movement.sprintSneakFaults > 1) {
        user.kick("Repeated player action faults");
      }
    }

    // see MultiPlayerGameMode#useItem
    if (protocol.useItemMovementPacket() && !movement.awaitTeleport
      && view.isPositionLook()
    ) {
      double positionX = view.positionX();
      double positionY = view.positionY();
      double positionZ = view.positionZ();
      double motionX = positionX - movement.verifiedLastPositionX;
      double motionY = positionY - movement.verifiedLastPositionY;
      double motionZ = positionZ - movement.verifiedLastPositionZ;
      double distance = MathHelper.hypot3d(motionX, motionY, motionZ);

      if (distance < 0.00001) {
        movement.dropPostTickMotionProcessing = true;
        Float yaw = view.yaw();
        Float pitch = view.pitch();
        if (DEBUG_MOVEMENT_IGNORE) {
          double yawDifference = MathHelper.noAbsDistanceInDegrees(movement.lastRotationYaw, yaw);
          double pitchDifference = MathHelper.noAbsDistanceInDegrees(movement.lastRotationPitch, pitch);
          System.out.println("[Intave] Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
        }
        logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Click movement ignore distance: " + distance);

        if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
          view.setCancelled(true);
        } else {
          view.setPosition(movement.verifiedLastPosition());
        }
        view.release();
        return;
      }
    }
    movement.awaitClickMovementSkip = false;

    if (user.receives(MessageChannel.DEBUG_POSITION)) {
      ActionBar.sendActionBar(player, "intave:" + formatDouble(movement.positionY, 2) + " server:" + formatDouble(player.getLocation().getY(), 2));
    }

    connectionData.receiveMovement();
    movement.updateMovement(
      view.positionX(), view.positionY(), view.positionZ(),
      view.yaw(), view.pitch(),
      hasMovement, hasRotation
    );
    inventoryData.updateSlotSwitch();

    if (hasMovement) {
      logging.logSystemMessage(user, () -> "MOTION LOGIC: Received motion: " + movement.sentOffsetMotion());
    }

    teleportController.receiveMovement(view);

    if (IntaveControl.DEBUG_COLLISION_BOXES || user.receives(MessageChannel.DEBUG_COLLISIONS)) {
      BoundingBox box = movement.boundingBox().grow(0.1);
      BlockShape shape = Collision.shape(user, movement, box);
      drawDebugBoxes(user, BlockShapes.optimize(shape).elementaryBoxes());
    }

    if (user.receives(MessageChannel.DEBUG_HITBOX)) {
      for (Position vertex : movement.boundingBox().vertices()) {
        Particles.spawnVillagerHappyParticleAt(user, vertex);
      }
    }

    if (movement.awaitTeleport || movement.awaitOutgoingTeleport) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      }
      view.setCancelled(true);
      movement.dropPostTickMotionProcessing = true;
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      view.release();
      return;
    }

    double distance = movement.verifiedLastPosition().distance(movement.position());

    if (distance > 50) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Distance movement ignore: " + distance);
      }
      logging.logSystemMessage(user, () -> "MOVEMENT REJECTED: Distance over limit: " + distance);
      movement.dropPostTickMotionProcessing = true;
      view.setCancelled(true);
      Modules.mitigate().movement().emulationSetBack(player, movement.mutableBaseMotionCopy(), 10, false);
      String message = "sent unsafe position";
      String details = "moved " + MathHelper.formatDouble(distance, 2) + " blocks";
      Map<String, String> granulars = new HashMap<>();
      granulars.put("DIST", MathHelper.formatDouble(distance, 2));
      granulars.put("FROM", movement.verifiedLastPositionX + " " + movement.verifiedLastPositionY + " " + movement.verifiedLastPositionZ);
      granulars.put("FROM_ORIGIN", movement.verifiedPositionOrigin);
      granulars.put("TO", movement.positionX + " " + movement.positionY + " " + movement.positionZ);
      granulars.put("MOTION", movement.baseMotionX + " " + movement.baseMotionY + " " + movement.baseMotionZ);
      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withGranulars(granulars).withVL(25).build();
      Modules.violationProcessor().processViolation(violation);
      view.release();
      return;
    }

    Entity attachedEntity = movement.ridingEntity();
    if (attachedEntity != null && !attachedEntity.isEntityAlive()
      && attachedEntity.hasTypeData() && attachedEntity.typeData().isLivingEntity()) {
      movement.dismountRidingEntity("Riding dead entity");
    }

    double distanceMoved = Hypot.fast(movement.offsetMotionX(), movement.offsetMotionZ());
    if (inventoryData.activatedItemThisTick && inventoryData.deactivatedItemThisTick && distanceMoved > 0.1) {
      if (violationLevelData.wrappedNoSlowdownVL++ > 5) {
        user.nerfPermanently(AttackNerfStrategy.DMG_HIGH, "No slowdown");
        user.nerfPermanently(AttackNerfStrategy.BLOCKING, "No slowdown");
        user.nerfPermanently(AttackNerfStrategy.RECEIVE_MORE_KNOCKBACK, "No slowdown");
        user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "No slowdown");
        inventoryData.blockNextArrow = true;
        inventoryData.lastBlockArrowRequest = System.currentTimeMillis();
      }
    } else {
      violationLevelData.wrappedNoSlowdownVL = Math.max(0, violationLevelData.wrappedNoSlowdownVL - 0.08);
    }

    if (inventoryData.releaseItemNextTick) {
      releaseItem(user);
      inventoryData.releaseItemNextTick = false;
      inventoryData.releaseItemType = Material.AIR;
    }

    inventoryData.activatedItemThisTick = false;
    inventoryData.deactivatedItemThisTick = false;

    if (violationLevelData.isInActiveTeleportBundle) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Teleport bundle movement ignore");
      }
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport bundle movement ignore");
      movement.dropPostTickMotionProcessing = true;
      view.setCancelled(true);
      view.release();
      return;
    }

    if (!movement.isTeleportConfirmationPacket &&
      movement.canResetMotion &&
      movement.mutableBaseMotionCopy().isZero() &&
      movement.sentOffsetMotion().isZero()
    ) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Movement reset ignore");
      }
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Movement reset ignore");
      movement.canResetMotion = false;
      view.release();
      return;
    }

    if (!movement.isTeleportConfirmationPacket) {
      timerCheck.receiveMovement(view);
      if (interactionRaytraceCheck.receiveMovement(view)) {
        movement.compileSpecialBlocks();
        movement.recheckWebStateFromLastTick();
      }

      // I have neither the time nor the energy for a proper fix
      if (movement.sentOffsetMotion().length() > 0.5 && movement.ticksPast(VEHICLE_DETACHMENT) < 2) {
        movement.setBaseMotion(Motion.newEmpty());
        movement.physicsResetMotionX = true;
        movement.physicsResetMotionZ = true;
      }

      physicsCheck.receiveMovement(user, hasMovement, hasRotation);
      if (!hasMovement && !hasRotation && !movement.treatThisFlyPacketAsMovePacket) {
        logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: No movement or rotation");
      }

      boolean clientOnGround = vehicleMove ? player.isOnGround() : view.onGround();
      boolean collidedWithBoat = movement.collidedWithBoat();

      if (movement.onGround && !clientOnGround && movement.step) {
        movement.onGround = false;
      }

      if (collidedWithBoat) {
        movement.onGround = clientOnGround;
      }

      attackData.updatePerfectRotation();

      updatePotionEffects(user);
      movement.canResetMotion = false;
    } else {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Basic reset movement ignore");
      }
      movement.canResetMotion = true;
    }

    // flag & setback -> remove packet
    if (movement.invalidMovement && violationLevelData.isInActiveTeleportBundle) {
      if (!movement.awaitOutgoingTeleport) {
        movement.outgoingTeleportCountdown = 5;
      }
      movement.awaitOutgoingTeleport = true; // awaiting next teleport
      view.setCancelled(true);
    }

    view.release();
  }

  private void drawDebugBoxes(User user, List<BoundingBox> boxes) {
    boxes
      .stream()
      .flatMap(box -> box.vertices().stream())
      .distinct()
      .forEach(position -> Particles.spawnVillagerHappyParticleAt(user, position));
  }

  private void updatePotionEffects(User user) {
    boolean infiniteEffectsAllowed = user.meta().protocol().protocolVersion() >= 763;
    EffectMetadata potionData = user.meta().potions();
    if (potionData.potionEffectSpeedAmplifier() > 0) {
      if (potionData.potionEffectSpeedDuration != -1 || !infiniteEffectsAllowed) {
        if (--potionData.potionEffectSpeedDuration <= 0) {
          potionData.potionEffectSpeedAmplifier(0);
        }
      }
    }

    if (potionData.potionEffectSlownessAmplifier() > 0) {
      if (potionData.potionEffectSlownessDuration != -1 || !infiniteEffectsAllowed) {
        if (--potionData.potionEffectSlownessDuration <= 0) {
          potionData.potionEffectSlownessAmplifier(0);
        }
      }
    }

    if (potionData.potionEffectJumpAmplifier() > 0) {
      if (potionData.potionEffectJumpDuration != -1 || !infiniteEffectsAllowed) {
        if (--potionData.potionEffectJumpDuration <= 0) {
          potionData.potionEffectJumpAmplifier(0);
        }
      }
    }
  }

  private void releaseItem(User user) {
    if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
      user.player().sendMessage(IntavePlugin.prefix() + "Applying item usage reset as requested");
    }
    Player player = user.player();
    ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
    InventoryMetadata inventory = user.meta().inventory();
    if (ItemProperties.isBow(inventory.releaseItemType) || ItemProperties.isBow(inventory.activeItemType())) {
      inventory.blockNextArrow = true;
      inventory.lastBlockArrowRequest = System.currentTimeMillis();
      if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
        user.player().sendMessage(IntavePlugin.prefix() + "Requesting arrow block as player is also holding a bow on item usage reset");
      }
    }
    inventory.lastFoodConsumptionBlockRequest = System.currentTimeMillis();
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.BLOCK_DIG);
    packet.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(0, 0, 0));
    packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
    packet.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);
    user.ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
    updatePlayerHandItem(player);
    Synchronizer.synchronize(user, player::updateInventory);
    if (IntaveControl.DEBUG_ITEM_USAGE) {
      player.sendMessage(ChatColor.DARK_PURPLE + "Release item");
    }
  }

  private void updatePlayerHandItem(Player player) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    inventoryData.deactivateHand();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveFinalMovement(
    User user,
    PlayerMoveReader reader,
    Cancellable cancellable
  ) {
    handleFinalMovement(
      user,
      reader.isVehicleMove(),
      reader.hasMovement(),
      reader.hasRotation(),
      reader.onGround(),
      new MovementPacketHandle() {
        @Override
        public boolean cancelled() {
          return cancellable.isCancelled();
        }

        @Override
        public void setOnGround(boolean onGround) {
          reader.setOnGround(onGround);
        }
      }
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveFinalMovement(PacketReceiveEvent event) {
    PacketEventsMovementView view = PacketEventsMovementView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      view.release();
      return;
    }
    try {
      handleFinalMovement(
        UserRepository.userOf(player),
        view.isVehicleMove(),
        view.hasMovement(),
        view.hasRotation(),
        view.onGround(),
        new MovementPacketHandle() {
          @Override
          public boolean cancelled() {
            return view.cancelled();
          }

          @Override
          public void setOnGround(boolean onGround) {
            view.setOnGround(onGround);
          }
        }
      );
    } finally {
      // Pushes a rewritten on ground flag back into the packet; a no-op when nothing was written.
      view.release();
    }
  }

  /**
   * The only two packet operations {@link #handleFinalMovement} performs. Everything else it needs
   * is read off the packet up front, so this is the whole seam between the handler and the engine.
   */
  private interface MovementPacketHandle {
    /** @return whether an earlier subscriber cancelled this packet. */
    boolean cancelled();

    void setOnGround(boolean onGround);
  }

  /**
   * Engine independent post movement handling; runs at {@link ListenerPriority#HIGH}, after the
   * checks have seen the packet.
   */
  private void handleFinalMovement(
    User user,
    boolean vehicleMove,
    boolean hasMovement,
    boolean hasRotation,
    boolean packetOnGround,
    MovementPacketHandle packet
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    AttackMetadata attack = meta.attack();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilities = meta.abilities();
    InventoryMetadata inventory = meta.inventory();

    boolean claimsToBeOnGround = vehicleMove ? player.isOnGround() : packetOnGround;

    if (player.isDead() || movement.awaitTeleport) {
      return;
    }

    if (movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movement.setVerifiedLastPosition(
        movement.position(),
        "Vehicle rotation only, blind copy from current"
      );
      return;
    }

    if (!vehicleMove && !movement.isSleeping() && !movement.awaitTeleport && !movement.awaitOutgoingTeleport && !movement.invalidMovement && !movement.dropPostTickMotionProcessing) {
      if (claimsToBeOnGround != movement.onGround) {
        double requiredFallDistance = Collision.present(user, movement, movement.boundingBox().grow(0.1, 0.1, 0.1)) ? 0.5 : 0.1;
        boolean shulkerInteraction = movement.shulkerXToleranceRemaining > 0 || movement.shulkerYToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0;
        if (shulkerInteraction) {
          requiredFallDistance = Math.max(requiredFallDistance, 3);
        }
        if (movement.artificialFallDistance > requiredFallDistance && !movement.onGround && claimsToBeOnGround) {
          Violation violation = Violation.builderFor(Physics.class)
            .forUser(user)
            .withMessage("claimed to be on ground midair")
            .withDetails("falling " + formatDouble(movement.artificialFallDistance, 2) + " blocks")
            .withVL(0.5)
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .build();
          Modules.violationProcessor().processViolation(violation);
        }
        if (movement.artificialFallDistance > requiredFallDistance || Math.abs(movement.offsetMotionY()) > 0.01) {
          packet.setOnGround(movement.onGround);
        }
      }
    }

    if (!packet.cancelled() && !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing) {
      physicsCheck.endMovement(user, hasMovement, hasRotation);
      movement.lastOnGround = movement.onGround;
      movement.setVerifiedLastPosition(
        movement.position(),
        "Verification push"
      );
    }

    attack.tickComplete();
    movement.tickComplete(hasMovement, hasRotation, true);
    abilities.tickComplete();
    inventory.tickComplete();

    Map<String, Double> clientDebugData = movement.clientMovementDebugValues;
    Map<String, Double> serverDebugData = movement.serverMovementDebugValues;
    if (!clientDebugData.isEmpty() || !serverDebugData.isEmpty()) {
      if (IntaveControl.MOVEMENT_DEBUGGER_COLLECTOR_POSTTICK_OUTPUT) {
        String message = clientDebugData.entrySet().stream().map(entry -> {
          String key1 = entry.getKey();
          double value = entry.getValue();
          return "C" + key1 + ":" + formatDouble(value, 4);
        }).collect(Collectors.joining(" "));
        message += " " + serverDebugData.entrySet().stream().map(entry -> {
          String key1 = entry.getKey();
          double value = entry.getValue();
          return "S" + key1 + ":" + formatDouble(value, 4);
        }).collect(Collectors.joining(" "));
        user.sendMessage(message);
      }
      clientDebugData.clear();
      serverDebugData.clear();
    }
  }

  @PacketSubscription(
    packetsIn = {
      STEER_VEHICLE
    }
  )
  public void receiveClientKeys(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    PacketContainer packet = event.getPacket();
    if (MinecraftVersions.VER1_21_2.atOrAbove()) {
      if (!user.meta().protocol().sendsInputs()) {
        return;
      }
      StructureModifier<Boolean> inputBooleans = packet.getStructures().read(0).getBooleans();
      movementData.lastInput = movementData.input;
      movementData.input = new Input(
        inputBooleans.read(0),
        inputBooleans.read(1),
        inputBooleans.read(2),
        inputBooleans.read(3),
        inputBooleans.read(4),
        inputBooleans.read(5),
        inputBooleans.read(6)
      );
      if (user.receives(MessageChannel.DEBUG_SENT_INPUT)) {
        ActionBar.sendActionBar(player, String.valueOf(movementData.input));
      }
    } else {
      handleLegacyVehicleKeys(
        user,
        packet.getFloat().read(0),
        packet.getFloat().read(1),
        packet.getBooleans().read(0)
      );
    }
  }

  /**
   * PacketEvents twin of {@link #receiveClientKeys(PacketEvent)}, limited to the legacy steer
   * vehicle packet.
   * <p>
   * The 1.21.2+ half of the ProtocolLib body reads the {@code net.minecraft.world.entity.player.Input}
   * structure out of the new {@code PLAYER_INPUT} packet. PacketEvents 2.4.0 knows neither that
   * packet type ({@code PacketType.Play.Client} stops at {@code STEER_VEHICLE}/{@code STEER_BOAT})
   * nor a wrapper for it, and its newest supported {@code ServerVersion} is {@code V_1_21}, so the
   * branch is unreachable on every server this engine can attach to. Mirroring the original's
   * version switch therefore reproduces the ProtocolLib behaviour exactly wherever the PacketEvents
   * engine actually runs, instead of guessing at a field layout that does not exist here.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      STEER_VEHICLE
    }
  )
  public void receiveClientKeys(PacketReceiveEvent event, Player player) {
    if (player == null || MinecraftVersions.VER1_21_2.atOrAbove()) {
      return;
    }
    if (event.getPacketType()
      != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.STEER_VEHICLE) {
      return;
    }
    WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);
    handleLegacyVehicleKeys(
      UserRepository.userOf(player),
      wrapper.getSideways(),
      wrapper.getForward(),
      wrapper.isJump()
    );
  }

  /**
   * Engine independent legacy vehicle key handling. The packet carries three values, so this takes
   * them directly the same way {@link #handleFoodUpdate} does rather than through a view.
   * <p>
   * {@code sideways} is the first float of the packet and {@code forward} the second, matching the
   * ProtocolLib field order the original body read.
   */
  private void handleLegacyVehicleKeys(
    User user, float sideways, float forward, boolean jumping
  ) {
    int strafeKey = (int) (sideways / 0.98f);
    int forwardKey = (int) (forward / 0.98f);
    if ((Math.abs(strafeKey) > 1 || Math.abs(forwardKey) > 1) && FaultKicks.INVALID_KEY_INPUT) {
      user.kick("Invalid key input");
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    movementData.legacyVehicleKeyInput = true;
    movementData.legacyVehicleStrafeKey = strafeKey;
    movementData.legacyVehicleForwardKey = forwardKey;
    movementData.clientPressedJump = jumping;
  }

  @PacketSubscription(
    engine = Engine.INTERNAL,
    packetsOut = {
      UPDATE_HEALTH
    }
  )
  public void catchFoodUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    handleFoodUpdate(user, event.getPacket().getIntegers().read(0));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      UPDATE_HEALTH
    }
  )
  public void catchFoodUpdate(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleFoodUpdate(
      UserRepository.userOf(player),
      new WrapperPlayServerUpdateHealth(event).getFood()
    );
  }

  /**
   * Engine independent food update handling; the only packet field it needs is the food level, so
   * this takes the value rather than a view, the same way {@link #handleFinalMovement} does.
   */
  private void handleFoodUpdate(User user, int originalFoodLevel) {
    user.tickFeedback(() -> {
      MetadataBundle meta = user.meta();
      if (originalFoodLevel <= 6) {
        meta.movement().setSprinting(false);
      }
      meta.abilities().foodLevel = originalFoodLevel;
    }, SELF_SYNCHRONIZATION);
  }

  @PacketSubscription(
    packetsOut = {
      WORLD_BORDER,
      INITIALIZE_BORDER,
      SET_BORDER_CENTER,
      SET_BORDER_SIZE,
      SET_BORDER_LERP_SIZE,
    }
  )
  public void sentWorldBorderUpdate(
    User user, PacketEvent event
  ) {
    handleWorldBorderUpdate(
      user, new ProtocolLibWorldBorderView(event.getPlayer(), event.getPacket())
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      WORLD_BORDER,
      INITIALIZE_BORDER,
      SET_BORDER_CENTER,
      SET_BORDER_SIZE,
      SET_BORDER_LERP_SIZE,
    }
  )
  public void sentWorldBorderUpdate(PacketSendEvent event) {
    PacketEventsWorldBorderView view = PacketEventsWorldBorderView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleWorldBorderUpdate(UserRepository.userOf(player), view);
  }

  /** Engine independent world border handling; see {@link WorldBorderView}. */
  private void handleWorldBorderUpdate(User user, WorldBorderView view) {
    user.tickFeedback(() -> {
      MovementMetadata movement = user.meta().movement();
      WorldBorder newBorder = view.updated(movement.border());
      movement.setWorldBorder(newBorder);
    });
  }

  @PacketSubscription(
    packetsOut = USE_BED
  )
  public void playerBedUseCommand(
    User user, BedUseReader reader, PacketEvent event
  ) {
    handleBedUse(
      user,
      new ProtocolLibAnimationView(event.getPlayer(), reader),
      ProtocolLibFeedbackHandle.of(event)
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = USE_BED
  )
  public void playerBedUseCommand(PacketSendEvent event) {
    PacketEventsAnimationView view = PacketEventsAnimationView.of(event);
    if (view == null || !view.isBedUse()) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleBedUse(
      UserRepository.userOf(player), view, PacketEventsFeedbackHandle.of(event)
    );
  }

  /**
   * Engine independent use bed handling; see {@link AnimationView}. The view is not released here
   * for the same reason {@link #handleEntityAction} does not release its own: the ProtocolLib
   * reader is owned by the subscription linker and the PacketEvents view holds nothing.
   */
  private void handleBedUse(User user, AnimationView view, FeedbackHandle handle) {
    if (!view.targetEntityIdIsSameAs(user)) {
      return;
    }
    BlockPosition sleepingBedPosition = view.bedPosition();
    user.packetTickFeedback(handle, () ->
      user.meta().movement().sleepingBedPosition = sleepingBedPosition
    );
  }

  @PacketSubscription(
    packetsOut = {ANIMATION}
  )
  public void playerAnimationCommand(
    User user, AnimationReader reader, PacketEvent event
  ) {
    handleAnimation(
      user,
      new ProtocolLibAnimationView(event.getPlayer(), reader),
      ProtocolLibFeedbackHandle.of(event)
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {ANIMATION}
  )
  public void playerAnimationCommand(PacketSendEvent event) {
    PacketEventsAnimationView view = PacketEventsAnimationView.of(event);
    if (view == null || view.isBedUse()) {
      // Null covers an animation Intave has no constant for; the WAKEUP test below ignores those
      // anyway. The bed use test keeps this subscription off the packet its own twin above owns.
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleAnimation(
      UserRepository.userOf(player), view, PacketEventsFeedbackHandle.of(event)
    );
  }

  /** Engine independent entity animation handling; see {@link AnimationView}. */
  private void handleAnimation(User user, AnimationView view, FeedbackHandle handle) {
    if (!view.targetEntityIdIsSameAs(user)) {
      return;
    }
    if (view.animation() == AnimationReader.Animation.WAKEUP) {
      MovementMetadata movement = user.meta().movement();
      BlockPosition sleepingBedPosition = movement.sleepingBedPosition;
      if (sleepingBedPosition != null) {
        Optional<Position> wakeupPosition = BedWakeupPositionSearch.findStandUpPosition(user, sleepingBedPosition, 0);
        user.packetTickFeedback(handle, () -> {
          wakeupPosition.ifPresent(position -> {
	          movement.setPosition(position);
            movement.setVerifiedLastPosition(position, "Bed wakeup");
          });
          movement.sleepingBedPosition = null;
        });
      }
    }
  }

  /**
   * The one engine specific operation the two sandwiched subscriptions below need: run a callback,
   * put the observed packet back on the wire, run a second callback - the whole trio wrapped in the
   * pair of transaction packets {@code FeedbackSender} sends around it.
   * <p>
   * It exists for the same reason {@link MovementPacketHandle} does. Every other packet operation
   * those handlers perform is covered by an engine neutral view or was read off the packet up
   * front; only the sandwich has a different call shape per engine, because
   * {@code User#doubleTickFeedback} takes a ProtocolLib {@code PacketEvent} in one overload and a
   * PacketEvents {@code PacketSendEvent} in the other. A shared {@link FeedbackHandle} cannot carry
   * it - see that interface for why - so the seam is a lambda supplied by each entry point.
   */
  private interface PacketSandwich {
    void around(EmptyFeedbackCallback before, EmptyFeedbackCallback after);
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = {
      ENTITY_VELOCITY
    }
  )
  public void sentVelocityPacket(
    User user, Player player,
    EntityVelocityReader reader,
    Cancellable cancellable,
    PacketEvent event
  ) {
    handleVelocity(
      user, player,
      new ProtocolLibEntityVelocityView(player, reader, cancellable, event),
      (before, after) -> user.doubleTickFeedback(event, before, after)
    );
  }

  /**
   * PacketEvents twin of {@link #sentVelocityPacket(User, Player, EntityVelocityReader, Cancellable, PacketEvent)}.
   * <p>
   * The reads and rewrites are the {@link PacketEventsEntityVelocityView} the family already
   * carries; what used to block this twin was the sandwich, and no longer does:
   * {@code User#doubleTickFeedback(PacketSendEvent, ...)} re-emits the cancelled packet from its
   * encoded buffer through {@code ProtocolManager#sendPacketSilently}, which writes past every
   * PacketEvents listener. See {@link FeedbackHandle} for the corrected account.
   * <p>
   * The one thing this call site still owes the sandwich is a flushed view: a knockback rewrite
   * lives in the view until {@code release()} pushes it into the wrapper, and only what is in the
   * wrapper reaches the re-emitted bytes. Everything below that - folding the wrapper back into the
   * event buffer and putting the reader index back where the decode left it - is done centrally by
   * {@code PacketEventsBuffers#encodeIntoEventBuffer}, which {@code FeedbackSender} runs before it
   * clones.
   * <p>
   * {@code prioritySlot} is deliberately not repeated here: it selects between ProtocolLib's two
   * listener registration slots and has no meaning for the PacketEvents linkage, which registers
   * one listener per priority.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      ENTITY_VELOCITY
    }
  )
  public void sentVelocityPacket(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsEntityVelocityView view = PacketEventsEntityVelocityView.of(event);
    if (view == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    try {
      handleVelocity(user, player, view, (before, after) -> {
        // A pending knockback rewrite lives in the view until this call moves it into the wrapper,
        // and the sandwich re-emits what the wrapper holds. The finally below repeats it harmlessly.
        view.release();
        user.doubleTickFeedback(event, before, after);
      });
    } finally {
      view.release();
    }
  }

  /**
   * Engine independent outbound velocity handling; see
   * {@link de.jpx3.intave.packet.view.EntityVelocityView}.
   * <p>
   * The knockback is registered as an open ended, tick ambiguous motion update between the two
   * transactions the {@code sandwich} sends, so the physics check accepts a client that applied it
   * on either side of that tick boundary.
   */
  private void handleVelocity(
    User user, Player player,
    EntityVelocityView view,
    PacketSandwich sandwich
  ) {
    if (view.entityId() == player.getEntityId()) {
      Motion motion = view.motion();
      if (IntaveControl.DEBUG_VELOCITY_RECEIVE) {
        player.sendMessage("§a" + MathHelper.formatMotion(motion));
      }
      MetadataBundle meta = user.meta();
      MovementMetadata movementData = meta.movement();
      if (movementData.willReceiveSetbackVelocity && motion.length() < 0.001) {
        movementData.willReceiveSetbackVelocity = false;
        view.setMotion(movementData.setbackOverrideVelocity);
        return;
      }
      /*
        Some players abuse "velocity buffering", giving them the ability to jump up to 40 - 50 blocks (provided they have external help).
        This fix is an attempt to decrease this bugs effectiveness, somewhat working
       */
      int pendingVelocityPackets = movementData.pendingVelocityPackets.get();
      if (pendingVelocityPackets > 1 && user.meta().attack().wasRecentlyAttackedByEntity()) {
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(player)
          .withMessage("is queuing up velocity packets")
          .withDetails("pending: " + pendingVelocityPackets)
          .withVL(0.5)
          .build();

        Modules.violationProcessor().processViolation(violation);
        if (pendingVelocityPackets < 6) {
          motion.setMotionX(motion.motionX() / pendingVelocityPackets);
          motion.setMotionY(Math.min(0, motion.motionY()));
          motion.setMotionZ(motion.motionZ() / pendingVelocityPackets);
          view.setMotion(motion);
        } else if (!view.readOnly()){
          view.setCancelled(true);
          return;
        }
      }

      movementData.pendingVelocityPackets.incrementAndGet();
      movementData.emulationVelocity = motion.copy();
      if (movementData.sneaking) {
        movementData.sneakPatchVelocity = motion.copy();
      }

      Motion finalVelocity = motion.copy();

      AtomicReference<MotionSetUpdate> velocity = new AtomicReference<>(null);
      PhysicsTestRecorder recorder = Modules.physicsTestRecorder();
      AtomicReference<PhysicsTestRecorder.VelocityCapture> recordingVelocity = new AtomicReference<>(null);
      sandwich.around(
        () -> {
          recordingVelocity.set(
            recorder.beginVelocity(user, finalVelocity)
          );
          velocity.set(MotionSetUpdate.openEnded(
            finalVelocity,
            movementData
          ));
          movementData.queueTickAmbiguousUpdate(velocity.get());
        },
        () -> {
          recorder.completeVelocity(recordingVelocity.get());
          MotionSetUpdate myMotionSetUpdate = velocity.get();
          if (myMotionSetUpdate != null) {
            myMotionSetUpdate.canNotRunAfterThisTick(movementData);
          }
          // legacy behavior
          receiveVelocity(player, finalVelocity);
          movementData.pendingVelocityPackets.decrementAndGet();
        }
      );

      movementData.activeTick(RECEIVED_VELOCITY_PACKET);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      EXPLOSION
    }
  )
  public void sentExplosion(
    User user, ExplosionReader reader,
    PacketEvent event
  ) {
    handleExplosion(
      user,
      reader.motion(),
      (before, after) -> user.doubleTickFeedback(event, before, after)
    );
  }

  /**
   * PacketEvents twin of {@link #sentExplosion(User, ExplosionReader, PacketEvent)}.
   * <p>
   * The only field either engine reads is the knockback the explosion applies to this player, and
   * each decodes it for the wire format in front of it: {@code ExplosionReader} switches between
   * the 1.21.3+ optional vector and the three legacy floats,
   * {@code WrapperPlayServerExplosion#getPlayerMotion} owns the same choice on its side. Both are
   * blocks per tick, so the shared handler sees the same number.
   * <p>
   * A missing knockback is handled the way the ProtocolLib body has always handled it: null means
   * "nothing to queue". The reader answers null for an empty optional on 1.21.3+; PacketEvents
   * answers null only where its own wrapper models the field as absent, and a hypothetical zero
   * vector in its place would merely queue a zero motion update, which widens the physics
   * prediction set rather than narrowing it - a leniency difference, never a false positive.
   * <p>
   * What used to block this twin was the sandwich, and no longer does; see {@link FeedbackHandle}
   * for the corrected account. Nothing here rewrites the packet, and the buffer bookkeeping the
   * decode leaves behind is handled centrally by
   * {@code PacketEventsBuffers#encodeIntoEventBuffer}, which {@code FeedbackSender} runs before it
   * clones, so this call site has nothing to flush.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      EXPLOSION
    }
  )
  public void sentExplosion(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    Vector3f knockback = new WrapperPlayServerExplosion(event).getPlayerMotion();
    User user = UserRepository.userOf(player);
    handleExplosion(
      user,
      knockback == null
        ? null
        : new Motion(knockback.getX(), knockback.getY(), knockback.getZ()),
      (before, after) -> user.doubleTickFeedback(event, before, after)
    );
  }

  /**
   * Engine independent explosion handling.
   * <p>
   * Takes the knockback rather than a view, the way {@link #handleFoodUpdate} takes the food level:
   * it is the only field the body reads and neither engine's handler writes the packet back.
   *
   * @param knockback the knockback this explosion applies to the player, or null when the packet
   * carries none.
   */
  private void handleExplosion(User user, Motion knockback, PacketSandwich sandwich) {
    MovementMetadata movement = user.meta().movement();
    if (knockback != null) {
      AtomicReference<MotionAddUpdate> update = new AtomicReference<>(null);
      sandwich.around(
        () -> {
          update.set(MotionAddUpdate.openEnded(
            knockback,
            movement
          ));
          movement.queueTickAmbiguousUpdate(update.get());
        },
        () -> {
          MotionAddUpdate myMotionAddUpdate = update.get();
          if (myMotionAddUpdate != null) {
            myMotionAddUpdate.canNotRunAfterThisTick(movement);
          }
          movement.pendingVelocityPackets.decrementAndGet();
        }
      );
    }
  }

  private void receiveVelocity(Player player, Motion velocity) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();
    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.baseMotionXBeforeVelocity = movementData.baseMotionX;
      movementData.baseMotionYBeforeVelocity = movementData.baseMotionY;
      movementData.baseMotionZBeforeVelocity = movementData.baseMotionZ;
//      movementData.setBaseMotion(velocity);
      movementData.lastVelocity = velocity.copy();
      if (!movementData.willReceiveSetbackVelocity && !movementData.willReceiveFinalSetbackVelocity) {
        movementData.activeTick(EXTERNAL_VELOCITY);
      }
      movementData.willReceiveSetbackVelocity = false;
      movementData.willReceiveFinalSetbackVelocity = false;
      PacketLogging logging = Modules.tracker().packetLogging();
      logging.logSystemMessage(user, () -> "MOTION LOGIC: Velocity base motion set to " + MathHelper.formatMotion(velocity));
    }
    movementData.activeTick(VELOCITY);
  }

  private static final Set<Material> SHULKER_BOX_MATERIALS = MaterialSearch.materialsThatContain("SHULKER_BOX");

  private static final Set<Material> PISTON_MATERIALS = MaterialSearch.materialsThatContain("PISTON");

  /**
   * ProtocolLib only. The blocker is <em>not</em> the packet sandwich, which is now available on
   * both engines - {@link #sentVelocityPacket(PacketSendEvent, Player)} and
   * {@link #sentExplosion(PacketSendEvent, Player)} are twinned through it, and the note in
   * {@link FeedbackHandle} that once called it impossible has been corrected. What blocks this one
   * is the very first field the body reads: the block.
   * <p>
   * Both branches switch on {@code BlockActionReader.blockType()}, an {@link Material}. ProtocolLib
   * produces it by converting the packet's native {@code Block} through its block structure
   * modifier. PacketEvents cannot reach the same value:
   * <ul>
   *   <li>{@code WrapperPlayServerBlockAction#getBlockType()} looks the field up with
   *       {@code WrappedBlockState.getByGlobalId(clientVersion, blockTypeId)}. That is the global
   *       block <em>state</em> palette; the packet's field is a block <em>type</em> registry id.
   *       Two different number spaces, so the answer is a real but unrelated block - the worst
   *       possible failure mode here, because it is silently plausible.</li>
   *   <li>{@code getBlockTypeId()} does hand out the raw type id, and
   *       {@code StateTypes.getById(clientVersion, id)} resolves it into a {@code StateType}. But a
   *       {@code StateType}'s only identity is its namespaced name, and PacketEvents ships no
   *       {@code StateType} to {@link Material} conversion: {@code SpigotConversionUtil} bridges
   *       item types to materials and block <em>states</em> to {@code BlockData}, neither of which
   *       is a block type. Rebuilding the material from the name means guessing Bukkit's enum
   *       spelling per server version - {@code STICKY_PISTON} on modern servers,
   *       {@code PISTON_STICKY_BASE} on the legacy ones this dispatcher still supports - and a miss
   *       returns null rather than failing loudly.</li>
   *   <li>Reading the block out of the world instead is not a substitute: a PacketEvents listener
   *       runs on the connection's netty thread, where a Bukkit world lookup is unsafe, and it
   *       answers with server state rather than with the field the packet carried.</li>
   * </ul>
   * A wrong or null material silently skips {@link #queueShulkerBoxAction} and
   * {@link #queuePistonAction}, dropping the ambiguous motion updates the physics check needs -
   * which is exactly the gap that turns a legitimate piston push into a violation. This becomes
   * portable when a verified block type id to {@link Material} mapping exists, not before.
   */
  @PacketSubscription(
    packetsOut = BLOCK_ACTION
  )
  public void onBlockAction(
    User user, BlockActionReader reader, PacketEvent event
  ) {
    Player player = user.player();
    MovementMetadata movement = user.meta().movement();
    Material material = reader.blockType();
    if (SHULKER_BOX_MATERIALS.contains(material)) {
      BlockPosition blockPosition = reader.blockPosition();
      World world = player.getWorld();
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, blockPosition.toLocation(world));
      Direction facing = variant.enumProperty(Direction.class, "facing");
      int openCount = reader.data();
      if (openCount != 0 && openCount != 1) {
        return;
      }
      boolean opening = openCount == 1;
      if (user.protocolVersion() >= VER_1_11) {
        queueShulkerBoxAction(user, event, blockPosition, facing, opening);
      }
    } else if (PISTON_MATERIALS.contains(material)) {
      BlockPosition blockPosition = reader.blockPosition();
      int facingIndex = reader.data() & 7;
      if (facingIndex > 5) {
        return;
      }
      Direction facing = Direction.getFront(facingIndex);
      int action = reader.action();
      if (action != 0 && action != 1) {
        return;
      }

      boolean extending = action == 0;
      if (user.protocolVersion() >= VER_1_9) {
        queuePistonAction(user, event, blockPosition, extending, facing);
      }

      Modules.feedback().synchronize(player, nothing -> {
        // First off, check if the player is even affected by this
        RawVector3d directionVec = facing.directionVector();
        BoundingBox pistonCollisionArea = new BoundingBox(0, 0, 0, 1.1f, 1.1f, 1.1f);
        int expectedPistonX = (int) directionVec.x() + blockPosition.getX();
        int expectedPistonY = (int) directionVec.y() + blockPosition.getY();
        int expectedPistonZ = (int) directionVec.z() + blockPosition.getZ();
        BoundingBox expandingBlockArea = pistonCollisionArea.offset(expectedPistonX, expectedPistonY, expectedPistonZ);
        boolean playerAffected = expandingBlockArea.intersectsWith(user.meta().movement().boundingBox());

        // Only do something if the player is actually affected
        if (playerAffected) {
          // Might seem like a high value, doesn't it?
          // Well this is fine as we constantly check if the player is inside the critical area
          // where he would get false-mitigated
          movement.pistonMotionToleranceRemaining = 10;
          movement.pistonCollisionArea = expandingBlockArea;

          float xOffset = (float) Math.abs(expectedPistonX - user.meta().movement().positionX);
          float yOffsetBottom = (float) Math.abs((expectedPistonY + 1) - user.meta().movement().boundingBox().minY);
          float yOffsetTop = (float) Math.abs(expectedPistonY - user.meta().movement().boundingBox().maxY);
          float zOffset = (float) Math.abs(expectedPistonZ - user.meta().movement().positionZ);
          switch (facing.axis()) {
            case X_AXIS: {
              // Magical hack to get the proper bounding box factor
              float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.x());
              movement.pistonHorizontalAllowance = xOffset + horizontalBoundingBoxFactor + 0.05f;
              break;
            }
            case Z_AXIS: {
              // Magical hack to get the proper bounding box factor
              float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.z());
              movement.pistonHorizontalAllowance = zOffset + horizontalBoundingBoxFactor + 0.05f;
              break;
            }
            case Y_AXIS: {
              // Cannot be done with directional vectors unfortunately :(
              switch (facing) {
                case UP:
                  movement.pistonVerticalAllowance = yOffsetBottom + 0.05f;
                  break;
                case DOWN:
                  movement.pistonVerticalAllowance = yOffsetTop + 0.05f;
                  break;
              }
              break;
            }
          }
        }
      });
    }
  }

  private void queueShulkerBoxAction(
    User user,
    PacketEvent event,
    BlockPosition position,
    Direction direction,
    boolean opening
  ) {
    MovementMetadata movement = user.meta().movement();
    AtomicReference<ShulkerBoxActionUpdate> update = new AtomicReference<>(null);
    AtomicLong recordingStart = new AtomicLong(-1);
    user.doubleTickFeedback(event,
      () -> {
        update.set(ShulkerBoxActionUpdate.openEnded(
          position, direction, opening, movement
        ));
        movement.queueTickAmbiguousUpdate(update.get());
        applyShulkerTolerance(movement, position, direction);

        MovementRecording recording = Modules.physicsTestRecorder().recordingSessionOf(user);
        if (recording != null) {
          recordingStart.set(recording.ticks());
        }
      },
      () -> {
        ShulkerBoxActionUpdate shulkerAction = update.get();
        if (shulkerAction != null) {
          shulkerAction.canNotRunAfterThisTick(movement);
        }
        MovementRecording recording = Modules.physicsTestRecorder().recordingSessionOf(user);
        long start = recordingStart.get();
        if (recording != null && start >= 0) {
          recording.insertAction(new ShulkerBoxAction(
            position,
            direction,
            opening,
            TickRange.betweenInclusive(start, recording.ticks())
          ));
        }
      }
    );
  }

  private static void applyShulkerTolerance(
    MovementMetadata movement,
    BlockPosition position,
    Direction direction
  ) {
    double distanceToShulker = MathHelper.distanceOf(
      movement.positionX, movement.positionY, movement.positionZ,
      position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5
    );
    if (distanceToShulker > 4) {
      return;
    }

    movement.lowestShulkerY = Math.min(movement.lowestShulkerY, position.getY());
    movement.highestShulkerY = Math.max(movement.highestShulkerY, position.getY() + 1);
    switch (direction.axis()) {
      case X_AXIS:
        movement.shulkerXToleranceRemaining = 20;
        break;
      case Y_AXIS:
        movement.shulkerYToleranceRemaining = 20;
        break;
      case Z_AXIS:
        movement.shulkerZToleranceRemaining = 20;
        break;
    }
  }

  private void queuePistonAction(
    User user,
    PacketEvent event,
    BlockPosition pistonPosition,
    boolean extending,
    Direction facing
  ) {
    long now = System.nanoTime();
    PistonSnapshot snapshot = pistonSnapshots.get(new PistonSnapshotKey(
      user.player().getWorld().getUID(), pistonPosition, extending
    ));
    if (snapshot == null || snapshot.expired(now) || snapshot.slimeSources.isEmpty()) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    Direction movementDirection = extending ? facing : facing.getOpposite();
    AtomicReference<PistonActionUpdate> update = new AtomicReference<>(null);
    AtomicLong recordingStart = new AtomicLong(-1);
    user.doubleTickFeedback(event,
      () -> {
        update.set(PistonActionUpdate.openEnded(
          movementDirection, snapshot.slimeSources, movement
        ));
        movement.queueTickAmbiguousUpdate(update.get());
        MovementRecording recording = Modules.physicsTestRecorder().recordingSessionOf(user);
        if (recording != null) {
          recordingStart.set(recording.ticks());
        }
      },
      () -> {
        PistonActionUpdate pistonAction = update.get();
        if (pistonAction != null) {
          pistonAction.canNotRunAfterThisTick(movement);
        }
        MovementRecording recording = Modules.physicsTestRecorder().recordingSessionOf(user);
        long start = recordingStart.get();
        if (recording != null && start >= 0) {
          recording.insertAction(new PistonSlimeAction(
            movementDirection,
            snapshot.slimeSources,
            TickRange.betweenInclusive(start, recording.ticks())
          ));
        }
      }
    );
  }

  private static final class PistonSnapshotKey {
    private final UUID worldId;
    private final BlockPosition pistonPosition;
    private final boolean extending;

    private PistonSnapshotKey(UUID worldId, BlockPosition pistonPosition, boolean extending) {
      this.worldId = worldId;
      this.pistonPosition = pistonPosition;
      this.extending = extending;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof PistonSnapshotKey)) {
        return false;
      }
      PistonSnapshotKey other = (PistonSnapshotKey) object;
      return extending == other.extending
        && worldId.equals(other.worldId)
        && pistonPosition.equals(other.pistonPosition);
    }

    @Override
    public int hashCode() {
      return Objects.hash(worldId, pistonPosition, extending);
    }
  }

  private static final class PistonSnapshot {
    private final List<BlockPosition> slimeSources;
    private final long capturedAt;

    private PistonSnapshot(List<BlockPosition> slimeSources, long capturedAt) {
      this.slimeSources = Collections.unmodifiableList(new ArrayList<>(slimeSources));
      this.capturedAt = capturedAt;
    }

    private boolean expired(long now) {
      return now - capturedAt > PISTON_SNAPSHOT_RETENTION_NANOS;
    }
  }

  @PacketSubscription(
    packetsIn = {
      USE_ITEM, BLOCK_DIG
    }
  )
  public void receiveUseItem(
    User user, BlockPositionReader reader
  ) {
    handleUseItem(user);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      USE_ITEM, BLOCK_DIG
    }
  )
  public void receiveUseItem(Player player) {
    if (player == null) {
      return;
    }
    handleUseItem(UserRepository.userOf(player));
  }

  /** Engine independent handling; nothing is read off the packet, only the player's inventory. */
  private void handleUseItem(User user) {
    Material heldType = user.meta().inventory().heldItemType();
    Material offhandType = user.meta().inventory().offhandItemType();
    if (heldType != Material.AIR || offhandType != Material.AIR) {
      user.meta().movement().awaitClickMovementSkip = true;
      if (DEBUG_MOVEMENT_IGNORE) {
//        Synchronizer.synchronize(() -> {
//          user.player().sendMessage("Item Usage Tick");
//        });
//        System.out.println("[Intave] Item Usage Tick");
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityActionPacket(
    User user, PlayerActionReader reader, Cancellable cancelable
  ) {
    handleEntityAction(user, new ProtocolLibPlayerActionView(user.player(), reader, cancelable));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityActionPacket(PacketReceiveEvent event) {
    PacketEventsPlayerActionView view = PacketEventsPlayerActionView.of(event);
    if (view == null) {
      // Not an entity action packet, or an action Intave has no equivalent for; the switch below
      // ignores those anyway.
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleEntityAction(UserRepository.userOf(player), view);
  }

  /**
   * Engine independent entity action handling; see {@link PlayerActionView}. The view is not
   * released here: on the ProtocolLib path the reader is owned by the subscription linker, and on
   * the PacketEvents path there is nothing to release.
   */
  private void handleEntityAction(User user, PlayerActionView view) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocol = meta.protocol();
    switch (view.playerAction()) {
      case START_SPRINTING:
        if (allowSprinting(user)) {
          movementData.setSprinting(true);
          if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
            user.player().sendMessage(ChatColor.WHITE + "Start sprinting " + meta.attack().attackPastTicks);
          }
        }
        break;
      case STOP_SPRINTING:
        int ticksSprinting = movementData.ticks(SPRINTING);
        movementData.setSprinting(false);
        if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
          user.player().sendMessage(ChatColor.BLACK + "Stop sprinting after " + ticksSprinting + " " + meta.attack().attackPastTicks);
        }
        break;
      case PRESS_SHIFT_KEY:
      case START_SNEAKING:
        startSneak(user, view);
        break;
      case RELEASE_SHIFT_KEY:
      case STOP_SNEAKING:
        stopSneak(user);
        break;
      case START_FALL_FLYING:
        if (movementData.hasElytraEquipped() && protocol.canUseElytra()) {
          if (protocol.serversideElytra()) {
            movementData.gliding = true;
            if (IntaveControl.DEBUG_ELYTRA) {
              user.player().sendMessage(ChatColor.GREEN + "Activated elytra flying (START_FALL_FLYING)");
            }
          }
        }
        break;
    }
  }

  /**
   * ProtocolLib only on PacketEvents 2.4.0: the exact getter this needs does not exist anywhere in
   * that jar.
   * <p>
   * What this reads is the sneak bit (mask {@code 0x20}) of the input bitmask that 1.21.2 moved
   * into {@code net.minecraft.world.entity.player.Input}, decoded here through
   * {@link InputConverter}. On PacketEvents 2.4.0 the only class that can carry a steer vehicle
   * payload is {@code WrapperPlayClientSteerVehicle}, and its complete getter set is
   * {@code getSideways()}, {@code getForward()}, {@code getFlags()}, {@code isJump()} and
   * {@code isUnmount()} - the missing getter is an {@code isShift()}, because the pre 1.21.2 wire
   * format that wrapper decodes has no sneak bit in it at all ({@code getFlags()} holds only jump
   * {@code 0x1} and unmount {@code 0x2}). {@code WrapperPlayClientPlayerInput} is not in the 2.4.0
   * jar, and {@code PacketType.Play.Client} there ends at {@code STEER_VEHICLE} /
   * {@code STEER_BOAT} with no {@code PLAYER_INPUT} constant. A 2.4.0 twin would therefore be dark
   * across precisely the range this check exists for: clients on 1.21.2 and above (protocol 768+),
   * which is the only range in which the sneak bit travels inside this packet. Below that range
   * sneaking arrives as {@code ENTITY_ACTION} instead and this method does not run.
   * <p>
   * Concrete unblock condition, both halves required. First, the runtime floor has to rise to the
   * build's {@code compileOnly} target, PacketEvents 2.13.0: that version does ship
   * {@code WrapperPlayClientPlayerInput} with {@code isShift()} - the exact getter named above -
   * next to {@code isForward()}, {@code isBackward()}, {@code isLeft()}, {@code isRight()},
   * {@code isJump()} and {@code isSprint()}, plus a {@code PacketType.Play.Client.PLAYER_INPUT}
   * constant. Second, {@link de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper} needs a
   * separate entry for it: it maps Intave's {@code STEER_VEHICLE} to the candidates
   * {@code "STEER_VEHICLE"} then {@code "PLAYER_INPUT"} and resolves first match wins, so even on
   * 2.13.0 it binds {@code STEER_VEHICLE} alone and a {@code PLAYER_INPUT} packet would never be
   * delivered to a twin. Until both hold, the only way to write this against 2.4.0 would be to
   * guess a bit position out of a byte that provably does not contain it, which in an anticheat is
   * a fabricated field read and a false ban.
   */
  @PacketSubscription(
    packetsIn = {
      STEER_VEHICLE
    }
  )
  public void onInputs(
    PacketEvent event
  ) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (!user.meta().protocol().sneakAsVehicleSteer()) {
      return;
    }
    MovementMetadata movement = user.meta().movement();
    StructureModifier<Input> inputs = packet.getModifier().withType(
      InputConverter.inputClass, InputConverter.INSTANCE
    );
    Input input = inputs.read(0);
    boolean sneaking = input.sneakKey();
    if (sneaking && !movement.sneaking) {
      startSneak(user, event);
    } else if (!sneaking && movement.sneaking) {
      stopSneak(user);
    }
  }

  private void startSneak(User user, Cancellable cancelable) {
    applySneakStart(user, () -> cancelable.setCancelled(true));
  }

  private void startSneak(User user, PlayerActionView view) {
    applySneakStart(user, () -> view.setCancelled(true));
  }

  /** Engine independent sneak start; cancelling the packet is the only thing it needs it for. */
  private void applySneakStart(User user, Runnable cancelPacket) {
    PunishmentMetadata punishmentData = user.meta().punishment();
    MovementMetadata movementData = user.meta().movement();
    if (System.currentTimeMillis() - punishmentData.timeLastSneakToggleCancel < 2000) {
      cancelPacket.run();
    }
    movementData.activeTick(VEHICLE_EXIT);
    if (movementData.isInVehicle()) {
      movementData.dismountRidingEntity("Sneak exit");
//      movementData.queueTickAmbiguousUpdate(
//        SneakingUpdate.sneaking(false, movementData)
//      );
      movementData.setSneaking(false);
    } else {
//      movementData.queueTickAmbiguousUpdate(
//        SneakingUpdate.sneaking(true, movementData)
//      );
      movementData.setSneaking(true);
    }
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.player().sendMessage(ChatColor.GREEN + "Start sneaking " + movementData.sneaking);
    }
  }

  private void stopSneak(User user) {
    MovementMetadata movementData = user.meta().movement();
//    movementData.queueTickAmbiguousUpdate(
//      SneakingUpdate.sneaking(false, movementData)
//    );
    movementData.setSneaking(false);
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.player().sendMessage(ChatColor.RED + "Stop sneaking after " + movementData.ticks(SNEAKING));
    }
  }

  private boolean allowSprinting(User user) {
    return !user.meta().inventory().inventoryOpen();
  }
}
