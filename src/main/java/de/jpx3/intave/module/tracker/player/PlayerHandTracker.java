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

package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsSender;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.converter.BlockPositionConverter;
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsBlockInteractionView;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.packet.view.ProtocolLibBlockPositionView;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.IntSupplier;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.HELD_ITEM_SLOT_OUT;

public class PlayerHandTracker extends Module {
  private final boolean NEW_ITEM_REQUEST = MinecraftVersions.VER1_9_0.atOrAbove();

//  @BukkitEventSubscription
//  public void itemConsume(FoodLevelChangeEvent event) {
//    if (!(event.getEntity() instanceof Player)) {
//      return;
//    }
//    Player player = (Player) event.getEntity();
//    User user = UserRepository.userOf(player);
//    InventoryMetadata inventoryData = user.meta().inventory();
//    if (event.getFoodLevel() >= 20 && inventoryData.foodItem() && inventoryData.handActive()) {
//      inventoryData.deactivateHand();
//    }
//  }

  @BukkitEventSubscription
  public void entityFoodChange(FoodLevelChangeEvent event) {
    HumanEntity entity = event.getEntity();
    if (!(entity instanceof Player)) {
      return;
    }

    Player player = (Player) entity;
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    int foodLevel = event.getFoodLevel();

    if (foodLevel >= 20 && inventoryData.handActive() && inventoryData.foodItem()) {
      if (!ItemProperties.foodConsumable(user, inventoryData.heldItemType())) {
        inventoryData.deactivateHand();
      }
    }
  }

  @BukkitEventSubscription
  public void receiveItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    inventoryData.deactivateHand();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    handleSlotSwitch(player, packet.getIntegers().read(0));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleSlotSwitch(player, new WrapperPlayClientHeldItemChange(event).getSlot());
  }

  /** Engine independent held slot handling; the packet only carries the new hotbar slot. */
  private void handleSlotSwitch(Player player, int slot) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();

    if (isInvalidSlot(slot)) {
      return;
    }

