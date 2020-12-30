package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.client.PlayerMovementLocaleHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.collision.CollisionFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.util.Vector;

public final class MovementDispatcher implements EventProcessor {
  private final TeleportPositionObserver teleportPositionObserver = new TeleportPositionObserver();

  private final IntavePlugin plugin;
  private final Physics physicsCheck;

  public MovementDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
    this.physicsCheck = plugin.checkService().searchCheck(Physics.class);
    linkTeleportObserver(plugin);
  }

  private void linkTeleportObserver(IntavePlugin plugin) {
    PacketSubscriptionLinker subscriptionLinker = plugin.packetSubscriptionLinker();
    subscriptionLinker.linkSubscriptionsIn(teleportPositionObserver);
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
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.pastPlayerAttackPhysics = 0;
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
//    priority = ListenerPriority.HIGH,
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
    UserMetaAbilityData abilityData = meta.abilityData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    boolean hasMovement = packet.getBooleans().read(1);
    boolean hasRotation = packet.getBooleans().read(2);

    movementData.updateMovement(packet, hasMovement, hasRotation);
    teleportPositionObserver.receiveMovement(event);

    if (movementData.awaitTeleport) {
//      event.setCancelled(true);
      return;
    }

    if (violationLevelData.isInActiveTeleportBundle) {
      event.setCancelled(true);
      return;
    }

    physicsCheck.receiveMovement(user, hasMovement);
    movementData.applyGroundInformationToPacket(packet);

    if (!movementData.teleport) {
      // Check calls

      updatePotionEffects(user);
    }

    // flag -> remove packet
    if (movementData.invalidMovement) {
      event.setCancelled(true);
      return;
    }

    movementData.invalidMovement = false;
    movementData.teleport = false;

    boolean flyingWithElytra = PlayerMovementLocaleHelper.flyingWithElytra(player);
    if (flyingWithElytra) {
      movementData.pastElytraFlying = 0;
    } else {
      movementData.pastElytraFlying++;
    }
    inventoryData.pastHotBarSlotChange++;
    inventoryData.pastItemUsageTransition++;
    movementData.pastWaterMovement++;
    movementData.pastVelocity++;

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
    Player player = user.bukkitPlayer();
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
      if (CollisionFactory.getCollisionBoxes(user.bukkitPlayer(), boundingBox).isEmpty()) {
        movementData.width = width;
        movementData.height = height;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_VELOCITY")
    }
  )
  public void sentVelocityPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    StructureModifier<Integer> integers = packet.getIntegers();
    Vector velocity = new Vector(
      integers.readSafely(1) / 8000d,
      integers.readSafely(2) / 8000d,
      integers.readSafely(3) / 8000d
    );
    if (packet.getEntityModifier(event).readSafely(0) == player) {

      // ignore setback velocity packet
      User user1 = UserRepository.userOf(player);
      if(user1.meta().violationLevelData().isInActiveTeleportBundle) {
        return;
      }

      plugin.eventService().transactionFeedbackService().requestPong(player, velocity, (player1, velocity1) -> {
        User user = UserRepository.userOf(player1);
        UserMetaMovementData movementData = user.meta().movementData();
        movementData.physicsLastMotionX = velocity1.getX();
        movementData.physicsLastMotionY = velocity1.getY();
        movementData.physicsLastMotionZ = velocity1.getZ();
        movementData.pastVelocity = 0;
      });
    }
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
}