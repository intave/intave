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
import de.jpx3.intave.block.special.BedWakeupPositionSearch;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.update.PistonActionUpdate;
import de.jpx3.intave.check.movement.physics.update.ShulkerBoxActionUpdate;
import de.jpx3.intave.check.movement.physics.update.MotionUpdate;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
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
import de.jpx3.intave.packet.plib.PacketSender;
import de.jpx3.intave.packet.plib.converter.InputConverter;
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
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.user.MessageChannel.DEBUG_TELEPORT;
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
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    meta.movement().replaceRecoveryWithExternalTeleport();
    meta.movement().discardVelocityBefore(Long.MAX_VALUE);
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
    PacketLogging logging = Modules.tracker().packetLogging();

    Player player = event.getPlayer();
    if (player.isDead() || event.isCancelled()) {
      logging.logSystemMessage(UserRepository.userOf(player), () -> "MOVEMENT IGNORED: Player is dead or event is cancelled");
      return;
    }

    PacketContainer packet = event.getPacket();
    PlayerMoveReader reader = PacketReaders.readerOf(packet);

    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    AttackMetadata attack = meta.attack();
    MovementMetadata movement = meta.movement();
    InventoryMetadata inventoryData = meta.inventory();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ConnectionMetadata connectionData = meta.connection();
    ProtocolMetadata protocol = meta.protocol();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
	  boolean hasMovement = reader.hasMovement();
    boolean hasRotation = reader.hasRotation();

    movement.recoverPendingTeleport();
    if (!mustRejectMovement(movement) && movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movement.setRotation(reader.yaw(), reader.pitch());
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Vehicle rotation only");
      reader.release();
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
    if (movement.isInRidingVehicle() && !vehicleMove && clientVehicleMovement) {
      movement.dismountRidingEntity("Client vehicle movement");
    }

    if (movement.isInRidingVehicle() && !vehicleMove && hasMovement) {
      if (movement.invalidVehiclePositionTicks++ > 10) {
        movement.dismountRidingEntity("Lower client vehicle movement");
      }
    }

    if (reader.anyNaNOrInfiniteValue() && FaultKicks.POSITION_FAULTS) {
      user.kick("NaN/infinite in server-bound movement packet");
      return;
    }

    boolean hasBypass = user.trustFactor().atLeast(TrustFactor.BYPASS);
    if (hasMovement || movement.isInVehicle() || movement.inRespawnScreen) {
      movement.lastPositionUpdate = 0;
    } else if (++movement.lastPositionUpdate > 20 && FaultKicks.MISSING_POSITION_UPDATE && !user.justJoined() && !hasBypass) {
      user.kick("Missing position update " + movement.vehicle());
    }

    // fix only works for 1.8
    if (movement.sprinting && movement.isSneaking() && movement.lastSneaking && !protocol.combatUpdate() && movement.acceptSneakFaults && FaultKicks.INVALID_PLAYER_ACTION && !user.justJoined() && !hasBypass) {
      movement.acceptSneakFaults = false;
      user.refreshSprintState(unused -> {
        movement.sprintSneakFaults++;
        movement.acceptSneakFaults = true;
      });
      if (movement.sprintSneakFaults > 1) {
        user.kick("Repeated player action faults");
      }
    }

    if (reader.couldBeTeleport() && teleportController.receiveMove(event, reader)) {
      movement.isTeleportConfirmationPacket = true;
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Move was teleport");
      reader.release();
      return;
    }

    boolean processThisPacket = teleportController.processMovementPackets(user);
    boolean forwardToServer = teleportController.forwardMovementPackets(user);

    if (!processThisPacket) {
      // Confirmation matching above must remain reachable while movement is blocked.
      // Keep retries alive here as well when feedback enforcement is disabled.
      if (--movement.teleportResendCountdown <= 0) {
        teleportController.onResendTimeout(user);
      }
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      }
      if (user.receives(DEBUG_TELEPORT)) {
        ActionBar.sendActionBar(user.player(), "Ignoring movement packet. Q: " + movement.pendingTeleports.get().size());
      }
      event.setCancelled(true);
      movement.dropPostTickMotionProcessing = true;
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      reader.release();
      return;
    }

    // see MultiPlayerGameMode#useItem
    if (protocol.useItemMovementPacket()
      && packet.getType() == PacketType.Play.Client.POSITION_LOOK
    ) {
      Position position = reader.position();
      if (position == null) {
        user.kick("Missing position in movement packet");
        return;
      }
      double distance = position.distance(movement.verifiedLastPosition());
      if (distance < 0.00001) {
        movement.dropPostTickMotionProcessing = true;
        logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Click movement ignore distance: " + distance);
        if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
          event.setCancelled(true);
        } else {
          // to fix nofall bypasses
          reader.setPosition(movement.verifiedLastPosition());
        }
        reader.release();
        return;
      }
    }
    movement.awaitClickMovementSkip = false;

    if (user.receives(MessageChannel.DEBUG_POSITION)) {
      ActionBar.sendActionBar(player, "intave:" + formatDouble(movement.positionY, 2) + " server:" + formatDouble(player.getLocation().getY(), 2));
    }

    connectionData.receiveMovement();
    movement.updateMovement(
      reader.positionX(), reader.positionY(), reader.positionZ(),
      reader.yaw(), reader.pitch(),
      hasMovement, hasRotation
    );
    inventoryData.updateSlotSwitch();

    if (hasMovement) {
      logging.logSystemMessage(user, () -> "MOTION LOGIC: Received motion: " + movement.sentOffsetMotion());
    }

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

    if (!forwardToServer) {
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport forward ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      event.setCancelled(true);
      // Server delivery is withheld, but this is still an ordinary client tick.
      // Its post-tick motion and position are the base for a relative teleport.
      movement.movementWithheldForTeleport = true;

      int teleportResendCountdown = movement.teleportResendCountdown;
      movement.teleportResendCountdown = Math.max(0, teleportResendCountdown - 1);
      if (teleportResendCountdown <= 0) {
        teleportController.onResendTimeout(user);
      }
    }

    double distance = movement.verifiedLastPosition().distance(movement.position());

    if (distance > 50) {
      movement.invalidMovement = true;
      movement.recoverPendingTeleport();
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Distance movement ignore: " + distance);
      }
      logging.logSystemMessage(user, () -> "MOVEMENT REJECTED: Distance over limit: " + distance);
      movement.dropPostTickMotionProcessing = true;
      event.setCancelled(true);

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
      reader.release();
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