//    if (IntaveControl.DEBUG_ITEM_USAGE) {
//      ItemStack item = player.getInventory().getItem(slot);
//      String typeName = item == null ? "AIR" : item.getType().name();
////      Synchronizer.synchronize(() -> {
//        player.sendMessage("(async) Slot changed to " + slot + ", type: " + typeName);
////      });
//    }

    // apparently required?
    inventoryData.setHeldItemSlot(slot);

    ItemStack item = player.getInventory().getItem(slot);
    inventoryData.pastSlotSwitch = 0;
    if (inventoryData.handActive() && !inventoryData.offhandItemPrimary()) {
      inventoryData.releaseItemNextTick();
      ItemStack itemStack = inventoryData.heldItem();
      if (!ItemProperties.canItemBeUsed(user, itemStack)) {
        inventoryData.blockNextArrow = true;
        inventoryData.lastBlockArrowRequest = System.currentTimeMillis();
        if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
          user.player().sendMessage(IntavePlugin.prefix() + " Detected item switch on active item, released hand and blocking impending arrow shot");
        }
      }
    }
    inventoryData.slotSwitchData = new InventoryMetadata.SlotSwitchData(slot, item);
  }

  @PacketSubscription(
    packetsOut = {
      HELD_ITEM_SLOT_OUT
    }
  )
  public void sentSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    handleSentSlotSwitch(player, packet.getIntegers().read(0));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      HELD_ITEM_SLOT_OUT
    }
  )
  public void sentSlotSwitch(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleSentSlotSwitch(player, new WrapperPlayServerHeldItemChange(event).getSlot());
  }

  /** Engine independent outbound held slot handling; the packet only carries the slot. */
  private void handleSentSlotSwitch(Player player, int slot) {
    User user = UserRepository.userOf(player);

    if (isInvalidSlot(slot)) {
      return;
    }

    Modules.feedback().synchronize(player, slot, (player1, slot1) -> {
      user.meta().inventory().setHeldItemSlot(slot);
    });
  }

  private boolean isInvalidSlot(int slot) {
    return slot >= 36 || slot < 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveBlockPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    boolean requestedItemUse = requestedItemUseLegacy(packet);

    if (requestedItemUse && handleItemUseRequest(user)) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveBlockPlace(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsBlockInteractionView view = PacketEventsBlockInteractionView.of(event);
    if (view == null) {
      return;
    }
    // Pre 1.9 the client announced an item use by sending the block placement without a face;
    // the view reports that as face 255, which is the value the ProtocolLib path tests for.
    boolean requestedItemUse = NEW_ITEM_REQUEST || view.enumDirection() == 255;

    if (requestedItemUse && handleItemUseRequest(UserRepository.userOf(player))) {
      view.setCancelled(true);
    }
  }

  private boolean requestedItemUseLegacy(PacketContainer packet) {
    if (NEW_ITEM_REQUEST) {
      return true;
    } else {
      StructureModifier<Integer> integers = packet.getIntegers();
      return integers.read(0) == 255;
    }
  }

  /**
   * Engine independent item use handling.
   *
   * @return true when the packet has to be cancelled.
   */
  private boolean handleItemUseRequest(User user) {
    InventoryMetadata inventoryData = user.meta().inventory();
    PunishmentMetadata punishmentData = user.meta().punishment();

    ItemStack heldItem = inventoryData.heldItem();
    ItemStack offhandItem = inventoryData.offhandItem();

    boolean sword = heldItem != null && heldItem.getType().name().endsWith("_SWORD");

    if (sword && System.currentTimeMillis() - punishmentData.timeLastBlockCancel < 5000) {
      return true;
    }

    boolean offHandUsable = ItemProperties.canItemBeUsed(user, offhandItem);
    boolean mainHandUsable = ItemProperties.canItemBeUsed(user, heldItem);
    boolean useItem = mainHandUsable || offHandUsable;

    // For some reason Minecraft sends BlockPlace packets on 1.9+ with diamond swords
    boolean usingSword = mainHandUsable && sword;
    if (usingSword && !offHandUsable && !user.meta().protocol().swordBlockingPossible()) {
      return false;
    }

    if (useItem) {
      inventoryData.activateHand();
    }
    return false;
  }

  /**
   * The face index the vanilla client sends with the item release and the item drop:
   * {@code Direction.DOWN}, which is index 0 in the D-U-N-S-W-E order both engines report through
   * {@code BlockPositionView#digFaceIndex()}. Spelled as the number rather than as
   * {@code EnumWrappers.Direction.DOWN.ordinal()} so this class initialises on a server that runs
   * PacketEvents without ProtocolLib.
   */
  private static final int DOWN_FACE = 0;

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockDigging(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    BlockPosition blockPosition = event.getPacket().getModifier()
      .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
      .read(0);

    boolean replayDigPacket = handleBlockDigging(
      player,
      user,
      ProtocolLibBlockPositionView.digActionOf(digType),
      // Handed over lazily because the original body only touched the direction modifier once the
      // two cheap conditions ahead of it held, and reading a field ProtocolLib cannot resolve
      // throws rather than answering null.
      () -> packet.getDirections().read(0).ordinal(),
      blockPosition.toVector().length() == 0
    );

    if (replayDigPacket) {
      PacketContainer unblockPacket = packet.shallowClone();
      unblockPacket.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);
      user.ignoreNextInboundPacket();
      PacketSender.receiveClientPacketFrom(player, packet);
    }
  }

  /**
   * PacketEvents entry point for {@link #receiveBlockDigging(PacketEvent)}.
   * <p>
   * Everything this handler reads is served by {@link PacketEventsBlockPositionView}: the dig
   * action, the targeted block, and - new for this port - the clicked face, which the digging
   * packet has always carried but no reader in that family exposed. Both engines report the face as
   * its vanilla D-U-N-S-W-E index, which is the number each library already holds, so the
   * {@code Direction.DOWN} test in the shared body means the same thing on either side.
   * <p>
   * The tail replays the packet. That was the blocker this method used to document, and it is gone:
   * {@code PacketEventsLinkage#skipInbound} now mirrors {@code ForwardingPacketAdapter}, so
   * {@code User#ignoreNextInboundPacket()} makes the re-injected packet skip every Intave
   * PacketEvents subscription exactly once - this one included, which is what keeps the replay from
   * feeding itself with the drop and the food item still in the same state.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockDigging(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null || view.kind() != BlockPositionView.Kind.BLOCK_DIG) {
      return;
    }
    try {
      BlockPositionView.DigAction digType = view.digAction();
      if (digType == null) {
        // An action id this PacketEvents version does not know. The ProtocolLib path cannot reach
        // this state - its enum covers every action the packet can carry - so there is nothing to
        // mirror, and running the body on a null action would only guess.
        return;
      }
      User user = UserRepository.userOf(player);
      de.jpx3.intave.share.BlockPosition blockPosition = view.blockPosition();
      int digFace = view.digFaceIndex();

      boolean replayDigPacket = handleBlockDigging(
        player,
        user,
        digType,
        () -> digFace,
        blockPosition != null
          && blockPosition.getX() == 0
          && blockPosition.getY() == 0
          && blockPosition.getZ() == 0
      );

      if (replayDigPacket) {
        replayDigPacket(player, user, event);
      }
    } finally {
      view.release();
    }
  }

  /**
   * Hands the digging packet back to the server, the PacketEvents twin of the replay at the tail of
   * {@link #receiveBlockDigging(PacketEvent)}.
   * <p>
   * A verbatim copy of the packet that was just received, because that is what the ProtocolLib path
   * re-injects: it builds a {@code RELEASE_USE_ITEM} clone but then passes the untouched original to
   * {@code PacketSender#receiveClientPacketFrom}, so the clone never leaves that method.
   * <p>
   * A freshly built wrapper rather than the one decoded off the event: re-injecting a wrapper writes
   * it into that wrapper's own buffer, and a wrapper constructed over an inbound event borrows the
   * event's byte buffer, so re-injecting that one would write into the packet still being
   * dispatched. A wrapper built from values starts without a buffer and is given a pooled one.
   * Decoding the event a second time is free: PacketEvents caches the wrapper it already decoded on
   * the event and copies from it.
   * <p>
   * The ignore flag is set immediately before the call, exactly as on the ProtocolLib path;
   * {@code PacketEventsSender#receiveClientPacketFrom} documents why the caller and not the sender
   * owns it.
   */
  private void replayDigPacket(Player player, User user, PacketReceiveEvent event) {
    WrapperPlayClientPlayerDigging received = new WrapperPlayClientPlayerDigging(event);
    WrapperPlayClientPlayerDigging replay = new WrapperPlayClientPlayerDigging(
      received.getAction(),
      received.getBlockPosition(),
      received.getBlockFace(),
      received.getSequence()
    );
    user.ignoreNextInboundPacket();
    PacketEventsSender.receiveClientPacketFrom(player, replay);
  }

  /**
   * Engine independent digging handling.
   *
   * @param digFace               the clicked face as its vanilla D-U-N-S-W-E index, resolved lazily
   *                              so neither engine reads it before the conditions ahead of it hold.
   * @param blockPositionAtOrigin whether the packet points at (0, 0, 0), which is what the client
   *                              sends when the action targets no block at all.
   * @return true when the caller has to replay the packet it just received.
   */
  private boolean handleBlockDigging(
    Player player,
    User user,
    BlockPositionView.DigAction digType,
    IntSupplier digFace,
    boolean blockPositionAtOrigin
  ) {
    InventoryMetadata inventoryData = user.meta().inventory();

    if (digType == BlockPositionView.DigAction.RELEASE_USE_ITEM
      && !inventoryData.handActive()
      && digFace.getAsInt() == DOWN_FACE
      && blockPositionAtOrigin
    ) {
      return false;
    }

    if (IntaveControl.DEBUG_ITEM_USAGE) {
      player.sendMessage("Digtype: " + digType);
    }

    switch (digType) {
      case RELEASE_USE_ITEM:
      case DROP_ALL_ITEMS:
      case DROP_ITEM: {
        inventoryData.deactivateHand();
        break;
      }
    }

    boolean usedFoodItem = inventoryData.foodItem() && inventoryData.handActive();
    // Fix eating while sprinting bug: https://www.youtube.com/watch?v=5ZHMrVmtdNY
    return digType == BlockPositionView.DigAction.DROP_ITEM && usedFoodItem;
  }
}
