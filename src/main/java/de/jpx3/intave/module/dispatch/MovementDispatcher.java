package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.Superposition;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.DEBUG_MOVEMENT_IGNORE;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_16;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

@Relocate
public final class MovementDispatcher extends Module {
  private static final boolean ELYTRA_SUPPORTED = MinecraftVersions.VER1_9_0.atOrAbove();
  private TeleportApplyEnforcer teleportApplyEnforcer;
  private Physics physicsCheck;
  private InteractionRaytrace interactionRaytraceCheck;
  private Timer timerCheck;

  @Override
  public void enable() {
    CheckService checks = plugin.checks();
    this.physicsCheck = checks.searchCheck(Physics.class);
    this.interactionRaytraceCheck = checks.searchCheck(InteractionRaytrace.class);
    this.timerCheck = checks.searchCheck(Timer.class);
    this.teleportApplyEnforcer = new TeleportApplyEnforcer();
    this.teleportApplyEnforcer.setup();
  }

  @BukkitEventSubscription
  public void receiveExternalTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PlayerTeleportEvent.TeleportCause cause = event.getCause();
    if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
      return;
    }
    Location fromLocation = event.getFrom();
    Location toLocation = event.getTo();
    World world = toLocation.getWorld();
    if (toLocation.getWorld() != player.getWorld() || toLocation.distance(fromLocation) > 8) {
      MovementMetadata movement = user.meta().movement();

      BoundingBox bb = BoundingBox.fromPosition(user, movement, toLocation);
      int shiftAllowed = 5;
      Location oldToLocation = toLocation.clone();
      while (toLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && shiftAllowed-- > 0 && Collision.unsafePresent(world, player, bb) && Collision.unsafeNonePresent(world, player, bb.offset(0, 0.5, 0))) {
        toLocation.add(0, 0.1, 0);
        bb = BoundingBox.fromPosition(user, movement, toLocation);
      }
      if (IntaveControl.DEBUG_STUCK_REVIVAL) {
        player.sendMessage("SREV " + shiftAllowed + " " + toLocation.distance(oldToLocation) + " cause " + cause);
      }
      event.setTo(toLocation);
    }
