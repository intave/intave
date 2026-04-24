package de.jpx3.intave.module.nayoro;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.event.*;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.module.nayoro.event.WindowActionEvent.Action.CLOSE;
import static de.jpx3.intave.module.nayoro.event.WindowActionEvent.Action.INFER_OPEN;

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
  public void onClick(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ClickEvent clickEvent = ClickEvent.create();
    reverseSink.accept(user, clickEvent::accept);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void onUse(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      int attackerId = player.getEntityId();
      int targetId = packet.getEntityId();
      AttackEvent attackEvent = AttackEvent.create(attackerId, targetId);
      reverseSink.accept(user, attackEvent::accept);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(ProtocolPacketEvent event) {
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
  public void receiveHeldItemSlot(ProtocolPacketEvent event, WrapperPlayClientHeldItemChange packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int slot = packet.getSlot();
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
    User user, WrapperPlayClientClickWindow packet
  ) {
    boolean assumeWindowOpen = user.meta().connection().assumeWindowOpen;
    if (!assumeWindowOpen) {
      user.meta().connection().assumeWindowOpen = true;
      WindowActionEvent openEvent = WindowActionEvent.create(INFER_OPEN, user.player().getInventory().getArmorContents());
      reverseSink.accept(user, openEvent::accept);
    }
    WindowClickEvent clickEvent = WindowClickEvent.create(
      packet.getWindowId(), packet.getSlot(), packet.getWindowClickType().ordinal(), packet.getButton(), packet.getActionNumber().orElse(-1)
    );
    reverseSink.accept(user, clickEvent::accept);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveWindowClose(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    WindowActionEvent closeEvent = WindowActionEvent.create(CLOSE, user.player().getInventory().getArmorContents());
    reverseSink.accept(user, closeEvent::accept);
    user.meta().connection().assumeWindowOpen = false;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW
    }
  )
  public void sentWindowOpen(
    User user, WrapperPlayServerOpenWindow packet
  ) {
    int slots = packet.getLegacySlots();
    user.meta().connection().nextWindowOpenSlots = slots;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void sendWindowItems(
    ProtocolPacketEvent event
  ) {
    // Nayoro currently does not emit item contents; keep the subscription as an ordering hook.
  }
}
