package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.Timer;
import de.jpx3.intave.detect.checks.world.InteractionRaytrace;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.client.PoseHelper;
import de.jpx3.intave.tools.packet.PlayerAction;
import de.jpx3.intave.tools.packet.PlayerActionResolver;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;

@Relocate
public final class MovementDispatcher implements EventProcessor {
  private final TeleportPositionObserver teleportPositionObserver = new TeleportPositionObserver();

  private final IntavePlugin plugin;
  private final Physics physicsCheck;
  private final InteractionRaytrace interactionRaytraceCheck;
  private final Timer timerCheck;

  public MovementDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
    this.physicsCheck = plugin.checkService().searchCheck(Physics.class);
    this.interactionRaytraceCheck = plugin.checkService().searchCheck(InteractionRaytrace.class);
    this.timerCheck = plugin.checkService().searchCheck(Timer.class);
    linkTeleportObserver(plugin);
  }

  private void linkTeleportObserver(IntavePlugin plugin) {
    PacketSubscriptionLinker subscriptionLinker = plugin.packetSubscriptionLinker();
    subscriptionLinker.linkSubscriptionsIn(teleportPositionObserver);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "SCOREBOARD_TEAM")
    }
  )
  public void applyNoEntityCollisionRule(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    StructureModifier<String> strings = packet.getStrings();


  }

  @BukkitEventSubscription
  public void receiveExternalTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PlayerTeleportEvent.TeleportCause cause = event.getCause();
    if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
      return;
    }
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.artificialFallDistance = 0;
  }

  @BukkitEventSubscription
  public void receiveRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaPotionData potionData = meta.potionData();
    UserMetaMovementData movementData = meta.movementData();
    movementData.artificialFallDistance = 0;
    potionData.clearPotionEffects();
  }

  @BukkitEventSubscription
  public void receiveWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.updateWorld();
  }

  @BukkitEventSubscription
  public void receiveVehicleMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    if (!movementData.inVehicle()) {
      return;
    }
    Location location = event.getTo();
    UserMetaClientData clientData = meta.clientData();
    if (clientData.protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE) {
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
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "RESPAWN")
    }
  )
  public void sentRespawn(PacketEvent event) {
    Player player = event.getPlayer();
    synchronizeRespawn(player);
  }

  private void synchronizeRespawn(Player player) {
    User user = UserRepository.userOf(player);
    plugin.eventService()
      .transactionFeedbackService()
      .requestPong(player, user.meta().movementData(), (p, movementData) -> {
        movementData.sneaking = false;
        movementData.sprinting = false;
        movementData.physicsMotionX = 0;
        movementData.physicsMotionY = 0;
        movementData.physicsMotionZ = 0;
      });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "EXPLOSION"),
    }
  )
  public void sentExplosion(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    plugin.eventService().transactionFeedbackService().requestPong(player, packet.getFloat(), (player1, floats) -> {
      User user = UserRepository.userOf(player1);
      UserMetaMovementData movementData = user.meta().movementData();
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
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "VEHICLE_MOVE")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      return;
    }
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;

    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);
    boolean hasRotation = vehicleMove || packet.getBooleans().read(2);

    movementData.updateMovement(packet, hasMovement, hasRotation);
    teleportPositionObserver.receiveMovement(event);

    timerCheck.receiveMovement(event, movementData.isTeleportConfirmationPacket);

    if (movementData.awaitTeleport) {
      event.setCancelled(true);
      return;
    }

    double distance = MathHelper.resolveDistance(
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      movementData.positionX, movementData.positionY, movementData.positionZ
    );
    if (distance > 50) {
      event.setCancelled(true);
      Vector vector = new Vector(movementData.physicsMotionX, movementData.physicsMotionY, movementData.physicsMotionZ);
      plugin.eventService().emulationEngine().emulationSetBack(player, vector, 10);
      String details = "moved " + MathHelper.formatDouble(distance, 2) + " blocks";
      plugin.violationProcessor().processViolation(player, 25, "Physics", "sent unsafe position", details);
      return;
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
      Boolean clientOnGround = packet.getBooleans().read(0);

      if (!vehicleMove) {
        movementData.applyGroundInformationToPacket(packet);
      }

      if (movementData.onGround && !clientOnGround && movementData.step) {
        movementData.onGround = false;
      }

      timerCheck.checkSetback(event);
      attackData.updatePerfectRotation();

      if (inventoryData.awaitingSlotSet != -1) {
        Synchronizer.synchronize(() -> {
          int awaitingSlotSet = inventoryData.awaitingSlotSet;
          if (awaitingSlotSet != -1) {
            player.getInventory().setHeldItemSlot(awaitingSlotSet);
            inventoryData.awaitingSlotSet = -1;
          }
        });
      }
      updatePotionEffects(user);
      movementData.canResetMotion = false;
    } else {
      movementData.canResetMotion = true;
    }

    // flag -> remove packet
    if (movementData.invalidMovement && violationLevelData.isInActiveTeleportBundle) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "VEHICLE_MOVE")
    }
  )
  public void receiveFinalMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      return;
    }
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAbilityData abilityData = meta.abilityData();
    UserMetaInventoryData inventoryData = meta.inventoryData();

    PacketType packetType = event.getPacketType();
    boolean vehicleMove = packetType == PacketType.Play.Client.VEHICLE_MOVE;
    boolean hasMovement = vehicleMove || packet.getBooleans().read(1);

    if (movementData.awaitTeleport) {
      return;
    }

    // onGround == true -> falldamage

    if (!event.isCancelled() && !movementData.isTeleportConfirmationPacket) {
      physicsCheck.endMovement(user, hasMovement);
    }

    if (!movementData.isTeleportConfirmationPacket) {
      movementData.lastTeleport++;
    }

    movementData.invalidMovement = false;
    movementData.suspiciousMovement = false;
    movementData.isTeleportConfirmationPacket = false;

    boolean flyingWithElytra = PoseHelper.flyingWithElytra(player);
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
    inventoryData.pastHotBarSlotChange++;
    inventoryData.pastItemUsageTransition++;
    movementData.pastWaterMovement++;
    movementData.pastVelocity++;
    movementData.ignoredAttackReduce = false;
    movementData.pastExternalVelocity++;
    movementData.pastLongTeleport++;
    movementData.physicsUnpredictableVelocityExpected = false;
    movementData.step = false;
    movementData.lastSprinting = movementData.sprintingAllowed();
    movementData.lastSneaking = movementData.sneaking;

    if (!inventoryData.handActive()) {
      movementData.physicsEatingSlotSwitchVL = 0;
    }

    if (!event.isCancelled() && !movementData.isTeleportConfirmationPacket) {
      movementData.lastOnGround = movementData.onGround;
      movementData.verifiedPositionX = movementData.positionX;
      movementData.verifiedPositionY = movementData.positionY;
      movementData.verifiedPositionZ = movementData.positionZ;
    }

    if (inventoryData.handActive()) {
      inventoryData.handActiveTicks++;
    } else {
      inventoryData.handActiveTicks = 0;
    }

    if (movementData.disabledFlying || !abilityData.allowFlying()) {
      abilityData.setFlying(false);
      movementData.disabledFlying = false;
    }

    updateSize(user);
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void preventVanillaFallDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    User user = UserRepository.userOf((Player) event.getEntity());
    UserMetaMovementData movementData = user.meta().movementData();
    if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !movementData.allowFallDamage) {
      event.setCancelled(true);
    }
  }

  private void updatePotionEffects(User user) {
    UserMetaPotionData potionData = user.meta().potionData();
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

  private void updateSize(User user) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    float width;
    float height;
    if (movementData.elytraFlying) {
      width = 0.6F;
      height = 0.6F;
    } else if (player.isSleeping()) {
      width = 0.2F;
      height = 0.2F;
    } else if (!movementData.swimming) {
      width = 0.6F;
      if(movementData.sneaking) {
        height = meta.clientData().hitBoxHeightWhenSneaking();
      } else {
        height = 1.8F;
      }
    } else {
      width = 0.6F;
      height = 0.6F;
    }
    if (width != movementData.width || height != movementData.height) {
      WrappedAxisAlignedBB boundingBox = movementData.boundingBox();
      boundingBox = new WrappedAxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.minX + (double) width, boundingBox.minY + (double) height, boundingBox.minZ + (double) width);
      if (Collision.resolve(user.player(), boundingBox).isEmpty()) {
        movementData.width = width;
        movementData.height = height;
        movementData.applySizeUpdate();
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    prioritySlot = PrioritySlot.EXTERNAL,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_VELOCITY")
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
      User.UserMeta meta = user.meta();
      UserMetaMovementData movementData = meta.movementData();

      if (movementData.willReceiveSetbackVelocity && velocity.length() < 0.001) {
        movementData.willReceiveSetbackVelocity = false;
        velocity = movementData.setbackOverrideVelocity;
        integers.writeSafely(1, (int) (velocity.getX() * 8000d));
        integers.writeSafely(2, (int) (velocity.getY() * 8000d));
        integers.writeSafely(3, (int) (velocity.getZ() * 8000d));
        return;
      }

      movementData.emulationVelocity = velocity.clone();
      plugin.eventService().transactionFeedbackService().requestPong(player, velocity, this::receiveVelocity);
    }
  }

  private void receiveVelocity(Player player, Vector velocity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaMovementData movementData = meta.movementData();
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
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "ENTITY_ACTION")
    }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    PacketContainer packet = event.getPacket();
    PlayerAction playerAction = PlayerActionResolver.resolveActionFromPacket(packet);
    switch (playerAction) {
      case START_SPRINTING:
        if (allowSprinting(player)) {
          movementData.sprinting = true;
        }
        break;
      case STOP_SPRINTING:
        movementData.sprinting = false;
        break;
      case START_SNEAKING:
        movementData.sneaking = true;
        break;
      case STOP_SNEAKING:
        movementData.sneaking = false;
        break;
    }
  }

  private boolean allowSprinting(Player player) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    return !inventoryData.inventoryOpen();
  }
}