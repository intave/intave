package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

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
      if (!ItemProperties.foodConsumable(player, inventoryData.heldItemType())) {
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
  public void receiveSlotSwitch(ProtocolPacketEvent event, WrapperPlayClientHeldItemChange packet) {
    Player player = event.getPlayer();

    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();

    int slot = packet.getSlot();

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
      if (!ItemProperties.canItemBeUsed(player, itemStack)) {
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
  public void sentSlotSwitch(ProtocolPacketEvent event, WrapperPlayServerHeldItemChange packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int slot = packet.getSlot();

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
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receiveBlockPlace(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    InventoryMetadata inventoryData = meta.inventory();
    PunishmentMetadata punishmentData = meta.punishment();

    ItemStack heldItem = inventoryData.heldItem();
    ItemStack offhandItem = inventoryData.offhandItem();

    boolean requestedItemUse = requestedItemUse(event);
    boolean sword = heldItem != null && heldItem.getType().name().endsWith("_SWORD");

    if (requestedItemUse && sword && System.currentTimeMillis() - punishmentData.timeLastBlockCancel < 5000) {
      event.setCancelled(true);
      return;
    }

    boolean offHandUsable = ItemProperties.canItemBeUsed(player, offhandItem);
    boolean mainHandUsable = ItemProperties.canItemBeUsed(player, heldItem);
    boolean useItem = mainHandUsable || offHandUsable;

    // For some reason Minecraft sends BlockPlace packets on 1.9+ with diamond swords
    boolean usingSword = mainHandUsable && sword;
    if (usingSword && !offHandUsable && user.protocolVersion() > 47) {
      return;
    }

    if (requestedItemUse && useItem) {
      inventoryData.activateHand();
    }
  }

  private boolean requestedItemUse(ProtocolPacketEvent event) {
    if (NEW_ITEM_REQUEST) {
      return true;
    }
    WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
    Vector3i position = packet.getBlockPosition();
    return packet.getFace() == BlockFace.OTHER || (position != null && position.x == -1 && position.y == -1 && position.z == -1);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockDigging(ProtocolPacketEvent event, WrapperPlayClientPlayerDigging packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();

    DiggingAction digType = packet.getAction();
    Vector3i blockPosition = packet.getBlockPosition();

    if (digType == DiggingAction.RELEASE_USE_ITEM
      && !inventoryData.handActive()
      && packet.getBlockFace() == BlockFace.DOWN
      && blockPosition.x == 0 && blockPosition.y == 0 && blockPosition.z == 0
    ) {
      return;
    }

    if (IntaveControl.DEBUG_ITEM_USAGE) {
      player.sendMessage("Digtype: " + digType);
    }

    switch (digType) {
      case RELEASE_USE_ITEM:
      case DROP_ITEM_STACK:
      case DROP_ITEM: {
        inventoryData.deactivateHand();
        break;
      }
    }

    boolean usedFoodItem = inventoryData.foodItem() && inventoryData.handActive();
    // Fix eating while sprinting bug: https://www.youtube.com/watch?v=5ZHMrVmtdNY
    if (digType == DiggingAction.DROP_ITEM && usedFoodItem) {
      WrapperPlayClientPlayerDigging unblockPacket = new WrapperPlayClientPlayerDigging(
        DiggingAction.RELEASE_USE_ITEM,
        new Vector3i(0, 0, 0),
        BlockFace.DOWN,
        packet.getSequence()
      );
      PacketEvents.getAPI().getPlayerManager().receivePacketSilently(player, unblockPacket);
    }
  }
}
