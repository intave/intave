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
import de.jpx3.intave.block.collision.custom.BedWakeupPositionSearch;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.update.MotionAddUpdate;
import de.jpx3.intave.check.movement.physics.update.MotionSetUpdate;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
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
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_16;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class MovementDispatcher extends Module {
  private Physics physicsCheck;
  private TeleportController teleportController;
  private InteractionRaytrace interactionRaytraceCheck;
  private Timer timerCheck;

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
    PlayerTeleportEvent.TeleportCause cause = event.getCause();
    if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || event.isCancelled()) {
      return;
    }
    Location fromLocation = event.getFrom();
    Location toLocation = event.getTo();
    double teleportDistance = toLocation.getWorld() != player.getWorld() ? Double.MAX_VALUE : toLocation.distance(fromLocation);
    if (toLocation.getWorld() != player.getWorld() || teleportDistance > 8) {
      Location fixed = fixLocation(user, toLocation);
      Synchronizer.synchronize(() -> {
        player.teleport(fixed, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
      });
    }
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
    respawn.setRespawnLocation(fixLocation(user, respawnLocation));
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
      return location;
    }

    MovementMetadata movement = user.meta().movement();
    int baseShifts = BASE_SHIFTS;
    Location fixedLocation = location.clone();
    World world = location.getWorld();

    // A: move out of existing blocks
    BoundingBox bb = BoundingBox.fromPosition(user, movement, fixedLocation);
    while (fixedLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && baseShifts-- > 0 && Collision.unsafePresent(world, user.player(), bb) && Collision.unsafeNonePresent(world, user.player(), bb.offset(0, BASE_SHIFTS * 0.1, 0))) {
      fixedLocation.add(0, 0.101, 0);
      bb = BoundingBox.fromPosition(user, movement, fixedLocation).grow(0.1);
    }

    // B: if clear of blocks, move up 0.55 block
    baseShifts = 5;
    bb = BoundingBox.fromPosition(user, movement, fixedLocation);
    while (fixedLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && baseShifts-- > 0 && Collision.unsafeNonePresent(world, user.player(), bb)) {
      fixedLocation.add(0, 0.101, 0);
      bb = BoundingBox.fromPosition(user, movement, fixedLocation).grow(0.1).expand(0.5, 0.45, 0.5);
    }
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
    Player player = event.getPlayer();
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
      if (protocol.protocolVersion() >= VER_1_16) {
        user.refreshSprintState();
      }
      Synchronizer.synchronize(inventory::releaseItemNextTick);
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
    MovementMetadata movement = meta.movement();
    AttackMetadata attackData = meta.attack();
    InventoryMetadata inventoryData = meta.inventory();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ConnectionMetadata connectionData = meta.connection();
    ProtocolMetadata protocol = meta.protocol();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
	  boolean hasMovement = reader.hasMovement();
    boolean hasRotation = reader.hasRotation();

    if (movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
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
    if (movement.isInRidingVehicle() && !vehicleMove && clientVehicleMovement && !movement.awaitTeleport) {
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
      && packet.getType() == PacketType.Play.Client.POSITION_LOOK
    ) {
      double positionX = reader.positionX();
      double positionY = reader.positionY();
      double positionZ = reader.positionZ();
      double motionX = positionX - movement.verifiedLastPositionX;
      double motionY = positionY - movement.verifiedLastPositionY;
      double motionZ = positionZ - movement.verifiedLastPositionZ;
      double distance = MathHelper.hypot3d(motionX, motionY, motionZ);

      if (distance < 0.00001) {
        movement.dropPostTickMotionProcessing = true;
        Float yaw = packet.getFloat().read(0);
        Float pitch = packet.getFloat().read(1);
        if (DEBUG_MOVEMENT_IGNORE) {
          double yawDifference = MathHelper.noAbsDistanceInDegrees(movement.lastRotationYaw, yaw);
          double pitchDifference = MathHelper.noAbsDistanceInDegrees(movement.lastRotationPitch, pitch);
          System.out.println("[Intave] Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
        }
        logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Click movement ignore distance: " + distance);

        if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
          event.setCancelled(true);
        } else {
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

    teleportController.receiveMovement(event);

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
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      }
      event.setCancelled(true);
      movement.dropPostTickMotionProcessing = true;
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport movement ignore " + movement.awaitTeleport + " " + movement.awaitOutgoingTeleport);
      reader.release();
      return;
    }

    double distance = movement.verifiedLastPosition().distance(movement.position());

    if (distance > 50) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Distance movement ignore: " + distance);
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Distance movement ignore: " + distance);
      }
      logging.logSystemMessage(user, () -> "MOVEMENT REJECTED: Distance over limit: " + distance);
      movement.dropPostTickMotionProcessing = true;
      event.setCancelled(true);
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

    if (violationLevelData.isInActiveTeleportBundle) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Teleport bundle movement ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Teleport bundle movement ignore");
      }
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Teleport bundle movement ignore");
      movement.dropPostTickMotionProcessing = true;
      event.setCancelled(true);
      reader.release();
      return;
    }

    if (!movement.isTeleportConfirmationPacket &&
      movement.canResetMotion &&
      movement.mutableBaseMotionCopy().isZero() &&
      movement.sentOffsetMotion().isZero()
    ) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Movement reset ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Movement reset ignore");
      }
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Movement reset ignore");
      movement.canResetMotion = false;
      reader.release();
      return;
    }

    if (!movement.isTeleportConfirmationPacket) {
      timerCheck.receiveMovement(event);
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

      attackData.updatePerfectRotation();

      updatePotionEffects(user);
      movement.canResetMotion = false;
    } else {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Basic reset movement ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Basic reset movement ignore");
      }
      movement.canResetMotion = true;
    }

    // flag & setback -> remove packet
    if (movement.invalidMovement && violationLevelData.isInActiveTeleportBundle) {
      if (!movement.awaitOutgoingTeleport) {
        movement.outgoingTeleportCountdown = 5;
      }
      movement.awaitOutgoingTeleport = true; // awaiting next teleport
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
    Synchronizer.synchronize(player::updateInventory);
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
          reader.setOnGround(movement.onGround);
        }
      }
    }

    if (!cancellable.isCancelled() && !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing) {
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
    if (MinecraftVersions.VER1_21_3.atOrAbove() && user.meta().protocol().sendsInputs()) {
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
    engine = Engine.INTERNAL,
    packetsOut = {
      UPDATE_HEALTH
    }
  )
  public void catchFoodUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    Integer originalFoodLevel = event.getPacket().getIntegers().read(0);
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
    user.sendMessage(IntavePlugin.prefix() + "Player is sleeping in bed at " + sleepingBedPosition);
    user.packetTickFeedback(event, () -> {
      user.meta().movement().sleepingBedPosition = sleepingBedPosition;
    });
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
    if (reader.animation() == AnimationReader.Animation.WAKEUP) {
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
    if (reader.entityId() == player.getEntityId()) {
      Motion motion = reader.motion();
      if (IntaveControl.DEBUG_VELOCITY_RECEIVE) {
        player.sendMessage("§a" + MathHelper.formatMotion(motion));
      }
      MetadataBundle meta = user.meta();
      MovementMetadata movementData = meta.movement();
      if (movementData.willReceiveSetbackVelocity && motion.length() < 0.001) {
        movementData.willReceiveSetbackVelocity = false;
        reader.setMotion(movementData.setbackOverrideVelocity);
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
          reader.setMotion(motion);
        } else if (!event.isReadOnly()){
          cancellable.setCancelled(true);
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
      user.doubleTickFeedback(event,
        () -> {
	        velocity.set(MotionSetUpdate.openEnded(
		        finalVelocity,
		        movementData
	        ));
          movementData.queueTickAmbiguousUpdate(velocity.get());
        },
        () -> {
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
    MovementMetadata movement = user.meta().movement();
    Motion knockback = reader.motion();
    if (knockback != null) {
      AtomicReference<MotionAddUpdate> update = new AtomicReference<>(null);
      user.doubleTickFeedback(event,
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
  @PacketSubscription(
    packetsOut = BLOCK_ACTION
  )
  public void onBlockAction(
    User user, BlockActionReader reader
  ) {
    Player player = user.player();
    MovementMetadata movement = user.meta().movement();
    Material material = reader.blockType();
    if (SHULKER_BOX_MATERIALS.contains(material)) {
      BlockPosition blockPosition = reader.blockPosition();
      World world = player.getWorld();
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, blockPosition.toLocation(world));
      Direction facing = variant.enumProperty(Direction.class, "facing");
      boolean opening = reader.data() == 1;
      user.tickFeedback(() -> {
        if (movement.shulkerData.containsKey(blockPosition)) {
          ShulkerBox shulkerBox = movement.shulkerData.get(blockPosition);
          if (opening) {
            shulkerBox.open();
          } else {
            shulkerBox.close();
          }
        } else {
          int positionHash = blockPosition.getX() << 12 | blockPosition.getY() << 8 | blockPosition.getZ();
          ShulkerBox box = opening ? ShulkerBox.opening(facing) : ShulkerBox.closing(facing);
          movement.shulkerData.put(blockPosition, box);
          movement.shulkers.add(blockPosition);
          movement.shulkerDataHashCodeAccess.putIfAbsent(positionHash, box);
        }
        double distanceToShulker = MathHelper.distanceOf(
          movement.positionX, movement.positionY, movement.positionZ,
          blockPosition.getX() + 0.5, blockPosition.getY() + 0.5, blockPosition.getZ() + 0.5
        );
        if (distanceToShulker <= 4) {
          movement.lowestShulkerY = Math.min(movement.lowestShulkerY, blockPosition.getY());
          movement.highestShulkerY = Math.max(movement.highestShulkerY, blockPosition.getY() + 1);
          switch (facing.axis()) {
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
      });
    } else if (PISTON_MATERIALS.contains(material)) {
      BlockPosition blockPosition = reader.blockPosition();
      World world = player.getWorld();
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, blockPosition.toLocation(world));
      Direction facing = variant.enumProperty(Direction.class, "facing");
      Boolean extended = variant.propertyOf("extended");
      boolean isExtending = true;//extended == null || !extended;
      if (isExtending) {
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
//        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(user.player(), "(DEBUG/MOVEMENTIGNORE) Item Usage Tick");
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
              user.player().sendMessage(ChatColor.GREEN + "Activated elytra flying (START_FALL_FLYING)");
            }
            movementData.manualPoseSet(Pose.FALL_FLYING);
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
