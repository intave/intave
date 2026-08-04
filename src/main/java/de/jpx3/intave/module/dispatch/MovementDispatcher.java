package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.check.world.interaction.PrinterMode;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.executor.FoliaSafeTeleport;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.DEBUG_MOVEMENT_IGNORE;
import static de.jpx3.intave.check.movement.physics.MoveMetric.*;
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
  private static final boolean ELYTRA_SUPPORTED = MinecraftVersions.VER1_9_0.atOrAbove();
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
      Synchronizer.synchronize(user, () -> {
        FoliaSafeTeleport.teleport(player, fixed);
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
    movementData.rotationPitch = location.getPitch();
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
//    Modules.feedback()
//      .synchronize(player, UserRepository.userOf(player), (p, user) -> {
    User user = UserRepository.userOf(player);
    user.tickFeedback(() -> {
      MetadataBundle meta = user.meta();
      MovementMetadata movement = meta.movement();
      ProtocolMetadata protocol = meta.protocol();
      InventoryMetadata inventory = meta.inventory();
      movement.sneaking = false;
      movement.setSprinting(false);
      if (protocol.protocolVersion() >= VER_1_16) {
        user.refreshSprintState();
      }
      Synchronizer.synchronize(user, inventory::releaseItemNextTick);
      movement.baseMotionX = 0;
      movement.baseMotionY = 0;
      movement.baseMotionZ = 0;
      user.blockCache().invalidateAll();
      meta.potions().clearPotionEffects();
    });
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
    Motion knockback = reader.motion();
    if (knockback != null) {
      user.packetTickFeedback(event, () -> {
        if (IntaveControl.DEBUG_VELOCITY_RECEIVE) {
          user.sendMessage("§a" + MathHelper.formatMotion(knockback));
        }
        MovementMetadata movement = user.meta().movement();
        movement.baseMotionX += knockback.motionX;
        movement.baseMotionY += knockback.motionY;
        movement.baseMotionZ += knockback.motionZ;
      });
    }
  }

  /**
   * A vanilla client sends exactly one movement packet per tick, 50ms apart. Two of them
   * this close together are therefore not two ticks of the client's clock: the second one
   * was produced outside the tick loop, which is what a schematic printer does when it
   * turns the player towards the block it is placing.
   */
  private static final long SURPLUS_ROTATION_WINDOW_MS = 30;

  /**
   * Whether this movement packet is an extra rotation update rather than a tick, and may
   * be applied without being counted as one. Deliberately narrow, because "packets that
   * do not count" is exactly what a timer cheat wants:
   * <ul>
   *   <li>only under printer mode, so nothing changes for a normal server;</li>
   *   <li>only a packet that carries <b>no position</b> - there is no movement in it to
   *       hide, so ignoring it as a tick cannot buy distance or speed;</li>
   *   <li>only when the previous movement packet arrived less than
   *       {@link #SURPLUS_ROTATION_WINDOW_MS} ago, so the one packet a turning-in-place
   *       player legitimately sends per tick still counts in full.</li>
   * </ul>
   * A client that sends its packets too fast still does so with position packets, which
   * are unaffected, so the timer check keeps working.
   */
  private boolean isSurplusRotationPacket(User user, PlayerMoveReader reader, boolean hasMovement, boolean hasRotation) {
    if (!hasRotation || !PrinterMode.enabled()) {
      return false;
    }
    long lastMovementPacket = user.meta().connection().lastMovementPacket();
    if (lastMovementPacket == 0
      || System.currentTimeMillis() - lastMovementPacket >= SURPLUS_ROTATION_WINDOW_MS) {
      return false;
    }
    if (!hasMovement) {
      return true;
    }
    // The printer also restores the rotation afterwards, and that one carries a position -
    // the position it already reported this tick. It counts as surplus only while it is
    // exactly that, so anything with actual movement in it stays a tick.
    MovementMetadata movement = user.meta().movement();
    return Math.abs(reader.positionX() - movement.positionX) < 0.00001
      && Math.abs(reader.positionY() - movement.positionY) < 0.00001
      && Math.abs(reader.positionZ() - movement.positionZ) < 0.00001;
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
      movement.rotationYaw = packet.getFloat().read(0);
      movement.rotationPitch = packet.getFloat().read(1);
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Vehicle rotation only");
      reader.release();
      return;
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

    if (isSurplusRotationPacket(user, reader, hasMovement, hasRotation)) {
      // A schematic printer turns the player towards each block it places and back, and
      // it does that outside the client's tick loop: the rotation is sent as an extra
      // movement packet within a tick that already sent one. Every check downstream
      // counts one movement packet as one tick, so such a packet is a phantom tick - it
      // pushes the timer's clock 50ms ahead of real time (the "moved too frequently
      // (~1.1 ticks ahead)" flags) and gives the physics chain a tick in which the
      // client reported no movement while we keep applying gravity and momentum (the
      // "received (0,0,0) vs predicted (…, 0.33, …)" detections).
      // The rotation itself is real, so it is applied - the packet just is not a tick.
      movement.applyRotationOnly(reader.yaw(), reader.pitch());
      logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: Surplus rotation packet (printer mode)");
      if (IntaveLogger.traceEnabled()) {
        IntaveLogger.trace("[PLACE-DEBUG] " + player.getName()
          + " surplus rotation packet " + packetType.name()
          + " " + (System.currentTimeMillis() - connectionData.lastMovementPacket())
          + "ms after the previous movement packet - not counted as a tick");
      }
      reader.release();
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
      logging.logSystemMessage(user, () -> "MOTION LOGIC: Received motion: " + movement.motion());
    }

    teleportController.receiveMovement(event);

    if (IntaveControl.DEBUG_COLLISION_BOXES || user.receives(MessageChannel.DEBUG_COLLISIONS)) {
      BoundingBox box = movement.boundingBox().grow(0.1);
      BlockShape shape = Collision.shape(user, movement, box);
      drawDebugBoxes(user, BlockShapes.optimize(shape).elementaryBoxes());
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

      // This path never re-anchors: it cancels the packet and asks for a setback, and the
      // verified position only moves again once that setback lands. When it does not --
      // an unseen teleport (a missed outbound send on Folia), a cancelled or failed
      // setback -- the player simply walks on and every single packet is the same offence
      // again. Live proof: 207 consecutive rejections in 13 seconds whose distance rose
      // and fell by the player's own step (50.19 -> 56.27 -> 50.41, i.e. they walked away
      // and back, never being moved), +25 VL each, VL pinned at its 1000 cap, resolved
      // only when DesyncWatchdog force-teleported them 24 seconds later.
      //
      // The repeats carry no information the first one did not, so only the first is
      // counted. Cancelling and re-requesting the setback continues unchanged, so a
      // player genuinely throwing 50-block positions is still stopped every tick and
      // still takes the violation that starts it.
      boolean firstRejection = movement.unsafePositionRejections == 0;
      movement.unsafePositionRejections++;

      if (firstRejection) {
        Modules.mitigate().movement().emulationSetBack(player, movement.mutableBaseMotionCopy(), 10, false);
      } else {
        // Re-arming the emulation every tick stacks setback bundles on top of each other,
        // which is its own source of teleport churn; the one in flight is enough.
        reader.release();
        return;
      }

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

    movement.unsafePositionRejections = 0;

    Entity attachedEntity = movement.ridingEntity();
    if (attachedEntity != null && !attachedEntity.isEntityAlive()
      && attachedEntity.hasTypeData() && attachedEntity.typeData().isLivingEntity()) {
      movement.dismountRidingEntity("Riding dead entity");
    }

    double distanceMoved = Hypot.fast(movement.motionX(), movement.motionZ());
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
      movement.motion().isZero()
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
      if (movement.motion().length() > 0.5 && movement.ticksPast(VEHICLE_DETACHMENT) < 2) {
        movement.setBaseMotion(Motion.newEmpty());
        movement.physicsResetMotionX = true;
        movement.physicsResetMotionZ = true;
      }

      if (hasMovement || hasRotation) {
        physicsCheck.receiveMovement(user, hasMovement, hasRotation);
      } else {
        logging.logSystemMessage(user, () -> "MOVEMENT IGNORED: No movement or rotation");
        physicsCheck.updateOnGroundIfFlying(user);
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
    packet.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
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

    if (!vehicleMove && !movement.awaitTeleport && !movement.awaitOutgoingTeleport && !movement.invalidMovement && !movement.dropPostTickMotionProcessing) {
      if (claimsToBeOnGround != movement.onGround) {
        double requiredFallDistance = Collision.present(user, movement, movement.boundingBox().grow(0.1, 0.1, 0.1)) ? 0.5 : 0.1;
        boolean shulkerInteraction = movement.shulkerXToleranceRemaining > 0 || movement.shulkerYToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0;
        if (shulkerInteraction) {
          requiredFallDistance = Math.max(requiredFallDistance, 3);
        }
        // A piston displaces the player (or the block they stand on) without giving
        // them any velocity, so for a moment our idea of where the ground is and the
        // client's disagree by up to the block the piston moved.
        if (movement.pistonMotionToleranceRemaining > 0) {
          requiredFallDistance = Math.max(requiredFallDistance, 3);
        }
        // A player standing on an entity is standing on nothing as far as this check is
        // concerned: the simulator collides against blocks only, so their support is
        // invisible to it and it has them falling for as long as they stand there. The
        // evaluator already tolerates that for the movement itself (collidedWithBoat);
        // without the same exemption here the ground claim alone still costs 0.5 VL a
        // tick. Five blocks covers the tallest thing that can carry a player -- a happy
        // ghast is four high -- and applies only while one is actually next to them.
        if (movement.collidedWithBoat()) {
          requiredFallDistance = Math.max(requiredFallDistance, 5);
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
        if (movement.artificialFallDistance > requiredFallDistance || Math.abs(movement.motionY()) > 0.01) {
          reader.setOnGround(movement.onGround);
        }
      }
    }

    if (!cancellable.isCancelled() && !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing) {
      physicsCheck.endMovement(user, hasMovement);
      movement.lastOnGround = movement.onGround;
      movement.setVerifiedLastPosition(
        movement.position(),
        "Verification push"
      );
    }

    attack.tickComplete();
    movement.tickComplete(hasMovement, hasRotation);
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
    if (MinecraftVersions.VER1_21_3.atOrAbove()) {
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
      if (IntaveLogger.traceEnabled()) {
        Input in = movementData.input;
        IntaveLogger.trace("[INPUT-DEBUG] " + player.getName()
          + " fwd=" + in.forward() + " back=" + in.backward()
          + " left=" + in.left() + " right=" + in.right()
          + " jump=" + in.jump() + " sneak=" + in.sneaking() + " sprint=" + in.sprinting()
          + " -> fKey=" + in.forwardKey() + " sKey=" + in.sidewaysKey()
          + " | aiMoveSpeed=" + movementData.aiMoveSpeed()
          + " friction/speed=" + movementData.friction());
      }
    } else {
      int strafeKey = (int) (packet.getFloat().read(0) / 0.98f);
      int forwardKey = (int) (packet.getFloat().read(1) / 0.98f);
      if ((Math.abs(strafeKey) > 1 || Math.abs(forwardKey) > 1) && FaultKicks.INVALID_KEY_INPUT) {
        user.kick("Invalid key input");
        return;
      }
      Boolean jumping = packet.getBooleans().read(0);
      movementData.externalKeyApply = true;
      movementData.clientStrafeKey = strafeKey;
      movementData.clientForwardKey = forwardKey;
      movementData.clientPressedJump = jumping;
    }
  }

  @PacketSubscription(
    engine = Engine.ASYNC_INTERNAL,
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
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      ENTITY_METADATA
    }
  )
  public void receiveElytraUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Integer entityId = packet.getIntegers().read(0);

    if (!ELYTRA_SUPPORTED || entityId != player.getEntityId()) {
      return;
    }

    EntityMetadataReader reader = PacketReaders.readerOf(packet);
    Object elytraObject = reader.fetchRaw(0);

    if (elytraObject == null) {
      reader.release();
      return;
    }

    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    ProtocolMetadata protocol = meta.protocol();

    if (!protocol.canUseElytra()) {
      reader.release();
      return;
    }

    if (IntaveControl.DEBUG_ELYTRA) {
      player.sendMessage("Elytra update received");
    }

    byte data = (byte) elytraObject;
    boolean gliding = (data & 1 << 7) != 0;

    user.tickFeedback(() -> {
      movement.elytraFlying = gliding;
      movement.updatePose();
      if (IntaveControl.DEBUG_ELYTRA) {
        if (gliding) {
          player.sendMessage("§aActivated elytra flying (metadata)");
        } else {
          player.sendMessage("§cDeactivated elytra flying (metadata)");
        }
      }
    });

    reader.release();
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
//      Motion motion = Motion.fromVector(velocity);
      // this caused more problems than it solved
//      if (
//        Physics.USE_SUPERPOSITIONS && violationMetadata.physicsOffset < 0.5 &&
//        /* on 1.19.4 we use bundles */ !MinecraftVersions.VER1_19_4.atOrAbove()
//      ) {
//        movementData.velocitySuperposition().stateSynchronize(
//          event, motion, begin -> {},
//          end -> movementData.pendingVelocityPackets.decrementAndGet()
//        );
//      } else {
      Motion finalVelocity = motion.copy();
      user.packetTickFeedback(event, () -> {
        receiveVelocity(player, finalVelocity);
        movementData.pendingVelocityPackets.decrementAndGet();
      });
//      }
      movementData.activeTick(RECEIVED_VELOCITY_PACKET);
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
      movementData.setBaseMotion(velocity);
      movementData.lastVelocity = velocity.copy();
      if (!movementData.willReceiveSetbackVelocity && !movementData.willReceiveFinalSetbackVelocity) {
        movementData.activeTick(EXTERNAL_VELOCITY);
        // Open the knockback timing window. We merge the velocity into the base
        // motion when the anchoring transaction is acknowledged; the client applied
        // it when the packet arrived. Those are the same tick only on a quiet
        // connection, so for the next few ticks the received motion may differ from
        // the prediction by up to the knockback itself (see SimulationEvaluator).
        movementData.armVelocityTolerance(user.latency(), velocity);
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
  // A piston extends over two ticks and displaces what it pushes by one block, so it
  // moves a player half a block in a tick -- positionally, with no velocity for us to
  // have predicted. Add the tick of gravity we did predict against it, plus slop: still
  // comfortably under the one block a single extension can ever amount to.
  private static final double PISTON_PUSH_PER_TICK = 0.65;
  private static final int PISTON_MAX_PUSH_LENGTH = 12;
  private static final int PISTON_TOLERANCE_TICKS = 10;

  /**
   * The volume a piston extension can displace something in: the column of up to
   * {@link #PISTON_MAX_PUSH_LENGTH} blocks it pushes, starting at the head, plus one
   * more block past the end for a player being carried on top of the last one, and half
   * a block laterally for a player standing on an edge.
   */
  private static BoundingBox pushCorridorOf(int headX, int headY, int headZ, RawVector3d direction) {
    int reach = PISTON_MAX_PUSH_LENGTH + 1;
    double endX = headX + direction.x * reach;
    double endY = headY + direction.y * reach;
    double endZ = headZ + direction.z * reach;
    return BoundingBox.fromBounds(
      Math.min(headX, endX), Math.min(headY, endY), Math.min(headZ, endZ),
      Math.max(headX, endX) + 1, Math.max(headY, endY) + 1, Math.max(headZ, endZ) + 1
    ).grow(0.5);
  }
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
      Direction variantFacing = variant.enumProperty(Direction.class, "facing");
      // An unknown variant leaves the facing null, and everything downstream
      // dereferences it (facing.axis() here, direction.ordinal() in ShulkerBox), so the
      // whole callback used to die before arming any tolerance -- the opening lid then
      // pushed the player while nothing accounted for it. Assume the common upright
      // orientation and, since the guess may be wrong, tolerate on every axis.
      boolean facingKnown = variantFacing != null;
      Direction facing = facingKnown ? variantFacing : Direction.UP;
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
          ShulkerBox box = opening ? ShulkerBox.opening(facing) : ShulkerBox.closing(facing);
          movement.shulkerData.put(blockPosition, box);
          movement.shulkers.add(blockPosition);
        }
        double distanceToShulker = MathHelper.distanceOf(
          movement.positionX, movement.positionY, movement.positionZ,
          blockPosition.getX() + 0.5, blockPosition.getY() + 0.5, blockPosition.getZ() + 0.5
        );
        if (distanceToShulker <= 4) {
          movement.lowestShulkerY = Math.min(movement.lowestShulkerY, blockPosition.getY());
          movement.highestShulkerY = Math.max(movement.highestShulkerY, blockPosition.getY() + 1);
          if (!facingKnown) {
            movement.shulkerXToleranceRemaining = 20;
            movement.shulkerYToleranceRemaining = 20;
            movement.shulkerZToleranceRemaining = 20;
          } else {
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
        }
      });
    } else if (PISTON_MATERIALS.contains(material)) {
      BlockPosition blockPosition = reader.blockPosition();
      World world = player.getWorld();
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, blockPosition.toLocation(world));
      Direction pistonFacing = variant.enumProperty(Direction.class, "facing");
      if (pistonFacing == null) {
        // An unknown variant used to leave this null and NPE inside the callback below,
        // killing the whole arming step -- the piston then moved the player with no
        // tolerance at all. There is nothing sensible to guess about a direction, so
        // cover every axis instead.
        movement.pistonMotionToleranceRemaining = PISTON_TOLERANCE_TICKS;
        movement.pistonCollisionArea = new BoundingBox(0, 0, 0, 1, 1, 1)
          .offset(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ())
          .grow(PISTON_MAX_PUSH_LENGTH + 1);
        movement.pistonHorizontalAllowance = PISTON_PUSH_PER_TICK;
        movement.pistonVerticalAllowance = PISTON_PUSH_PER_TICK;
        return;
      }
      Direction facing = pistonFacing;
      Boolean extended = variant.propertyOf("extended");
      boolean isExtending = true;//extended == null || !extended;
      if (isExtending) {
        Modules.feedback().synchronize(player, nothing -> {
          // First off, check if the player is even affected by this
          RawVector3d directionVec = facing.directionVector();
          int expectedPistonX = (int) directionVec.x + blockPosition.getX();
          int expectedPistonY = (int) directionVec.y + blockPosition.getY();
          int expectedPistonZ = (int) directionVec.z + blockPosition.getZ();
          // A piston pushes a whole column -- up to 12 blocks -- so the player can be
          // standing on, or in the way of, any block along it, not just the one in front
          // of the head. Checking only that first block meant "a piston moved the block
          // I am standing on" armed no tolerance whenever the stack was longer than one.
          // One extra block past the end covers the player carried on top of the last one.
          BoundingBox expandingBlockArea = pushCorridorOf(
            expectedPistonX, expectedPistonY, expectedPistonZ, directionVec
          );
          boolean playerAffected = expandingBlockArea.intersectsWith(user.meta().movement().boundingBox());

          // Only do something if the player is actually affected
          if (playerAffected) {
            // Might seem like a high value, doesn't it?
            // Well this is fine as we constantly check if the player is inside the critical area
            // where he would get false-mitigated
            movement.pistonMotionToleranceRemaining = PISTON_TOLERANCE_TICKS;
            movement.pistonCollisionArea = expandingBlockArea;

            float xOffset = (float) Math.abs(expectedPistonX - user.meta().movement().positionX);
            float yOffsetBottom = (float) Math.abs((expectedPistonY + 1) - user.meta().movement().boundingBox().minY);
            float yOffsetTop = (float) Math.abs(expectedPistonY - user.meta().movement().boundingBox().maxY);
            float zOffset = (float) Math.abs(expectedPistonZ - user.meta().movement().positionZ);
            // The distance to the piston is not the size of the error. A piston displaces
            // what it pushes by a whole block over two ticks -- 0.5 per tick -- and does
            // so positionally, without handing the entity any velocity we could predict.
            // A player standing right on the head therefore had an allowance of 0.05
            // against a 0.5 move. Floor every allowance at the push speed; that is a hard
            // physical bound on what a piston can do to a player in a tick.
            switch (facing.axis()) {
              case X_AXIS: {
                // Magical hack to get the proper bounding box factor
                float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.x);
                movement.pistonHorizontalAllowance = Math.max(PISTON_PUSH_PER_TICK, xOffset + horizontalBoundingBoxFactor + 0.05f);
                break;
              }
              case Z_AXIS: {
                // Magical hack to get the proper bounding box factor
                float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.z);
                movement.pistonHorizontalAllowance = Math.max(PISTON_PUSH_PER_TICK, zOffset + horizontalBoundingBoxFactor + 0.05f);
                break;
              }
              case Y_AXIS: {
                // Cannot be done with directional vectors unfortunately :(
                switch (facing) {
                  case UP:
                    movement.pistonVerticalAllowance = Math.max(PISTON_PUSH_PER_TICK, yOffsetBottom + 0.05f);
                    break;
                  case DOWN:
                    movement.pistonVerticalAllowance = Math.max(PISTON_PUSH_PER_TICK, yOffsetTop + 0.05f);
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
            movementData.elytraFlying = true;
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
    boolean sneaking = input.sneaking();
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
      movementData.sneaking = false;
    } else {
      movementData.sneaking = true;
    }
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.player().sendMessage(ChatColor.GREEN + "Start sneaking " + movementData.sneaking);
    }
  }

  private void stopSneak(User user) {
    MovementMetadata movementData = user.meta().movement();
    movementData.sneaking = false;
    if (IntaveControl.DEBUG_PLAYER_ACTIONS || user.receives(MessageChannel.DEBUG_PLAYER_ACTIONS)) {
      user.player().sendMessage(ChatColor.RED + "Stop sneaking after " + movementData.ticks(SNEAKING));
    }
  }

  private boolean allowSprinting(User user) {
    return !user.meta().inventory().inventoryOpen();
  }
}
