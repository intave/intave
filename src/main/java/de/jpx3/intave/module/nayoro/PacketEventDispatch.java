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

import de.jpx3.intave.share.PlayerHand;

import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.ClientCommand;

import ac.intave.samples.event.*;
import ac.intave.samples.share.Position;
import ac.intave.samples.share.Rotation;
import ac.intave.samples.share.SlotUpdate;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.PlayerDigAction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.function.BiConsumer;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketEventDispatch implements PacketEventSubscriber {
  private static volatile Method attackCooldownMethod;
  private static volatile boolean attackCooldownMethodResolved;

  private final BiConsumer<? super User, ? super Event> eventEmitter;

  public PacketEventDispatch(BiConsumer<? super User, ? super Event> eventEmitter) {
    this.eventEmitter = eventEmitter;
  }

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void onClick(User user, ArmAnimationReader reader) {
    eventEmitter.accept(user, ClickEvent.create(SampleTypes.hand(reader.hand())));
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void onUse(
    Player player, User user, EntityUseReader reader
  ) {
    EntityUseAction useAction = reader.useAction();
    if (useAction == EntityUseAction.ATTACK) {
      int attackerId = player.getEntityId();
      int targetId = reader.entityId();
      AttackEvent attackEvent = AttackEvent.create(
        attackerId, targetId, attackStrength(player)
      );
      eventEmitter.accept(user, attackEvent);
    } else {
      EntityInteractEvent.Action action = useAction == EntityUseAction.INTERACT_AT
        ? EntityInteractEvent.Action.INTERACT_AT
        : useAction == EntityUseAction.INTERACT
          ? EntityInteractEvent.Action.INTERACT
          : EntityInteractEvent.Action.UNKNOWN;
      eventEmitter.accept(user, new EntityInteractEvent(
        player.getEntityId(), reader.entityId(), action,
        SampleTypes.hand(reader.hand()),
        action == EntityInteractEvent.Action.INTERACT_AT
          ? SampleTypes.vector(reader.hitPosition())
          : null
      ));
    }
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveItemUse(
    User user, BlockInteractionReader reader
  ) {
    if (!reader.isItemUseRequest()) {
      return;
    }
    InventoryMetadata inventory = user.meta().inventory();
    PlayerHand hand = reader.hand();
    if (hand == null) {
      hand = inventory.activeHand();
    }
    eventEmitter.accept(user, new ItemActionEvent(
      ItemActionEvent.Action.USE,
      SampleTypes.hand(hand),
      SampleTypes.item(itemInHand(inventory, hand))
    ));
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveItemReleaseOrStab(
    Player player, User user, BlockDigReader reader
  ) {
    InventoryMetadata inventory = user.meta().inventory();
    if (reader.isStab()) {
      PlayerHand hand = PlayerHand.MAIN_HAND;
      eventEmitter.accept(user, new ItemActionEvent(
        ItemActionEvent.Action.STAB,
        SampleTypes.hand(hand),
        SampleTypes.item(itemInHand(inventory, hand)),
        attackStrength(player)
      ));
      return;
    }
    if (reader.action() != PlayerDigAction.RELEASE_USE_ITEM) {
      return;
    }
    PlayerHand hand = inventory.activeHand();
    eventEmitter.accept(user, new ItemActionEvent(
      ItemActionEvent.Action.RELEASE_USE,
      SampleTypes.hand(hand),
      SampleTypes.item(itemInHand(inventory, hand))
    ));
  }

  private static ItemStack itemInHand(
    InventoryMetadata inventory, PlayerHand hand
  ) {
    return hand == PlayerHand.OFF_HAND
      ? inventory.offhandItem()
      : inventory.heldItem();
  }

  private static Float attackStrength(Player player) {
    if (MinecraftVersions.VER1_9_0.below()) {
      return null;
    }
    Method method = attackCooldownMethod();
    if (method == null) {
      return null;
    }
    float strength;
    try {
      Object value = method.invoke(player);
      if (!(value instanceof Number)) {
        return null;
      }
      strength = ((Number) value).floatValue();
    } catch (ReflectiveOperationException exception) {
      return null;
    }
    if (!Float.isFinite(strength)) {
      return null;
    }
    return Math.max(0.0F, Math.min(1.0F, strength));
  }

  private static Method attackCooldownMethod() {
    if (!attackCooldownMethodResolved) {
      try {
        attackCooldownMethod = Player.class.getMethod("getAttackCooldown");
      } catch (NoSuchMethodException ignored) {
        attackCooldownMethod = null;
      }
      attackCooldownMethodResolved = true;
    }
    return attackCooldownMethod;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = ENTITY_STATUS
  )
  public void sendEntityStatus(
    User user, PacketEvent event, EntityStatusReader reader
  ) {
    if (!reader.indicatesTotemActivation()) {
      return;
    }
    int entityId = reader.entityId();
    user.packetTickFeedback(event, () ->
      eventEmitter.accept(user, new TotemPopEvent(entityId))
    );
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
    boolean sprinting = movement.isSprinting();
    String pose = movement.pose().name();

    PlayerMoveEvent movementEvent = PlayerMoveEvent.create(
      keyStrafe, keyForward,
      new Position(x, y, z), new Rotation(yaw, pitch),
      collidedHorizontally, collidedVertically, inWater, inLava,
      inVehicle, sneaking, recentlyTeleported, jumped,
      sprinting, sneaking, pose
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
  public void receiveClientInventoryOpen(User user, ClientCommandReader reader) {
    if (reader.command() != ClientCommand.OPEN_INVENTORY
      || user.meta().connection().assumeWindowOpen) {
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
    boolean assumeWindowOpen = user.meta().connection().assumeWindowOpen;
    if (!assumeWindowOpen) {
      user.meta().connection().assumeWindowOpen = true;
      user.meta().connection().assumedWindowId = reader.containerId();
      InventoryOpenEvent openEvent = new InventoryOpenEvent(
        reader.containerId(), reader.containerId() == 0 ? "minecraft:inventory" : "unknown", true
      );
      eventEmitter.accept(user, openEvent);
    }
    InventoryActionEvent clickEvent = new InventoryActionEvent(
      reader.containerId(), reader.action(), reader.slot(), reader.button(), reader.revision(),
      SampleTypes.slotUpdates(reader.predictedSlots()),
      reader.carriedItemKnown(), SampleTypes.nullableItem(reader.carriedItem())
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
    eventEmitter.accept(user, new InventoryCloseEvent(
      reader.containerId(), InventoryCloseEvent.Source.SERVER
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
    int containerId = reader.containerId();
    if (user.meta().connection().assumeWindowOpen
      && user.meta().connection().assumedWindowId != containerId) {
      eventEmitter.accept(user, new InventoryCloseEvent(
        user.meta().connection().assumedWindowId, InventoryCloseEvent.Source.INFERRED
      ));
    }
    user.meta().connection().assumeWindowOpen = true;
    user.meta().connection().assumedWindowId = containerId;
    eventEmitter.accept(user, new InventoryOpenEvent(
      containerId, reader.menuType(), false
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
    int packetContainer = reader.windowId();
    int container = packetContainer == -1 && user.meta().connection().assumeWindowOpen
      ? user.meta().connection().assumedWindowId
      : packetContainer < 0 ? 0 : packetContainer;
    InventoryUpdateEvent event = new InventoryUpdateEvent(
      container, reader.full(), reader.revision(),
      SampleTypes.slotUpdates(reader.itemMap()),
      reader.carriedItemKnown(), SampleTypes.nullableItem(reader.carriedItem())
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
    InventoryActionEvent.Action action;
    PlayerDigAction digType = reader.action();
    if (digType == PlayerDigAction.DROP_ITEM) {
      action = InventoryActionEvent.Action.DROP_HELD_ONE;
    } else if (digType == PlayerDigAction.DROP_ALL_ITEMS) {
      action = InventoryActionEvent.Action.DROP_HELD_STACK;
    } else if (digType == PlayerDigAction.SWAP_HELD_ITEMS) {
      action = InventoryActionEvent.Action.SWAP_HANDS;
    } else {
      return;
    }
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
    eventEmitter.accept(user, InventoryActionEvent.simple(
      containerId, InventoryActionEvent.Action.PLACE_RECIPE, -1,
      Boolean.TRUE.equals(placeAll) ? 1 : 0, null
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
    eventEmitter.accept(user, InventoryActionEvent.simple(
      0, InventoryActionEvent.Action.PICK_ITEM, slot, -1, null
    ));
  }
}
