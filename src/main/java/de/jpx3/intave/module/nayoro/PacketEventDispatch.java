package de.jpx3.intave.module.nayoro;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.event.AttackEvent;
import de.jpx3.intave.module.nayoro.event.ClickEvent;
import de.jpx3.intave.module.nayoro.event.PlayerMoveEvent;
import de.jpx3.intave.module.nayoro.event.SlotSwitchEvent;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static de.jpx3.intave.check.movement.physics.Simulators.ELYTRA;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.WINDOW_ITEMS;

public final class PacketEventDispatch implements PacketEventSubscriber {
  private final BiConsumer<? super User, Consumer<EventSink>> reverseSink;

  public PacketEventDispatch(BiConsumer<? super User, Consumer<EventSink>> sinkCallback) {
    this.reverseSink = sinkCallback;
  }

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void onClick(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ClickEvent clickEvent = ClickEvent.create();
    reverseSink.accept(user, clickEvent::accept);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void onUse(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    EntityUseReader packetReader = PacketReaders.readerOf(packet);
    if (packetReader.useAction() == EnumWrappers.EntityUseAction.ATTACK) {
      int attackerId = player.getEntityId();
      int targetId = packetReader.entityId();
      AttackEvent attackEvent = AttackEvent.create(attackerId, targetId);
      reverseSink.accept(user, attackEvent::accept);
    }
    packetReader.release();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movement = user.meta().movement();
    double x = movement.positionX;
    double y = movement.positionY;
    double z = movement.positionZ;
    double lastX = movement.lastPositionX;
    double lastY = movement.lastPositionY;
    double lastZ = movement.lastPositionZ;
    float yaw = movement.rotationYaw;
    float pitch = movement.rotationPitch;
    float lastYaw = movement.lastRotationYaw;
    float lastPitch = movement.lastRotationPitch;
    int keyStrafe = movement.keyStrafe;
    int keyForward = movement.keyForward;

    boolean collidedHorizontally = movement.collidedHorizontally;
    boolean collidedVertically = movement.collidedVertically || movement.onGround();
    boolean inWater = movement.inWater;
    boolean inLava = movement.inLava();

    boolean inVehicle = movement.isInVehicle();
    boolean sneaking = movement.isSneaking();
    boolean recentlyTeleported = movement.lastTeleport <= 3;
    boolean jumped = movement.physicsJumped;

    int movementFlags = 0;
    movementFlags |= collidedHorizontally ? 1 : 0;
    movementFlags |= collidedVertically ? 2 : 0;
    movementFlags |= inWater ? 4 : 0;
    movementFlags |= inLava ? 8 : 0;
    movementFlags |= inVehicle ? 16 : 0;
    movementFlags |= sneaking ? 32 : 0;
    movementFlags |= recentlyTeleported ? 64 : 0;
    movementFlags |= jumped ? 128 : 0;

    PlayerMoveEvent movementEvent = PlayerMoveEvent.create(
      keyStrafe, keyForward,
      x, y, z,
      yaw, pitch,
      lastX, lastY, lastZ,
      lastYaw, lastPitch,
      movementFlags,
      movement.recordedMoves++ % 200 == 0
    );
    reverseSink.accept(user, movementEvent::accept);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int slot = event.getPacket().getIntegers().read(0);
    ItemStack item = player.getInventory().getItem(slot);
    Material type;
    int amount;
    if (item != null) {
      type = item.getType();
      amount = item.getAmount();
    } else {
      type = Material.AIR;
      amount = 0;
    }
    SlotSwitchEvent slotSwitchEvent = SlotSwitchEvent.create(
      slot, type.name(), amount
    );
    reverseSink.accept(user, slotSwitchEvent::accept);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void receiveWindowClick(
    User user, WindowClickReader reader
  ) {
    Player player = user.player();
//    player.sendMessage("Window click " + reader.windowId() + " " + reader.slot() + " " + reader.clickType() + " " + reader.shiftClick());
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void sendWindowItems(
    User user, WindowItemReader reader
  ) {
    Player player = user.player();
//    player.sendMessage("Window items " + reader.windowId() + " " + reader.itemMap());
  }
}
