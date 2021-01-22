package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerMovementPoseHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class MovementDispatcher implements EventProcessor {
  private final TeleportPositionObserver teleportPositionObserver = new TeleportPositionObserver();

  private final IntavePlugin plugin;
  private final Physics physicsCheck;
  private MethodHandle fallDamageInvokeMethod;

  public MovementDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
    this.physicsCheck = plugin.checkService().searchCheck(Physics.class);
    linkTeleportObserver(plugin);
    linkFallDamageInvokeMethod();
  }

  private void linkTeleportObserver(IntavePlugin plugin) {
    PacketSubscriptionLinker subscriptionLinker = plugin.packetSubscriptionLinker();
    subscriptionLinker.linkSubscriptionsIn(teleportPositionObserver);
  }

  private void linkFallDamageInvokeMethod() {
    Class<?> entityLivingClass = ReflectiveAccess.lookupServerClass("EntityLiving");
    String methodName = "e";
    if (ProtocolLibAdapter.VILLAGE_UPDATE.atOrAbove()) {
      methodName = "b";
    } else if (ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      methodName = "c";
    }
    try {
      this.fallDamageInvokeMethod = MethodHandles
        .publicLookup()
        .findVirtual(entityLivingClass, methodName, MethodType.methodType(Void.TYPE, Float.TYPE, Float.TYPE));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  @BukkitEventSubscription
  public void receiveWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.updateWorld();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "RESPAWN")
    }
  )
  public void sentRespawn(PacketEvent event) {
    Player player = event.getPlayer();
    Synchronizer.synchronizeDelayed(() -> synchronizeRespawn(player), 1);
  }

  private void synchronizeRespawn(Player player) {
    User user = UserRepository.userOf(player);
    plugin.eventService()
      .transactionFeedbackService()
      .requestPong(player, user.meta().movementData(), (p, movementData) -> {
        movementData.sneaking = false;
        movementData.sprinting = false;
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
      movementData.physicsLastMotionX += motionX;
      movementData.physicsLastMotionY += motionY;
      movementData.physicsLastMotionZ += motionZ;
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    boolean hasMovement = packet.getBooleans().read(1);
    boolean hasRotation = packet.getBooleans().read(2);

    movementData.updateMovement(packet, hasMovement, hasRotation);
    teleportPositionObserver.receiveMovement(event);

    if (movementData.awaitTeleport) {
      event.setCancelled(true);
      return;
    }

    double distance = MathHelper.resolveDistance(
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      movementData.positionX, movementData.positionY, movementData.positionZ
    );
    if (distance > 10) {
      event.setCancelled(true);
      Vector vector = new Vector(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ);
      plugin.eventService().emulationEngine().emulationSetBack(player, vector, 10);
      String details = "moved " + MathHelper.formatDouble(distance, 2) + " blocks";
      plugin.retributionService().processViolation(player, 25, "Physics", "sent unsafe position", details);
      return;
    }

    if (violationLevelData.isInActiveTeleportBundle) {
      event.setCancelled(true);
      return;
    }

    if (
      !movementData.isTeleportConfirmationPacket &&
        movementData.canResetMotion &&
        movementData.physicsLastMotionX == 0 &&
        movementData.physicsLastMotionY == 0 &&
        movementData.physicsLastMotionZ == 0 &&
        movementData.motionX() == 0 &&
        movementData.motionY() == 0 &&
        movementData.motionZ() == 0
    ) {
      movementData.canResetMotion = false;
      return;
    }

    if (!movementData.isTeleportConfirmationPacket) {
      physicsCheck.receiveMovement(user, hasMovement);
    }
    movementData.applyGroundInformationToPacket(packet);

    if (!movementData.isTeleportConfirmationPacket) {

      // delete velocity cache if not used
//      if(!violationLevelData.isInActiveTeleportBundle && !movementData.invalidMovement) {
//        if(movementData.emulationVelocity != null) {
//          Bukkit.broadcastMessage("Delete emulation space " + movementData.emulationVelocity);
//          movementData.emulationVelocity = null;
//        }
//      }

      attackData.updatePerfectRotation();
      // Check calls

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
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveFinalMovement(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);

    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAbilityData abilityData = meta.abilityData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    boolean hasMovement = packet.getBooleans().read(1);

    if (movementData.awaitTeleport) {
      return;
    }

    // onGround == true -> falldamage

    if (!event.isCancelled() && !movementData.isTeleportConfirmationPacket) {
      physicsCheck.endMovement(user, hasMovement);
    }

    if (!violationLevelData.isInActiveTeleportBundle && !movementData.lastOnGround && movementData.motionY() < 0) {
      movementData.artificialFallDistance += -movementData.motionY();
    }

    if (movementData.onGround) {
      try {
        if (movementData.artificialFallDistance > 3.4) {
          movementData.allowFallDamage = true;
          fallDamageInvokeMethod.invoke(user.playerHandle(), movementData.artificialFallDistance, 1.0f);
          movementData.allowFallDamage = false;
        }
      } catch (Throwable throwable) {
        throwable.printStackTrace();
      }
      movementData.artificialFallDistance = 0;
    }

    if (!movementData.isTeleportConfirmationPacket) {
      movementData.lastTeleport++;
    }

    movementData.invalidMovement = false;
    movementData.suspiciousMovement = false;
    movementData.isTeleportConfirmationPacket = false;

    boolean flyingWithElytra = PlayerMovementPoseHelper.flyingWithElytra(player);
    if (flyingWithElytra) {
      movementData.pastElytraFlying = 0;
    } else {
      movementData.pastElytraFlying++;
    }
    inventoryData.pastHotBarSlotChange++;
    inventoryData.pastItemUsageTransition++;
    movementData.pastWaterMovement++;
    movementData.pastVelocity++;

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
      abilityData.flying(false);
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
      if (movementData.sneaking && meta.clientData().hitBoxSneakAffected()) {
        width = 0.6F;
        height = 1.65F;
      } else {
        width = 0.6F;
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
      UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
      boolean isInActiveTeleportBundle = violationLevelData.isInActiveTeleportBundle;

      if (movementData.willReceiveSetbackVelocity && velocity.length() < 0.001) {
        movementData.willReceiveSetbackVelocity = false;
        velocity = movementData.setbackOverrideVelocity;

        integers.writeSafely(1, (int) (velocity.getX() * 8000d));
        integers.writeSafely(2, (int) (velocity.getY() * 8000d));
        integers.writeSafely(3, (int) (velocity.getZ() * 8000d));
        return;
      }

//      Bukkit.broadcastMessage(player.getName() + ": motion update force: " + MathHelper.formatMotion(velocity) + " " + isInActiveTeleportBundle);

      //if(isInActiveTeleportBundle) {
      //}
      movementData.emulationVelocity = velocity.clone();

      VelocityCallBackData velocityCallBackData = new VelocityCallBackData(isInActiveTeleportBundle, velocity);
      plugin.eventService().transactionFeedbackService().requestPong(player, velocityCallBackData, this::receiveVelocity);
    }
  }

  private void receiveVelocity(Player player, VelocityCallBackData velocityCallBackData) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    Vector velocity = velocityCallBackData.velocity;
    if (!velocityCallBackData.isInActiveTeleportBundle) {
      movementData.physicsLastMotionX = velocity.getX();
      movementData.physicsLastMotionY = velocity.getY();
      movementData.physicsLastMotionZ = velocity.getZ();
      movementData.lastVelocity = velocity.clone();
//      player.sendMessage("Apply velocity: " + MathHelper.formatMotion(velocity));
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
    EnumWrappers.PlayerAction playerAction = packet.getPlayerActions().read(0);
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
    UserMetaMovementData movementData = meta.movementData();
//    if (movementData.sneaking && !user.meta().clientData().sprintWhenSneaking()) {
//      return false;
//    }
    return !inventoryData.inventoryOpen();
  }

  private static final class VelocityCallBackData {
    private final boolean isInActiveTeleportBundle;
    private final Vector velocity;

    public VelocityCallBackData(boolean isInActiveTeleportBundle, Vector velocity) {
      this.isInActiveTeleportBundle = isInActiveTeleportBundle;
      this.velocity = velocity;
    }
  }
}