//    respawn.setRespawnLocation(toLocation);
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
    MovementMetadata movement = user.meta().movement();
    Location respawnLocation = respawn.getRespawnLocation().clone();
    World world = respawnLocation.getWorld();
    int shiftAllowed = 5;
    BoundingBox bb = BoundingBox.fromPosition(user, movement, respawnLocation);
    while (respawnLocation.getY() < WorldHeight.UPPER_WORLD_LIMIT && shiftAllowed-- > 0 && Collision.unsafePresent(world, player, bb) && Collision.unsafeNonePresent(world, player, bb.offset(0, 0.5, 0))) {
      respawnLocation.add(0, 0.1, 0);
      bb = BoundingBox.fromPosition(user, movement, respawnLocation);
      if (IntaveControl.DEBUG_STUCK_REVIVAL) {
        player.sendMessage("SREV " + shiftAllowed + " " + respawnLocation.distance(respawn.getRespawnLocation()) + " respawn");
      }
    }
    respawn.setRespawnLocation(respawnLocation);
  }

  @BukkitEventSubscription
  public void receiveWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    movementData.updateWorld();
    user.blockStates().invalidateAll();
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
      MovementMetadata movement = user.meta().movement();
      ProtocolMetadata protocol = user.meta().protocol();
      movement.sneaking = false;
      movement.setSprinting(false);
      if (protocol.protocolVersion() >= VER_1_16) {
        movement.sprintReset();
        user.refreshSprintState();
      }
      movement.baseMotionX = 0;
      movement.baseMotionY = 0;
      movement.baseMotionZ = 0;
      user.blockStates().invalidateAll();
      user.meta().potions().clearPotionEffects();
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      EXPLOSION
    }
  )
  public void sentExplosion(PacketEvent event) {
    Player player = event.getPlayer();
    StructureModifier<Float> floats = event.getPacket().getFloat();
    User user = UserRepository.userOf(player);
    user.tickFeedback(() -> {
      MovementMetadata movementData = user.meta().movement();
      Float motionX = floats.read(1);
      Float motionY = floats.read(2);
      Float motionZ = floats.read(3);
      movementData.baseMotionX += motionX;
      movementData.baseMotionY += motionY;
      movementData.baseMotionZ += motionZ;
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead() || event.isCancelled()) {
      return;
    }

    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    InventoryMetadata inventoryData = meta.inventory();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ConnectionMetadata connectionData = meta.connection();
    ProtocolMetadata protocol = meta.protocol();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);
    boolean hasRotation = vehicleMove || packet.getBooleans().read(2);

    if (violationLevelData.disableActiveTeleportBundleNextTick) {
      violationLevelData.disableActiveTeleportBundleNextTick = false;
      violationLevelData.isInActiveTeleportBundle = false;
      movementData.dropPostTickMotionProcessing = true;
    }

    if (movementData.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movementData.applyGroundInformationToPacket(packet);
      movementData.rotationYaw = packet.getFloat().read(0);
      movementData.rotationPitch = packet.getFloat().read(1);
      return;
    }

    boolean clientVehicleMovement = MinecraftVersions.VER1_9_0.atOrAbove() && protocol.combatUpdate();
    if (movementData.isInRidingVehicle() && !vehicleMove && clientVehicleMovement) {
      movementData.dismountRidingEntity("Client vehicle movement");
    }

    if (movementData.isInRidingVehicle() && !vehicleMove && hasMovement) {
      if (movementData.invalidVehiclePositionTicks++ > 10) {
        movementData.dismountRidingEntity("Lower client vehicle movement");
      }
    }

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      for (int i = 0; i < 3; i++) {
        if (modifier.read(i) == null || Double.isInfinite(modifier.read(i)) && FaultKicks.POSITION_FAULTS) {
          user.kick("Intolerable position fault");
          return;
        }
      }
    }

    if (hasRotation) {
      StructureModifier<Float> modifier = packet.getFloat();
      for (int i = 0; i < 2; i++) {
        if (modifier.read(i) == null || Double.isInfinite(modifier.read(i)) && FaultKicks.POSITION_FAULTS) {
          user.kick("Intolerable position fault");
          return;
        }
      }
    }

    if (hasMovement || movementData.isInVehicle() || movementData.inRespawnScreen) {
      movementData.lastPositionUpdate = 0;
    } else if (++movementData.lastPositionUpdate > 20 && FaultKicks.MISSING_POSITION_UPDATE && !user.justJoined() && !user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      user.kick("Missing position update");
    }

    // fix only works for 1.8
    if (movementData.sprinting && movementData.isSneaking() && movementData.lastSneaking && !protocol.combatUpdate() && movementData.acceptSneakFaults && FaultKicks.INVALID_PLAYER_ACTION && !user.justJoined() && !user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      movementData.acceptSneakFaults = false;
      user.refreshSprintState(unused -> {
        movementData.sprintSneakFaults++;
        movementData.acceptSneakFaults = true;
      });
      if (movementData.sprintSneakFaults > 1) {
        user.kick("Repeated player action faults");
      }
    }

    // see MultiPlayerGameMode#useItem
    if (protocol.cavesAndCliffsUpdate() && !movementData.awaitTeleport
//      && !movementData.awaitOutgoingTeleport
      && packet.getType() == PacketType.Play.Client.POSITION_LOOK
    ) {
      StructureModifier<Double> modifier = packet.getDoubles();
      double positionX = modifier.read(0);
      double positionY = modifier.read(1);
      double positionZ = modifier.read(2);
      double motionX = positionX - movementData.verifiedPositionX;
      double motionY = positionY - movementData.verifiedPositionY;
      double motionZ = positionZ - movementData.verifiedPositionZ;
      double distance = MathHelper.hypot3d(motionX, motionY, motionZ);

      if (distance < 0.00001) {
        movementData.dropPostTickMotionProcessing = true;
        Float yaw = packet.getFloat().read(0);
        Float pitch = packet.getFloat().read(1);
//        movementData.lastRotationYaw = movementData.rotationYaw;
//        movementData.lastRotationPitch = movementData.rotationPitch;
        movementData.rotationYaw = yaw;
        movementData.rotationPitch = pitch;

        if (DEBUG_MOVEMENT_IGNORE) {
          double yawDifference = MathHelper.noAbsDistanceInDegrees(movementData.lastRotationYaw, yaw);
          double pitchDifference = MathHelper.noAbsDistanceInDegrees(movementData.lastRotationPitch, pitch);
//          Synchronizer.synchronize(() -> {
//            player.sendMessage("Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
//          });
          System.out.println("[Intave] Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Click movement ignore distance: " + distance + " yaw: " + yawDifference + " pitch: " + pitchDifference);
        }

        if (!MinecraftVersions.VER1_9_0.atOrAbove()) {
          event.setCancelled(true);
        }
        return;
      }
    }
    movementData.awaitClickMovementSkip = false;

    connectionData.receiveMovement();
    movementData.updateMovement(packet, hasMovement, hasRotation);
    teleportApplyEnforcer.receiveMovement(event);

    for (Superposition<?> superposition : movementData.superpositions()) {
      superposition.beginTick();
    }
    for (Superposition<?> superposition : movementData.superpositions()) {
      superposition.computeVariations();
    }

    if (movementData.awaitTeleport || movementData.awaitOutgoingTeleport) {
      if (DEBUG_MOVEMENT_IGNORE) {
        System.out.println("[Intave] Teleport movement ignore " + movementData.awaitTeleport + " " + movementData.awaitOutgoingTeleport);
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Teleport movement ignore " + movementData.awaitTeleport + " " + movementData.awaitOutgoingTeleport);
      }
      event.setCancelled(true);
      movementData.dropPostTickMotionProcessing = true;
      return;
    }

    double distance = MathHelper.distanceOf(
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      movementData.positionX, movementData.positionY, movementData.positionZ
    );

    if (distance > 50) {
      if (DEBUG_MOVEMENT_IGNORE) {
//        player.sendMessage("Distance movement ignore: " + distance);
        System.out.println("[Intave] Distance movement ignore: " + distance);
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Distance movement ignore: " + distance);
      }
      event.setCancelled(true);
      Vector vector = new Vector(movementData.baseMotionX, movementData.baseMotionY, movementData.baseMotionZ);
      Modules.mitigate().movement().emulationSetBack(player, vector, 10, false);
      String message = "sent unsafe position";
      String details = "moved " + MathHelper.formatDouble(distance, 2) + " blocks";
      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(25)
        .build();
      Modules.violationProcessor().processViolation(violation);
      return;
    }

    Entity attachedEntity = movementData.ridingEntity();
    if (attachedEntity != null && !attachedEntity.isEntityAlive()
      && attachedEntity.hasTypeData() && attachedEntity.typeData().isLivingEntity()) {
      movementData.dismountRidingEntity("Riding dead entity");
    }

    if (inventoryData.releaseItemNextTick) {
      releaseItem(user);
      inventoryData.releaseItemNextTick = false;
      inventoryData.releaseItemType = Material.AIR;
    }

    if (violationLevelData.isInActiveTeleportBundle) {
      if (DEBUG_MOVEMENT_IGNORE) {
//        player.sendMessage("Teleport bundle movement ignore");
        System.out.println("[Intave] Teleport bundle movement ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Teleport bundle movement ignore");
      }
      event.setCancelled(true);
      return;
    }

    if (!movementData.isTeleportConfirmationPacket &&
      movementData.canResetMotion &&
      movementData.baseMotionX == 0 &&
      movementData.baseMotionY == 0 &&
      movementData.baseMotionZ == 0 &&
      movementData.motionX() == 0 &&
      movementData.motionY() == 0 &&
      movementData.motionZ() == 0
    ) {
      if (DEBUG_MOVEMENT_IGNORE) {
//        player.sendMessage("Movement reset ignore");
        System.out.println("[Intave] Movement reset ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Movement reset ignore");
      }
      movementData.canResetMotion = false;
      return;
    }

    if (!connectionData.nextFeedbackSubscribers.isEmpty()) {
      connectionData.movementPassedForNFS = true;
    }

