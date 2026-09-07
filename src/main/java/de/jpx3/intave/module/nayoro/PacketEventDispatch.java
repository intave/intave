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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.*;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import ac.intave.samples.share.SlotUpdate;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindowButton;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCraftRecipeRequest;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPickItem;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.packet.view.AttackView;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.packet.view.PacketEventsWindowClickView;
import de.jpx3.intave.packet.view.PacketEventsWindowItemView;
import de.jpx3.intave.packet.view.PacketEventsWindowOpenView;
import de.jpx3.intave.packet.view.ProtocolLibAttackView;
import de.jpx3.intave.packet.view.ProtocolLibWindowClickView;
import de.jpx3.intave.packet.view.ProtocolLibWindowItemView;
import de.jpx3.intave.packet.view.ProtocolLibWindowOpenView;
import de.jpx3.intave.packet.view.WindowClickView;
import de.jpx3.intave.packet.view.WindowItemView;
import de.jpx3.intave.packet.view.WindowOpenView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.function.BiConsumer;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketEventDispatch implements PacketEventSubscriber {
  private final BiConsumer<? super User, ? super Event> eventEmitter;

  public PacketEventDispatch(BiConsumer<? super User, ? super Event> eventEmitter) {
    this.eventEmitter = eventEmitter;
  }

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void onClick(PacketEvent event) {
    handleClick(UserRepository.userOf(event.getPlayer()));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void onClick(Player player) {
    if (player == null) {
      return;
    }
    handleClick(UserRepository.userOf(player));
  }

  /** Engine independent arm swing handling; the packet itself carries nothing Intave reads. */
  private void handleClick(User user) {
    // The samples factory returns a shared singleton, but recording offsets are event-local.
    ClickEvent clickEvent = new ClickEvent();
    eventEmitter.accept(user, clickEvent);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void onUse(PacketEvent event) {
    ProtocolLibAttackView view = new ProtocolLibAttackView(event);
    handleUse(UserRepository.userOf(event.getPlayer()), view);
    view.release();
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void onUse(PacketReceiveEvent event) {
    PacketEventsAttackView view = PacketEventsAttackView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleUse(UserRepository.userOf(player), view);
    view.release();
  }

  /** Engine independent entity interaction handling; see {@link AttackView}. */
  private void handleUse(User user, AttackView view) {
    if (view.isAttackPacket()) {
      int attackerId = view.player().getEntityId();
      int targetId = view.entityId();
      AttackEvent attackEvent = AttackEvent.create(attackerId, targetId);
      eventEmitter.accept(user, attackEvent);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    handleMovement(UserRepository.userOf(event.getPlayer()));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(Player player) {
    if (player == null) {
      return;
    }
    handleMovement(UserRepository.userOf(player));
  }

  /**
   * Engine independent movement sampling: the sample is assembled purely from the movement
   * metadata the movement dispatcher already updated, so the packet is never read here.
   */
  private void handleMovement(User user) {
    MovementMetadata movement = user.meta().movement();
    double x = movement.positionX;
    double y = movement.positionY;
    double z = movement.positionZ;
    float yaw = movement.rotationYaw;
    float pitch = movement.rotationPitch;
    int keyStrafe = movement.keyStrafe;
    int keyForward = movement.keyForward;

    boolean collidedHorizontally = movement.collidedHorizontally;
    boolean collidedVertically = movement.collidedVertically || movement.onGround();
    boolean inWater = movement.inWater();
    boolean inLava = movement.inLava();

    boolean inVehicle = movement.isInVehicle();
    boolean sneaking = movement.isSneaking();
    boolean recentlyTeleported = movement.ticksPast(TELEPORT) <= 3;
    boolean jumped = movement.physicsJumped;

    PlayerMoveEvent movementEvent = PlayerMoveEvent.create(
      keyStrafe, keyForward,
      new Position(x, y, z), new Rotation(yaw, pitch),
      collidedHorizontally, collidedVertically, inWater, inLava,
      inVehicle, sneaking, recentlyTeleported, jumped
    );
    eventEmitter.accept(user, movementEvent);
  }

  @PacketSubscription(
    priority = ListenerPriority.MONITOR,
    packetsIn = {
      CLIENT_TICK_END
    }
  )
  public void receiveClientTickEnd(User user) {
    handleClientTickEnd(user);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.MONITOR,
    packetsIn = {
      CLIENT_TICK_END
    }
  )
  public void receiveClientTickEnd(Player player) {
    if (player == null) {
      return;
    }
    handleClientTickEnd(UserRepository.userOf(player));
  }

  private void handleClientTickEnd(User user) {
    eventEmitter.accept(user, new ClientTickEndEvent());
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(PacketEvent event) {
    Player player = event.getPlayer();
    int slot = event.getPacket().getIntegers().read(0);
    handleHeldItemSlot(UserRepository.userOf(player), player, slot);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    int slot = new WrapperPlayClientHeldItemChange(event).getSlot();
    handleHeldItemSlot(UserRepository.userOf(player), player, slot);
  }

  /** Engine independent hotbar switch handling; the packet only carries the slot index. */
  private void handleHeldItemSlot(User user, Player player, int slot) {
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
    eventEmitter.accept(user, slotSwitchEvent);
    eventEmitter.accept(user, InventoryActionEvent.simple(
      0, InventoryActionEvent.Action.SELECT_HOTBAR, -1, slot, null
    ));
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveClientInventoryOpen(User user, PacketContainer packet) {
    EnumWrappers.ClientCommand command = packet.getClientCommands().readSafely(0);
    if (command != EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      return;
    }
    handleClientInventoryOpen(user);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveClientInventoryOpen(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLIENT_STATUS) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    WrapperPlayClientClientStatus.Action action = new WrapperPlayClientClientStatus(event).getAction();
    if (action != WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
      return;
    }
    handleClientInventoryOpen(UserRepository.userOf(player));
  }

  /** Engine independent "client opened its own inventory" handling. */
  private void handleClientInventoryOpen(User user) {
    if (user.meta().connection().assumeWindowOpen) {
      return;
    }
    user.meta().connection().assumeWindowOpen = true;
    user.meta().connection().assumedWindowId = 0;
    eventEmitter.accept(user, new InventoryOpenEvent(0, "minecraft:inventory", false));
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
    handleWindowClick(user, new ProtocolLibWindowClickView(user.player(), reader));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void receiveWindowClick(PacketReceiveEvent event) {
    PacketEventsWindowClickView view = PacketEventsWindowClickView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleWindowClick(UserRepository.userOf(player), view);
  }

  /** Engine independent container click handling; see {@link WindowClickView}. */
  private void handleWindowClick(User user, WindowClickView view) {
    boolean assumeWindowOpen = user.meta().connection().assumeWindowOpen;
    if (!assumeWindowOpen) {
      user.meta().connection().assumeWindowOpen = true;
      user.meta().connection().assumedWindowId = view.containerId();
      InventoryOpenEvent openEvent = new InventoryOpenEvent(
        view.containerId(), view.containerId() == 0 ? "minecraft:inventory" : "unknown", true
      );
      eventEmitter.accept(user, openEvent);
    }
    InventoryActionEvent clickEvent = new InventoryActionEvent(
      view.containerId(), view.action(), view.slot(), view.button(), view.revision(),
      SampleTypes.slotUpdates(view.predictedSlots()),
      view.carriedItemKnown(), SampleTypes.nullableItem(view.carriedItem())
    );
    eventEmitter.accept(user, clickEvent);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveWindowClose(PacketEvent event) {
    User user = UserRepository.userOf(event.getPlayer());
    int containerId = event.getPacket().getIntegers().readSafely(0);
    handleWindowClose(user, containerId);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveWindowClose(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLOSE_WINDOW) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    int containerId = new WrapperPlayClientCloseWindow(event).getWindowId();
    handleWindowClose(UserRepository.userOf(player), containerId);
  }

  /** Engine independent "client closed the container" handling. */
  private void handleWindowClose(User user, int containerId) {
    eventEmitter.accept(user, new InventoryCloseEvent(
      containerId, InventoryCloseEvent.Source.CLIENT
    ));
    user.meta().connection().assumeWindowOpen = false;
    user.meta().connection().assumedWindowId = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.CLOSE_WINDOW
    }
  )
  public void sentWindowClose(User user, WindowIdReader reader) {
    handleSentWindowClose(user, reader.containerId());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.CLOSE_WINDOW
    }
  )
  public void sentWindowClose(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.CLOSE_WINDOW) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    int containerId = new WrapperPlayServerCloseWindow(event).getWindowId();
    handleSentWindowClose(UserRepository.userOf(player), containerId);
  }

  /** Engine independent "server closed the container" handling. */
  private void handleSentWindowClose(User user, int containerId) {
    eventEmitter.accept(user, new InventoryCloseEvent(
      containerId, InventoryCloseEvent.Source.SERVER
    ));
    user.meta().connection().assumeWindowOpen = false;
    user.meta().connection().assumedWindowId = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW, OPEN_WINDOW_HORSE
    }
  )
  public void sentWindowOpen(
    User user, WindowOpenReader reader
  ) {
    handleSentWindowOpen(user, new ProtocolLibWindowOpenView(user.player(), reader));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW, OPEN_WINDOW_HORSE
    }
  )
  public void sentWindowOpen(PacketSendEvent event) {
    PacketEventsWindowOpenView view = PacketEventsWindowOpenView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleSentWindowOpen(UserRepository.userOf(player), view);
  }

  /**
   * Engine independent "server opened a container" handling; see {@link WindowOpenView}. The menu
   * type is exact on both backends up to 1.18.2 and degrades to {@code "unknown"} - the value the
   * ProtocolLib reader itself falls back to - on PacketEvents from 1.19 on; the container id, which
   * is what the assumed window bookkeeping below runs on, is exact on every version.
   */
  private void handleSentWindowOpen(User user, WindowOpenView view) {
    int containerId = view.containerId();
    if (user.meta().connection().assumeWindowOpen
      && user.meta().connection().assumedWindowId != containerId) {
      eventEmitter.accept(user, new InventoryCloseEvent(
        user.meta().connection().assumedWindowId, InventoryCloseEvent.Source.INFERRED
      ));
    }
    user.meta().connection().assumeWindowOpen = true;
    user.meta().connection().assumedWindowId = containerId;
    eventEmitter.accept(user, new InventoryOpenEvent(
      containerId, view.menuType(), false
    ));
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
    handleWindowItems(user, new ProtocolLibWindowItemView(user.player(), reader));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      WINDOW_ITEMS, SET_SLOT
    }
  )
  public void sendWindowItems(PacketSendEvent event) {
    PacketEventsWindowItemView view = PacketEventsWindowItemView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleWindowItems(UserRepository.userOf(player), view);
  }

  /** Engine independent container content handling; see {@link WindowItemView}. */
  private void handleWindowItems(User user, WindowItemView view) {
    int packetContainer = view.windowId();
    int container = packetContainer == -1 && user.meta().connection().assumeWindowOpen
      ? user.meta().connection().assumedWindowId
      : packetContainer < 0 ? 0 : packetContainer;
    InventoryUpdateEvent event = new InventoryUpdateEvent(
      container, view.full(), view.revision(),
      SampleTypes.slotUpdates(view.itemMap()),
      view.carriedItemKnown(), SampleTypes.nullableItem(view.carriedItem())
    );
    eventEmitter.accept(user, event);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveHeldItemAction(User user, BlockDigReader reader) {
    EnumWrappers.PlayerDigType digType = reader.action();
    InventoryActionEvent.Action action;
    if (digType == EnumWrappers.PlayerDigType.DROP_ITEM) {
      action = InventoryActionEvent.Action.DROP_HELD_ONE;
    } else if (digType == EnumWrappers.PlayerDigType.DROP_ALL_ITEMS) {
      action = InventoryActionEvent.Action.DROP_HELD_STACK;
    } else if (digType == EnumWrappers.PlayerDigType.SWAP_HELD_ITEMS) {
      action = InventoryActionEvent.Action.SWAP_HANDS;
    } else {
      return;
    }
    handleHeldItemAction(user, action);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveHeldItemAction(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    DiggingAction digAction = new WrapperPlayClientPlayerDigging(event).getAction();
    InventoryActionEvent.Action action;
    // PacketEvents' DROP_ITEM / DROP_ITEM_STACK carry the vanilla ids 4 and 3, so they line up
    // with ProtocolLib's DROP_ITEM and DROP_ALL_ITEMS respectively.
    if (digAction == DiggingAction.DROP_ITEM) {
      action = InventoryActionEvent.Action.DROP_HELD_ONE;
    } else if (digAction == DiggingAction.DROP_ITEM_STACK) {
      action = InventoryActionEvent.Action.DROP_HELD_STACK;
    } else if (digAction == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
      action = InventoryActionEvent.Action.SWAP_HANDS;
    } else {
      return;
    }
    handleHeldItemAction(UserRepository.userOf(player), action);
  }

  /** Engine independent drop / swap handling; the action was already normalised by the caller. */
  private void handleHeldItemAction(User user, InventoryActionEvent.Action action) {
    eventEmitter.accept(user, InventoryActionEvent.simple(0, action, -1, -1, null));
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      SET_CREATIVE_SLOT
    }
  )
  public void receiveCreativeSlot(User user, PacketContainer packet) {
    Integer slot = packet.getIntegers().readSafely(0);
    if (slot == null) {
      Short shortSlot = packet.getShorts().readSafely(0);
      slot = shortSlot == null ? -1 : shortSlot.intValue();
    }
    ItemStack item = packet.getItemModifier().readSafely(0);
    handleCreativeSlot(user, slot, item);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      SET_CREATIVE_SLOT
    }
  )
  public void receiveCreativeSlot(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    WrapperPlayClientCreativeInventoryAction wrapper =
      new WrapperPlayClientCreativeInventoryAction(event);
    handleCreativeSlot(
      UserRepository.userOf(player), wrapper.getSlot(), bukkitItem(wrapper.getItemStack())
    );
  }

  /** Engine independent creative set-slot handling. */
  private void handleCreativeSlot(User user, int slot, ItemStack item) {
    InventoryActionEvent.Action action = slot < 0
      ? InventoryActionEvent.Action.CREATIVE_DROP
      : InventoryActionEvent.Action.CREATIVE_SET_SLOT;
    eventEmitter.accept(user, new InventoryActionEvent(
      0, action, slot, -1, null,
      Collections.singletonList(new SlotUpdate(slot, SampleTypes.nullableItem(item))),
      false, null
    ));
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENCHANT_ITEM
    }
  )
  public void receiveMenuButton(User user, PacketContainer packet) {
    int containerId = packet.getIntegers().readSafely(0);
    int button = packet.getIntegers().readSafely(1);
    handleMenuButton(user, containerId, button);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENCHANT_ITEM
    }
  )
  public void receiveMenuButton(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW_BUTTON) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    WrapperPlayClientClickWindowButton wrapper = new WrapperPlayClientClickWindowButton(event);
    handleMenuButton(UserRepository.userOf(player), wrapper.getWindowId(), wrapper.getButtonId());
  }

  /** Engine independent container button handling. */
  private void handleMenuButton(User user, int containerId, int button) {
    eventEmitter.accept(user, InventoryActionEvent.simple(
      containerId, InventoryActionEvent.Action.MENU_BUTTON, -1, button, null
    ));
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.AUTO_RECIPE
    }
  )
  public void receivePlaceRecipe(User user, PacketContainer packet) {
    int containerId = packet.getIntegers().readSafely(0);
    Boolean placeAll = packet.getBooleans().readSafely(0);
    handlePlaceRecipe(user, containerId, Boolean.TRUE.equals(placeAll));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.AUTO_RECIPE
    }
  )
  public void receivePlaceRecipe(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CRAFT_RECIPE_REQUEST) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    WrapperPlayClientCraftRecipeRequest wrapper = new WrapperPlayClientCraftRecipeRequest(event);
    handlePlaceRecipe(UserRepository.userOf(player), wrapper.getWindowId(), wrapper.isMakeAll());
  }

  /** Engine independent recipe book placement handling. */
  private void handlePlaceRecipe(User user, int containerId, boolean placeAll) {
    eventEmitter.accept(user, InventoryActionEvent.simple(
      containerId, InventoryActionEvent.Action.PLACE_RECIPE, -1,
      placeAll ? 1 : 0, null
    ));
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PICK_ITEM
    }
  )
  public void receivePickItem(User user, PacketContainer packet) {
    int slot = packet.getIntegers().readSafely(0);
    handlePickItem(user, slot);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PICK_ITEM
    }
  )
  public void receivePickItem(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.PICK_ITEM) {
      return;
    }
    Player player = bukkitPlayer(event);
    if (player == null) {
      return;
    }
    handlePickItem(UserRepository.userOf(player), new WrapperPlayClientPickItem(event).getSlot());
  }

  /** Engine independent middle click handling. */
  private void handlePickItem(User user, int slot) {
    eventEmitter.accept(user, InventoryActionEvent.simple(
      0, InventoryActionEvent.Action.PICK_ITEM, slot, -1, null
    ));
  }

  /** @return the Bukkit player behind a PacketEvents event, or null before the play phase. */
  private static Player bukkitPlayer(com.github.retrooper.packetevents.event.ProtocolPacketEvent event) {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  /**
   * @return the Bukkit stack for a PacketEvents one, matching what ProtocolLib's item modifier
   * hands out for an empty slot (an air stack, which the samples layer maps to "no item").
   */
  private static ItemStack bukkitItem(
    com.github.retrooper.packetevents.protocol.item.ItemStack item
  ) {
    if (item == null || item.isEmpty()) {
      return new ItemStack(Material.AIR);
    }
    ItemStack converted = SpigotConversionUtil.toBukkitItemStack(item);
    return converted == null ? new ItemStack(Material.AIR) : converted;
  }
}
