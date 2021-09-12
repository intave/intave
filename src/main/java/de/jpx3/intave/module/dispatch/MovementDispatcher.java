package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackCallback;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.converter.PlayerActionResolver;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.violation.Violation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.List;

import static de.jpx3.intave.module.feedback.TransactionOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_16;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

@Relocate
public final class MovementDispatcher extends Module {
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
    MovementMetadata movementData = meta.movement();
    movementData.artificialFallDistance = 0;
    movementData.dismountRidingEntity();
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
    BoundingBox bb = BoundingBox.fromPosition(user, respawnLocation);
    while (respawnLocation.getY() < 256 && Collision.present(player, bb)) {
      respawnLocation.add(0, 1, 0);
      bb = BoundingBox.fromPosition(user, respawnLocation);
    }
    respawn.setRespawnLocation(respawnLocation);
  }

  @BukkitEventSubscription
  public void receiveWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    movementData.updateWorld();
    user.blockShapeAccess().identityInvalidate();
    user.refreshSprintState();
  }

  @BukkitEventSubscription
  public void receiveVehicleMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    if (!movementData.hasRidingEntity()) {
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
    Modules.feedback()
      .synchronize(player, UserRepository.userOf(player), (p, user) -> {
        MovementMetadata movement = user.meta().movement();
        ProtocolMetadata protocol = user.meta().protocol();
        movement.sneaking = false;
        movement.setSprinting(false);
        if (protocol.protocolVersion() >= VER_1_16) {
          movement.sprintReset();
          user.refreshSprintState();
        }
        movement.physicsMotionX = 0;
        movement.physicsMotionY = 0;
        movement.physicsMotionZ = 0;
        user.blockShapeAccess().identityInvalidate();
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
    PacketContainer packet = event.getPacket();
    Modules.feedback().synchronize(player, packet.getFloat(), (player1, floats) -> {
      User user = UserRepository.userOf(player1);
      MovementMetadata movementData = user.meta().movement();
      Float motionX = floats.read(1);
      Float motionY = floats.read(2);
      Float motionZ = floats.read(3);
      movementData.physicsMotionX += motionX;
      movementData.physicsMotionY += motionY;
      movementData.physicsMotionZ += motionZ;
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
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
    ProtocolMetadata clientData = meta.protocol();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;

    boolean hasRotation = vehicleMove || packet.getBooleans().read(2);
    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);

    if (movementData.hasRidingEntity() && !vehicleMove && hasRotation && !hasMovement) {
      movementData.applyGroundInformationToPacket(packet);
      movementData.rotationYaw = packet.getFloat().read(0);
      movementData.rotationPitch = packet.getFloat().read(1);
      return;
    }

    // garbage fix
    if (
      clientData.cavesAndCliffsUpdate() && !movementData.awaitTeleport && !movementData.awaitOutgoingTeleport
    ) {
      StructureModifier<Double> modifier = packet.getDoubles();
      double positionX = modifier.read(0);
      double positionY = modifier.read(1);
      double positionZ = modifier.read(2);
      double motionX = positionX - movementData.verifiedPositionX;
      double motionY = positionY - movementData.verifiedPositionY;
      double motionZ = positionZ - movementData.verifiedPositionZ;
      if (MathHelper.hypot3d(motionX, motionY, motionZ) < 0.00001) {
        movementData.dropPostTickMotionProcessing = true;
        return;
      }
    }

    connectionData.receiveMovement();
    movementData.updateMovement(packet, hasMovement, hasRotation);
    teleportApplyEnforcer.receiveMovement(event);

    if (movementData.awaitTeleport || movementData.awaitOutgoingTeleport) {
      event.setCancelled(true);
      return;
    }

    double distance = MathHelper.distanceOf(
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      movementData.positionX, movementData.positionY, movementData.positionZ
    );
    if (distance > 50) {
      event.setCancelled(true);
      Vector vector = new Vector(movementData.physicsMotionX, movementData.physicsMotionY, movementData.physicsMotionZ);
      plugin.eventService().emulationEngine().emulationSetBack(player, vector, 10, false);
      String message = "sent unsafe position";
      String details = "moved " + MathHelper.formatDouble(distance, 2) + " blocks";
      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(25)
        .build();
      plugin.violationProcessor().processViolation(violation);
      return;
    }

    WrappedEntity attachedEntity = movementData.ridingEntity();
    if (attachedEntity != null && !attachedEntity.isEntityAlive() && !attachedEntity.entityName().equals("Boat")) {
      movementData.dismountRidingEntity();
    }

    if (violationLevelData.isInActiveTeleportBundle) {
      event.setCancelled(true);
      return;
    }

    if (
      !movementData.isTeleportConfirmationPacket &&
        movementData.canResetMotion &&
        movementData.physicsMotionX == 0 &&
        movementData.physicsMotionY == 0 &&
        movementData.physicsMotionZ == 0 &&
        movementData.motionX() == 0 &&
        movementData.motionY() == 0 &&
        movementData.motionZ() == 0
    ) {
      movementData.canResetMotion = false;
      return;
    }

    if (!movementData.isTeleportConfirmationPacket) {
      interactionRaytraceCheck.receiveMovement(event);

      if (hasMovement) {
        physicsCheck.receiveMovement(user);
      } else {
        physicsCheck.updateOnGroundIfFlying(user);
      }

      timerCheck.receiveMovement(event);

      boolean clientOnGround = vehicleMove ? player.isOnGround() : packet.getBooleans().read(0);
      boolean collidedWithBoat = movementData.collidedWithBoat();

      if (!vehicleMove && !collidedWithBoat) {
        movementData.applyGroundInformationToPacket(packet);
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
      movementData.canResetMotion = true;
    }

    // flag -> remove packet
    if (movementData.invalidMovement && violationLevelData.isInActiveTeleportBundle) {
      movementData.awaitOutgoingTeleport = true; // awaiting next teleport
      event.setCancelled(true);
    }
  }

  private void updatePotionEffects(User user) {
    EffectMetadata potionData = user.meta().potions();
    if (potionData.potionEffectSpeedAmplifier() > 0) {
      if (--potionData.potionEffectSpeedDuration <= 0) {
        potionData.potionEffectSpeedAmplifier(0);
      }
    }

    if (potionData.potionEffectSlownessAmplifier() > 0) {
      if (--potionData.potionEffectSlownessDuration <= 0) {
        potionData.potionEffectSlownessAmplifier(0);
      }
    }

    if (potionData.potionEffectJumpAmplifier() > 0) {
      if (--potionData.potionEffectJumpDuration <= 0) {
        potionData.potionEffectJumpAmplifier(0);
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveFinalMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      return;
    }
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    InventoryMetadata inventoryData = meta.inventory();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);
    boolean hasRotation = vehicleMove || packet.getBooleans().read(2);

    if (movementData.awaitTeleport) {
      return;
    }

    if (movementData.hasRidingEntity() && !vehicleMove && hasRotation && !hasMovement) {
      movementData.applyGroundInformationToPacket(packet);
      return;
    }

    // onGround == true -> falldamage

    if (!event.isCancelled() && !movementData.isTeleportConfirmationPacket && !movementData.dropPostTickMotionProcessing) {
      physicsCheck.endMovement(user, hasMovement);
    }

    if (!movementData.isTeleportConfirmationPacket) {
      movementData.lastTeleport++;
    }

    movementData.invalidMovement = false;
    movementData.suspiciousMovement = false;
    movementData.isTeleportConfirmationPacket = false;
    movementData.dropPostTickMotionProcessing = false;

    boolean flyingWithElytra = movementData.pose() == Pose.FALL_FLYING;
    if (flyingWithElytra) {
      movementData.pastElytraFlying = 0;
    } else {
      movementData.pastElytraFlying++;
    }
    if (movementData.inWeb) {
      movementData.pastInWeb = 0;
    } else {
      movementData.pastInWeb++;
    }
    if (inventoryData.inventoryOpen()) {
      movementData.pastInventoryOpen = 0;
    } else {
      movementData.pastInventoryOpen++;
    }
    if (movementData.physicsJumped) {
      movementData.lastJumpTimestamps = System.currentTimeMillis();
    }
    inventoryData.pastHotBarSlotChange++;
    inventoryData.pastItemUsageTransition++;
    movementData.pastWaterMovement++;
    movementData.pastVelocity++;
    movementData.ignoredAttackReduce = false;
    movementData.pastExternalVelocity++;
    movementData.pastLongTeleport++;
    abilityData.ticksToLastHealthUpdate++;
    inventoryData.pastSlotSwitch++;
    movementData.physicsUnpredictableVelocityExpected = false;
    movementData.step = false;
    movementData.lastSprinting = movementData.sprintingAllowed();
    movementData.lastSneaking = movementData.sneaking;
    movementData.fireworkTolerant = false;

    if (!inventoryData.handActive() && inventoryData.pastHandActiveTicks > 2) {
      movementData.physicsEatingSlotSwitchVL = 0;
    }

    if (!event.isCancelled() /*&& !movementData.isTeleportConfirmationPacket && !movementData.dropPostTickMotionProcessing*/) {
      movementData.lastOnGround = movementData.onGround;
      movementData.verifiedPositionX = movementData.positionX;
      movementData.verifiedPositionY = movementData.positionY;
      movementData.verifiedPositionZ = movementData.positionZ;
    }

    if (inventoryData.handActive()) {
      inventoryData.handActiveTicks++;
      inventoryData.pastHandActiveTicks = 0;
    } else {
      inventoryData.pastHandActiveTicks++;
      inventoryData.handActiveTicks = 0;
    }

    if (movementData.disabledFlying || !abilityData.allowFlying()) {
      abilityData.setFlying(false);
      movementData.disabledFlying = false;
    }

    updateSize(user);
    movementData.externalKeyApply = false;
  }

  private void updateSize(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    Pose pose = movementData.pose();
    movementData.width = pose.width(user);
    movementData.height = pose.width(user);
    ;
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
    Boolean jumping = packet.getBooleans().read(0);
    movementData.externalKeyApply = true;
    movementData.clientStrafeKey = strafeKey;
    movementData.clientInputKey = forwardKey;
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
    FeedbackCallback<Integer> callback = (x, foodLevel) -> {
      MetadataBundle meta = user.meta();
      if (foodLevel <= 6) {
        meta.movement().sprinting = false;
      }
      meta.abilities().foodLevel = foodLevel;
    };
    Modules.feedback().synchronize(player, originalFoodLevel, callback, SELF_SYNCHRONIZATION);
  }

  private final boolean ELYTRA_SUPPORTED = MinecraftVersions.VER1_9_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_METADATA
    }
  )
  public void receiveElytraUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    if (!ELYTRA_SUPPORTED || entityID != player.getEntityId()) {
      return;
    }
    List<WrappedWatchableObject> wrappedWatchableObjects = packet.getWatchableCollectionModifier().read(0);
    WrappedWatchableObject watchableObject = wrappedWatchableObjects
      .stream()
      .filter(wrappedWatchableObject -> wrappedWatchableObject.getIndex() == 0)
      .findFirst()
      .orElse(null);
    if (watchableObject == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    if (!meta.protocol().canUseElytra()) {
      return;
    }
    byte data = (byte) watchableObject.getValue();
    boolean gliding = (data & 1 << 7) != 0;
    FeedbackCallback<Boolean> callback = (p, g) -> {
      movement.elytraFlying = g;
      movement.updatePose();
    };
    Modules.feedback().synchronize(player, gliding, callback);
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void preventVanillaFallDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    User user = UserRepository.userOf((Player) event.getEntity());
    MovementMetadata movementData = user.meta().movement();
    if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !movementData.allowFallDamage) {
      event.setCancelled(true);
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
    if (packet.getEntityModifier(event).readSafely(0) == player) {
      Vector velocity = new Vector(
        integers.readSafely(1) / 8000d,
        integers.readSafely(2) / 8000d,
        integers.readSafely(3) / 8000d
      );

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
        This fix is an attempt to decrease this bugs effectiveness, neither perfect nor sustainable, but at least working to a certain degree
       */
      int pendingVelocityPackets = movementData.pendingVelocityPackets.get();
      if(pendingVelocityPackets > 1) {
        if (pendingVelocityPackets < 6) {
          velocity.setX(velocity.getX() / pendingVelocityPackets);
          velocity.setY(Math.min(0, velocity.getY()));
          velocity.setZ(velocity.getZ() / pendingVelocityPackets);
          integers.writeSafely(1, (int) (velocity.getX() * 8000d));
          integers.writeSafely(2, (int) (velocity.getY() * 8000d));
          integers.writeSafely(3, (int) (velocity.getZ() * 8000d));
        } else {
          if (!event.isReadOnly()) {
            event.setCancelled(true);
            return;
          }
        }
      }

      movementData.pendingVelocityPackets.incrementAndGet();
      movementData.emulationVelocity = velocity.clone();
      Modules.feedback().synchronize(player, velocity, this::receiveVelocity);
    }
  }

  private void receiveVelocity(Player player, Vector velocity) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();
    if (!violationLevelData.isInActiveTeleportBundle) {
      movementData.physicsMotionXBeforeVelocity = movementData.physicsMotionX;
      movementData.physicsMotionYBeforeVelocity = movementData.physicsMotionY;
      movementData.physicsMotionZBeforeVelocity = movementData.physicsMotionZ;
      movementData.physicsMotionX = velocity.getX();
      movementData.physicsMotionY = velocity.getY();
      movementData.physicsMotionZ = velocity.getZ();
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

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION
    }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    PunishmentMetadata punishmentData = meta.punishment();
    PacketContainer packet = event.getPacket();
    PlayerAction playerAction = PlayerActionResolver.resolveActionFromPacket(packet);
    switch (playerAction) {
      case START_SPRINTING:
        if (allowSprinting(player)) {
          movementData.setSprinting(true);
        }
        break;
      case STOP_SPRINTING:
        movementData.setSprinting(false);
        break;
      case PRESS_SHIFT_KEY:
      case START_SNEAKING:
        if (System.currentTimeMillis() - punishmentData.timeLastSneakToggleCancel < 1000) {
          event.setCancelled(true);
        }
        if (movementData.hasRidingEntity()) {
          movementData.dismountRidingEntity();
          movementData.sneaking = false;
        }
        movementData.lastSneakingTimestamps = System.currentTimeMillis();
        movementData.sneaking = true;
        break;
      case RELEASE_SHIFT_KEY:
      case STOP_SNEAKING:
        movementData.sneaking = false;
        break;
      case START_FALL_FLYING:
        if (movementData.hasElytraEquipped() && clientData.canUseElytra()) {
          movementData.elytraFlying = true;
          movementData.setPose(Pose.FALL_FLYING);
        }
    }
  }

  private boolean allowSprinting(Player player) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    InventoryMetadata inventoryData = meta.inventory();
    return !inventoryData.inventoryOpen();
  }
}