//    System.out.println("Received movement");

    if (!movementData.isTeleportConfirmationPacket) {
      timerCheck.receiveMovement(event);
      if (interactionRaytraceCheck.receiveMovement(event)) {
        movementData.compileSpecialBlocks();
        movementData.recheckWebStateFromLastTick();
      }

      if (hasMovement || hasRotation) {
        physicsCheck.receiveMovement(user, hasMovement, hasRotation);
      } else {
        physicsCheck.updateOnGroundIfFlying(user);
      }

      boolean clientOnGround = vehicleMove ? player.isOnGround() : packet.getBooleans().read(0);
      boolean collidedWithBoat = movementData.collidedWithBoat();

      if (!vehicleMove && !collidedWithBoat) {
//        movementData.applyGroundInformationToPacket(packet);
      }

      if (movementData.onGround && !clientOnGround && movementData.step) {
        movementData.onGround = false;
      }

      if (collidedWithBoat) {
        movementData.onGround = clientOnGround;
      }

      attackData.updatePerfectRotation();

/*      if (inventoryData.awaitingSlotSet != -1) {
        Synchronizer.synchronize(() -> {
          int awaitingSlotSet = inventoryData.awaitingSlotSet;
          if (awaitingSlotSet != -1) {
            player.getInventory().setHeldItemSlot(awaitingSlotSet);
            inventoryData.awaitingSlotSet = -1;
          }
        });
      }*/
      updatePotionEffects(user);
      movementData.canResetMotion = false;
    } else {
      if (DEBUG_MOVEMENT_IGNORE) {
//        player.sendMessage("Basic reset movement ignore");
        System.out.println("[Intave] Basic reset movement ignore");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/MOVEMENTIGNORE) Basic reset movement ignore");
      }
      movementData.canResetMotion = true;
    }

    // flag & setback -> remove packet
    if (movementData.invalidMovement && violationLevelData.isInActiveTeleportBundle) {
      movementData.awaitOutgoingTeleport = true; // awaiting next teleport
      movementData.outgoingTeleportCountdown = 5;
      event.setCancelled(true);
    }
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
    Player player = user.player();
    ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
    InventoryMetadata inventory = user.meta().inventory();
    if (ItemProperties.isBow(inventory.releaseItemType) || ItemProperties.isBow(inventory.activeItemType())) {
      inventory.blockNextArrow = true;
      inventory.lastBlockArrowRequest = System.currentTimeMillis();
    }
    inventory.lastFoodConsumptionBlockRequest = System.currentTimeMillis();
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.BLOCK_DIG);
    packet.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
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
  public void receiveFinalMovement(PacketEvent event) {
    Player player = event.getPlayer();

    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    InventoryMetadata inventoryData = meta.inventory();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);
    boolean hasRotation = vehicleMove || packet.getBooleans().read(2);
    boolean claimsToBeOnGround = vehicleMove ? player.isOnGround() : packet.getBooleans().read(0);

    for (Superposition<?> superposition : movement.superpositions()) {
      superposition.completeTick();
    }

    if (player.isDead() || movement.awaitTeleport) {
      return;
    }

    if (movement.isInVehicle() && !vehicleMove && hasRotation && !hasMovement) {
      movement.applyGroundInformationToPacket(packet);
      movement.verifiedPositionX = movement.positionX;
      movement.verifiedPositionY = movement.positionY;
      movement.verifiedPositionZ = movement.positionZ;
      return;
    }

    if (!vehicleMove && !movement.awaitTeleport && !movement.awaitOutgoingTeleport && !movement.invalidMovement && !movement.dropPostTickMotionProcessing) {
      if (claimsToBeOnGround != movement.onGround) {
        double requiredFallDistance = Collision.present(player, user.meta().movement().boundingBox().grow(0.1, 0.1, 0.1)) ? 0.5 : 0.1;
        if (movement.artificialFallDistance > requiredFallDistance && !movement.onGround && claimsToBeOnGround) {
          Violation violation = Violation.builderFor(Physics.class)
            .forPlayer(player)
            .withMessage("claimed to be on ground midair")
            .withDetails("falling " + formatDouble(movement.artificialFallDistance, 2) + " blocks")
            .withVL(0.5)
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .build();
          Modules.violationProcessor().processViolation(violation);
          packet.getBooleans().write(0, false);
        }
      }
    }

    // onGround == true -> falldamage

    if (!event.isCancelled() && !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing) {
      physicsCheck.endMovement(user, hasMovement);
    }

    // if event is cancelled, we must flush certain states
    if (event.isCancelled()) {
      movement.inWeb = false;
    }

    if (!movement.isTeleportConfirmationPacket) {
      movement.lastTeleport++;
    }

    movement.invalidMovement = false;
    movement.suspiciousMovement = false;
    movement.isTeleportConfirmationPacket = false;
    movement.dropPostTickMotionProcessing = false;

    Map<BlockPosition, ShulkerBox> shulkerData = movement.shulkerData;
    Map<Integer, ShulkerBox> shulkerDataHashCodeAccess = movement.shulkerDataHashCodeAccess;
    if (!shulkerData.isEmpty()) {
      int shulkerLimit = 2048;
      for (Iterator<BlockPosition> iterator = movement.shulkers.iterator(); iterator.hasNext(); ) {
        if (shulkerLimit-- <= 0) {
          break;
        }
        BlockPosition shulkerBlock = iterator.next();
        ShulkerBox shulkerBox = shulkerData.get(shulkerBlock);
        if (shulkerBox == null) {
          iterator.remove();
          continue;
        }
        if (shulkerBox.complete()) {
          iterator.remove();
          shulkerData.remove(shulkerBlock);
          shulkerDataHashCodeAccess.remove(shulkerBox.hashCode());
        } else if (shulkerBox.shouldTick()) {
          shulkerBox.tick();
        }
      }
    }

    if (movement.shulkerXToleranceRemaining > 0) {
      movement.shulkerXToleranceRemaining--;
    }
    if (movement.shulkerYToleranceRemaining > 0) {
      movement.shulkerYToleranceRemaining--;
      if (movement.shulkerYToleranceRemaining == 0) {
        movement.highestShulkerY = Integer.MIN_VALUE;
        movement.lowestShulkerY = Integer.MAX_VALUE;
      }
    }
    if (movement.shulkerZToleranceRemaining > 0) {
      movement.shulkerZToleranceRemaining--;
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      movement.pistonMotionToleranceRemaining--;
    }

    boolean flyingWithElytra = movement.elytraFlying;//movement.pose() == Pose.FALL_FLYING;
    if (flyingWithElytra) {
      movement.pastElytraFlying = 0;
    } else {
      movement.pastElytraFlying++;
    }