//    if (violationLevelData.isInActiveTeleportBundle) {
//      if (DEBUG_MOVEMENT_IGNORE) {
//        System.out.println("[Intave] Teleport bundle movement ignore");
//      }
//      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport bundle movement ignore");
//      movement.dropPostTickMotionProcessing = true;
//      event.setCancelled(true);
//      reader.release();
//      return;
//    }

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
      reader.release();
      return;
    }

    if (!movement.isTeleportConfirmationPacket) {
      timerCheck.receiveMovement(event);
      movement.recoverPendingTeleport();
      if (movement.inRecovery) {
        event.setCancelled(true);
        reader.release();
        return;
      }
      if (interactionRaytraceCheck.receiveMovement(event)) {
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

      boolean clientOnGround = vehicleMove ? player.isOnGround() : reader.onGround();
      boolean collidedWithBoat = movement.collidedWithBoat();

      if (movement.onGround && !clientOnGround && movement.step) {
        movement.onGround = false;
      }

      if (collidedWithBoat) {
        movement.onGround = clientOnGround;
      }

      attack.updatePerfectRotation();

      updatePotionEffects(user);
      movement.canResetMotion = false;
    } else {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Basic reset movement ignore");
      }
      movement.canResetMotion = true;
    }

    // Invalid movement must never reach the server, even before a correction is sent.
    movement.recoverPendingTeleport();
    if (mustRejectMovement(movement)) {
      movement.dropPostTickMotionProcessing = true;
      event.setCancelled(true);
    }

    reader.release();
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
    if (user.receives(MessageChannel.DEBUG_ITEM_USE)) {
      user.sendMessage(IntavePlugin.prefix() + "Applying item usage reset as requested");
    }
    Player player = user.player();
    ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
    InventoryMetadata inventory = user.meta().inventory();
    if (ItemProperties.isBow(inventory.releaseItemType) || ItemProperties.isBow(inventory.activeItemType())) {
      inventory.blockNextArrow = true;
      inventory.lastBlockArrowRequest = System.currentTimeMillis();
      if (user.receives(MessageChannel.DEBUG_ITEM_USE)) {
        user.sendMessage(IntavePlugin.prefix() + "Requesting arrow block as player is also holding a bow on item usage reset");
      }
    }
    inventory.lastFoodConsumptionBlockRequest = System.currentTimeMillis();
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.BLOCK_DIG);
    packet.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(0, 0, 0));
    packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
    BlockDigReader.setAction(packet, PlayerDigAction.RELEASE_USE_ITEM);
    user.ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
    updatePlayerHandItem(player);
    Synchronizer.synchronize(user, player::updateInventory);
    if (IntaveControl.DEBUG_ITEM_USAGE) {
      user.sendMessage(ChatColor.DARK_PURPLE + "Release item");
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
    Player player = user.player();
    MetadataBundle meta = user.meta();
    AttackMetadata attack = meta.attack();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilities = meta.abilities();
    InventoryMetadata inventory = meta.inventory();

    boolean vehicleMove = reader.isVehicleMove();
	  boolean hasMovement = reader.hasMovement();
    boolean hasRotation = reader.hasRotation();
    boolean claimsToBeOnGround = vehicleMove ? player.isOnGround() : reader.onGround();

    if (player.isDead()) {
      return;
    }

    if (movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movement.setVerifiedLastPosition(
        movement.position(),
        "Vehicle rotation only, blind copy from current"
      );
      return;
    }

    if (!vehicleMove && !movement.isSleeping() && !movement.invalidMovement && !movement.dropPostTickMotionProcessing) {
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
          reader.setOnGround(movement.onGround);
        }
      }
    }

    if (canCommitMovement(movement, cancellable.isCancelled())) {
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
      int strafeKey = (int) (packet.getFloat().read(0) / 0.98f);
      int forwardKey = (int) (packet.getFloat().read(1) / 0.98f);
      if ((Math.abs(strafeKey) > 1 || Math.abs(forwardKey) > 1) && FaultKicks.INVALID_KEY_INPUT) {
        user.kick("Invalid key input");
        return;
      }
      Boolean jumping = packet.getBooleans().read(0);
      movementData.legacyVehicleKeyInput = true;
      movementData.legacyVehicleStrafeKey = strafeKey;
      movementData.legacyVehicleForwardKey = forwardKey;
      movementData.clientPressedJump = jumping;
    }
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
    user.tickFeedback(() -> {
	    try (
        WorldBorderReader reader = PacketReaders.readerOf(event.getPacket())
      ) {
        MovementMetadata movement = user.meta().movement();
        WorldBorder newBorder = reader.updated(movement.border());
        movement.setWorldBorder(newBorder);
      }
    });
  }

  @PacketSubscription(
    packetsOut = USE_BED
  )
  public void playerBedUseCommand(
    User user, BedUseReader reader, PacketEvent event
  ) {
    if (!reader.targetEntityIdIsSameAs(user)) {
      return;
    }
    de.jpx3.intave.share.BlockPosition sleepingBedPosition = reader.bedPosition();
    user.packetTickFeedback(event, () ->
      user.meta().movement().sleepingBedPosition = sleepingBedPosition
    );
  }

  @PacketSubscription(
    packetsOut = {ANIMATION}
  )
  public void playerAnimationCommand(
    User user, AnimationReader reader, PacketEvent event
  ) {
    if (!reader.targetEntityIdIsSameAs(user)) {
      return;
    }
    if (reader.animation() == EntityAnimation.WAKEUP) {
      MovementMetadata movement = user.meta().movement();
      BlockPosition sleepingBedPosition = movement.sleepingBedPosition;
      if (sleepingBedPosition != null) {
        Optional<Position> wakeupPosition = BedWakeupPositionSearch.findStandUpPosition(user, sleepingBedPosition, 0);
        user.packetTickFeedback(event, () -> {
          wakeupPosition.ifPresent(position -> {
	          movement.setPosition(position);
            movement.setVerifiedLastPosition(position, "Bed wakeup");
          });
          movement.sleepingBedPosition = null;
        });
      }
    }
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
    if (!event.isFiltered()) {
      return;
    }
    if (reader.entityId() == player.getEntityId()) {
      Motion motion = reader.motion();
      if (IntaveControl.DEBUG_VELOCITY_RECEIVE) {
        user.sendMessage("§a" + MathHelper.formatMotion(motion));
      }
      MetadataBundle meta = user.meta();
      MovementMetadata movementData = meta.movement();
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
          reader.setMotion(motion);
        } else if (!event.isReadOnly()){
          cancellable.setCancelled(true);
          return;
        }
      }

      movementData.pendingVelocityPackets.incrementAndGet();
      if (movementData.sneaking) {
        movementData.sneakPatchVelocity = motion.copy();
      }

      Motion finalVelocity = motion.copy();

      movementData.teleportLock.lock();
      try {
        MotionUpdate velocity = MotionUpdate.pendingReplacement(finalVelocity);
        movementData.trackVelocity(velocity);
        PhysicsTestRecorder recorder = Modules.physicsTestRecorder();
        AtomicReference<PhysicsTestRecorder.VelocityCapture> recordingVelocity = new AtomicReference<>(null);
        user.doubleTickFeedback(event,
          () -> {
            recordingVelocity.set(
              recorder.beginVelocity(user, finalVelocity)
            );
            velocity.activate(movementData);
          },
          () -> {
            recorder.completeVelocity(recordingVelocity.get());
            velocity.canNotRunAfterThisTick(movementData);
            movementData.pendingVelocityPackets.decrementAndGet();
          }
        );
      } finally {
        movementData.teleportLock.unlock();
      }

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
    MovementMetadata movement = user.meta().movement();
    Motion knockback = reader.motion();
    if (knockback != null) {
      movement.teleportLock.lock();
      try {
        MotionUpdate update = MotionUpdate.pendingAddition(knockback);
        movement.trackVelocity(update);
        user.doubleTickFeedback(event,
          () -> {
            update.activate(movement);
          },
          () -> {
            update.canNotRunAfterThisTick(movement);
            movement.pendingVelocityPackets.decrementAndGet();
          }
        );
      } finally {
        movement.teleportLock.unlock();
      }
    }
  }

  private static final Set<Material> SHULKER_BOX_MATERIALS = MaterialSearch.materialsThatContain("SHULKER_BOX");

  private static final Set<Material> PISTON_MATERIALS = MaterialSearch.materialsThatContain("PISTON");
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
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocol = meta.protocol();
    switch (reader.playerAction()) {
      case START_SPRINTING:
        if (allowSprinting(user)) {
          movementData.setSprinting(true);
          if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
            user.sendMessage(ChatColor.WHITE + "Start sprinting " + meta.attack().attackPastTicks);
          }
        }
        break;
      case STOP_SPRINTING:
        int ticksSprinting = movementData.ticks(SPRINTING);
        movementData.setSprinting(false);
        if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
          user.sendMessage(ChatColor.BLACK + "Stop sprinting after " + ticksSprinting + " " + meta.attack().attackPastTicks);
        }
        break;
      case PRESS_SHIFT_KEY:
      case START_SNEAKING:
        startSneak(user, cancelable);
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
              user.sendMessage(ChatColor.GREEN + "Activated elytra flying (START_FALL_FLYING)");
            }
          }
        }
        break;
    }
  }

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
    PunishmentMetadata punishmentData = user.meta().punishment();
    MovementMetadata movementData = user.meta().movement();
    if (System.currentTimeMillis() - punishmentData.timeLastSneakToggleCancel < 2000) {
      cancelable.setCancelled(true);
    }
    movementData.activeTick(VEHICLE_EXIT);
    if (movementData.isInVehicle()) {
      movementData.dismountRidingEntity("Sneak exit");
      movementData.setSneaking(false);
    } else {
      movementData.setSneaking(true);
    }
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.sendMessage(ChatColor.GREEN + "Start sneaking " + movementData.sneaking);
    }
  }

  private void stopSneak(User user) {
    MovementMetadata movementData = user.meta().movement();
    movementData.setSneaking(false);
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.sendMessage(ChatColor.RED + "Stop sneaking after " + movementData.ticks(SNEAKING));
    }
  }

  private boolean allowSprinting(User user) {
    return !user.meta().inventory().inventoryOpen();
  }

  static boolean mustRejectMovement(MovementMetadata movement) {
    return movement.inRecovery || movement.invalidMovement;
  }

  static boolean canCommitMovement(MovementMetadata movement, boolean cancelled) {
    return !mustRejectMovement(movement)
      && !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing
      && (!cancelled || movement.movementWithheldForTeleport);
  }

  public TeleportController teleports() {
    return teleportController;
  }
}