//    if (movement.onGround && movement.elytraFlying) {
//      player.sendMessage(ChatColor.RED + "Deactivated elytra flying");
//      movement.elytraFlying = false;
//    }
    if (movement.inWeb) {
      movement.pastInWeb = 0;
    } else {
      movement.pastInWeb++;
    }
    if (inventoryData.inventoryOpen()) {
      movement.pastInventoryOpen = 0;
    } else {
      movement.pastInventoryOpen++;
    }
    if (movement.physicsJumped) {
      movement.lastJump = System.currentTimeMillis();
    }
    if (movement.isSneaking()) {
      movement.ticksSneaking++;
      if (movement.ticksSneaking > 1) {
        movement.lastSneakingTimestamps = System.currentTimeMillis();
      }
    } else {
      movement.ticksSneaking = 0;
    }
    movement.pastBlockPlacement++;
    inventoryData.pastSlotSwitch++;
    inventoryData.pastHotBarSlotChange++;
    inventoryData.pastItemUsageTransition++;
    movement.pastWaterMovement++;
    movement.pastLavaMovement++;
    movement.pastVelocity++;
    movement.pastStep++;
    movement.pastEdgeSneak++;
    if (movement.inWater) {
      movement.waterTicks++;
    } else {
      movement.waterTicks = 0;
    }
    movement.ignoredAttackReduce = false;
    if (hasMovement || hasRotation) {
      movement.pastExternalVelocity++;
    }
    movement.pastLongTeleport++;
    abilityData.ticksToLastHealthUpdate++;
    movement.physicsUnpredictableVelocityExpected = false;
    movement.step = false;
    movement.lastSprinting = movement.sprintingAllowed();
    movement.lastSneaking = movement.sneaking;
    movement.fireworkRocketsTicks++;
    movement.attachVehicleTicks++;

    if (!inventoryData.handActive() && inventoryData.pastHandActiveTicks > 2) {
      movement.physicsEatingSlotSwitchVL = 0;
    }

    if (!event.isCancelled() /*&& !movement.isTeleportConfirmationPacket && !movement.dropPostTickMotionProcessing*/) {
      movement.lastOnGround = movement.onGround;
      movement.verifiedPositionX = movement.positionX;
      movement.verifiedPositionY = movement.positionY;
      movement.verifiedPositionZ = movement.positionZ;
    }

    if (inventoryData.handActive()) {
      inventoryData.handActiveTicks++;
      inventoryData.pastHandActiveTicks = 0;
    } else {
      inventoryData.pastHandActiveTicks++;
      inventoryData.handActiveTicks = 0;
    }

    if (movement.disabledFlying || !abilityData.allowFlying()) {
      abilityData.setFlying(false);
      movement.disabledFlying = false;
    }

    updateSize(user);
    movement.externalKeyApply = false;

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

        String finalMessage = message;
        Synchronizer.synchronize(() -> {
          player.sendMessage(finalMessage);
        });
      }
      clientDebugData.clear();
      serverDebugData.clear();
    }
  }

  private void updateSize(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    Pose pose = movementData.pose();
    movementData.width = pose.width(user);
    movementData.height = pose.height(user);
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
    List<WrappedWatchableObject> watchableObjects = reader.metadataObjects();

    WrappedWatchableObject elytraObject = watchableObjects
      .stream()
      .filter(wrappedWatchableObject -> wrappedWatchableObject.getIndex() == 0)
      .findFirst()
      .orElse(null);

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

    byte data = (byte) elytraObject.getValue();
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

  @BukkitEventSubscription(
    priority = EventPriority.LOWEST,
    ignoreCancelled = false // this is correct
  )
  public void preventVanillaFallDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    User user = UserRepository.userOf((Player) event.getEntity());
    MovementMetadata movementData = user.meta().movement();
    if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !movementData.dealCustomFallDamage) {
      movementData.seenFallDamage = (float) event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = {
      ENTITY_VELOCITY
    }
  )
  public void sentVelocityPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    StructureModifier<Integer> integers = packet.getIntegers();

    if (packet.getIntegers().readSafely(0) == player.getEntityId()) {
      Vector velocity = new Vector(
        integers.readSafely(1) / 8000d,
        integers.readSafely(2) / 8000d,
        integers.readSafely(3) / 8000d
      );
      if (IntaveControl.DEBUG_VELOCITY_RECEIVE) {
        player.sendMessage("§a" + MathHelper.formatMotion(velocity));
      }
      User user = UserRepository.userOf(player);
      MetadataBundle meta = user.meta();
      MovementMetadata movementData = meta.movement();
      if (movementData.willReceiveSetbackVelocity && velocity.length() < 0.001) {
        movementData.willReceiveSetbackVelocity = false;
        velocity = movementData.setbackOverrideVelocity;
        integers.writeSafely(1, (int) (velocity.getX() * 8000d));
        integers.writeSafely(2, (int) (velocity.getY() * 8000d));
        integers.writeSafely(3, (int) (velocity.getZ() * 8000d));
        return;
      }
      /*
        Some players abuse "velocity buffering", giving them the ability to jump up to 40 - 50 blocks (provided they have external help).
        This fix is an attempt to decrease this bugs effectiveness, neither perfect nor sustainable, but somewhat working
       */
      int pendingVelocityPackets = movementData.pendingVelocityPackets.get();
      if (pendingVelocityPackets > 1 && user.meta().attack().wasRecentlyAttackedByEntity()) {
        if (pendingVelocityPackets < 6) {
          velocity.setX(velocity.getX() / pendingVelocityPackets);
          velocity.setY(Math.min(0, velocity.getY()));
          velocity.setZ(velocity.getZ() / pendingVelocityPackets);
          integers.writeSafely(1, (int) (velocity.getX() * 8000d));
          integers.writeSafely(2, (int) (velocity.getY() * 8000d));
          integers.writeSafely(3, (int) (velocity.getZ() * 8000d));
        } else {
          if (event.isReadOnly()) {
            event.setReadOnly(false);
          }
          event.setCancelled(true);
          return;
        }
      }
      movementData.pendingVelocityPackets.incrementAndGet();
      movementData.emulationVelocity = velocity.clone();
      if (movementData.sneaking) {
        movementData.sneakPatchVelocity = velocity.clone();
      }
      Motion motion = Motion.fromVector(velocity);
      if (Physics.USE_SUPERPOSITIONS) {
        movementData.velocitySuperposition().stateSynchronize(event, motion);
      } else {
//        Modules.feedback().synchronize(player, velocity, this::receiveVelocity);
        Vector finalVelocity = velocity;
        user.tickFeedback(() -> receiveVelocity(player, finalVelocity));
      }
    }
  }

  private void receiveVelocity(Player player, Vector velocity) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();
    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.baseMotionXBeforeVelocity = movementData.baseMotionX;
      movementData.baseMotionYBeforeVelocity = movementData.baseMotionY;
      movementData.baseMotionZBeforeVelocity = movementData.baseMotionZ;
      movementData.baseMotionX = velocity.getX();
      movementData.baseMotionY = velocity.getY();
      movementData.baseMotionZ = velocity.getZ();
      movementData.lastVelocity = velocity.clone();
      if (!movementData.willReceiveSetbackVelocity) {
        movementData.pastExternalVelocity = 0;
      }
      movementData.willReceiveSetbackVelocity = false;
    }
    Synchronizer.synchronize(() -> movementData.emulationVelocity = null);
    movementData.pastVelocity = 0;
    movementData.pendingVelocityPackets.decrementAndGet();
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
          NativeVector directionVec = facing.directionVector();
          BoundingBox pistonCollisionArea = new BoundingBox(0, 0, 0, 1.1f, 1.1f, 1.1f);
          int expectedPistonX = (int) directionVec.xCoord + blockPosition.getX();
          int expectedPistonY = (int) directionVec.yCoord + blockPosition.getY();
          int expectedPistonZ = (int) directionVec.zCoord + blockPosition.getZ();
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
                float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.xCoord);
                movement.pistonHorizontalAllowance = xOffset + horizontalBoundingBoxFactor + 0.05f;
                break;
              }
              case Z_AXIS: {
                // Magical hack to get the proper bounding box factor
                float horizontalBoundingBoxFactor = (float) (user.meta().movement().width() / 2f * directionVec.zCoord);
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
        System.out.println("[Intave] Item Usage Tick");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(user.player(), "(DEBUG/MOVEMENTIGNORE) Item Usage Tick");
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
    PunishmentMetadata punishmentData = meta.punishment();
    switch (reader.playerAction()) {
      case START_SPRINTING:
        if (allowSprinting(user)) {
          movementData.setSprinting(true);
          if (IntaveControl.DEBUG_PLAYER_ACTIONS) {
            user.player().sendMessage(ChatColor.WHITE + "Start sprinting");
          }
        }
        break;
      case STOP_SPRINTING:
        movementData.setSprinting(false);
        if (IntaveControl.DEBUG_PLAYER_ACTIONS) {
          user.player().sendMessage(ChatColor.BLACK + "Stop sprinting");
        }
        break;
      case PRESS_SHIFT_KEY:
      case START_SNEAKING:
        if (System.currentTimeMillis() - punishmentData.timeLastSneakToggleCancel < 2000) {
          cancelable.setCancelled(true);
        }
        movementData.pastVehicleExitTicks = 0;
        if (movementData.isInVehicle()) {
          movementData.dismountRidingEntity("Sneak exit");
          movementData.sneaking = false;
        } else {
          movementData.sneaking = true;
        }
        if (IntaveControl.DEBUG_PLAYER_ACTIONS) {
          user.player().sendMessage(ChatColor.GREEN + "Start sneaking " + movementData.sneaking);
        }
//        player.sendMessage("Sneaking: " + movementData.isSneaking());
//        movementData.setPose(movementData.isSneaking() ? Pose.CROUCHING : Pose.STANDING);
        break;
      case RELEASE_SHIFT_KEY:
      case STOP_SNEAKING:
        movementData.sneaking = false;
        if (IntaveControl.DEBUG_PLAYER_ACTIONS) {
          user.player().sendMessage(ChatColor.RED + "Stop sneaking");
        }
//        player.sendMessage("Sneaking: " + movementData.isSneaking());
//        movementData.setPose(Pose.STANDING);
        break;
      case START_FALL_FLYING:
        if (movementData.hasElytraEquipped() && protocol.canUseElytra()) {
          if (protocol.serversideElytra()) {
            movementData.elytraFlying = true;
            if (IntaveControl.DEBUG_ELYTRA) {
              user.player().sendMessage(ChatColor.GREEN + "Activated elytra flying (START_FALL_FLYING)");
            }
            movementData.setPose(Pose.FALL_FLYING);
          }
        }
    }
  }

  private boolean allowSprinting(User user) {
    return !user.meta().inventory().inventoryOpen();
  }

  public static void applyVelocitySuperposition(User user, Motion velocity) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();

    movementData.pastExternalVelocityResetCache = movementData.pastExternalVelocity;
    movementData.baseMotionXBeforeVelocityResetCache = movementData.baseMotionXBeforeVelocity;
    movementData.baseMotionYBeforeVelocityResetCache = movementData.baseMotionYBeforeVelocity;
    movementData.baseMotionZBeforeVelocityResetCache = movementData.baseMotionZBeforeVelocity;
    movementData.baseMotionXResetCache = movementData.baseMotionX;
    movementData.baseMotionYResetCache = movementData.baseMotionY;
    movementData.baseMotionZResetCache = movementData.baseMotionZ;
    movementData.willReceiveSetbackVelocityResetCache = movementData.willReceiveSetbackVelocity;

    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.baseMotionXBeforeVelocity = movementData.baseMotionX;
      movementData.baseMotionYBeforeVelocity = movementData.baseMotionY;
      movementData.baseMotionZBeforeVelocity = movementData.baseMotionZ;
      movementData.baseMotionX = velocity.motionX();
      movementData.baseMotionY = velocity.motionY();
      movementData.baseMotionZ = velocity.motionZ();
//      user.player().sendMessage("Applied velocity " + velocity);
      movementData.lastVelocity = new Vector(velocity.motionX(), velocity.motionY(), velocity.motionZ());
    }
  }

  public static void collapseVelocitySuperposition(User user, @Nullable Motion velocity) {
    if (velocity != null) {
      MetadataBundle meta = user.meta();
      MovementMetadata movementData = meta.movement();
      Synchronizer.synchronize(() -> movementData.emulationVelocity = null);
      movementData.pastVelocity = 0;
      movementData.pendingVelocityPackets.decrementAndGet();
      if (!movementData.willReceiveSetbackVelocity) {
        movementData.pastExternalVelocity = 0;
      }
      movementData.willReceiveSetbackVelocity = false;
//      user.player().sendMessage("Collapsed velocity " + velocity);
//      if (!movementData.willReceiveSetbackVelocity) {
//        movementData.pastExternalVelocity = 0;
//      }
//      movementData.willReceiveSetbackVelocity = false;
    }
  }

  public static void resetVelocitySuperposition(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();

    movementData.pastExternalVelocity = movementData.pastExternalVelocityResetCache;
    movementData.baseMotionXBeforeVelocity = movementData.baseMotionXBeforeVelocityResetCache;
    movementData.baseMotionYBeforeVelocity = movementData.baseMotionYBeforeVelocityResetCache;
    movementData.baseMotionZBeforeVelocity = movementData.baseMotionZBeforeVelocityResetCache;
    movementData.baseMotionX = movementData.baseMotionXResetCache;
    movementData.baseMotionY = movementData.baseMotionYResetCache;
    movementData.baseMotionZ = movementData.baseMotionZResetCache;
    movementData.willReceiveSetbackVelocity = movementData.willReceiveSetbackVelocityResetCache;
  }
}