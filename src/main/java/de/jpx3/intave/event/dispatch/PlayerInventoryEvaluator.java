package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketId;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.event.packet.PacketId.Client.HELD_ITEM_SLOT;
import static de.jpx3.intave.event.packet.PacketId.Client.*;
import static de.jpx3.intave.event.packet.PacketId.Server.*;

public final class PlayerInventoryEvaluator implements PacketEventSubscriber, BukkitEventSubscriber {
  private final IntavePlugin plugin;

  public PlayerInventoryEvaluator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void entityFoodChange(FoodLevelChangeEvent event) {
    HumanEntity entity = event.getEntity();
    if (!(entity instanceof Player)) {
      return;
    }
    updatePlayerHandItem((Player) entity, event.getFoodLevel());
  }

  @BukkitEventSubscription
  public void itemConsume(FoodLevelChangeEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getEntity();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
//    if (inventoryData.pastSlotSwitch < 5) {
//      event.setCancelled(true);
//    }
    if (event.getFoodLevel() >= 20 && inventoryData.foodItem() && inventoryData.handActive()) {
      inventoryData.deactivateHand();
    }
  }

  @BukkitEventSubscription
  public void receiveInteraction(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    // https://i.imgur.com/O5UBqoJ.png
    if (inventoryData.pastSlotSwitch < 10) {
      event.setCancelled(true);
    }
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
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    inventoryData.updateInventoryOpenState(false);
  }

  private void updatePlayerHandItem(Player player, int foodLevel) {
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    if (foodLevel >= 20 && inventoryData.handActive() && inventoryData.foodItem()) {
      if (!InventoryUseItemHelper.foodItemRegistry().foodConsumable(player, inventoryData.heldItemType())) {
        inventoryData.deactivateHand();
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW
    }
  )
  public void sentOpenInventory(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    PacketContainer packet = event.getPacket();

    WrappedChatComponent chatComponent = packet.getChatComponents().read(0);
    String json = chatComponent.getJson();
    boolean clientDoesNotSendCloseWindow = json.contains("container.beacon");

    if (!clientDoesNotSendCloseWindow) {
      plugin.eventService()
        .feedback()
        .singleSynchronize(player, user, this::openInventory);
      inventoryData.forceInventoryOnClickOpen = true;
    } else {
      inventoryData.forceInventoryOnClickOpen = false;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveClientCommand(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      openInventory(player, user);
    }
  }

  private void openInventory(Player player, User user) {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    if (!inNetherPortal(user)) {
      inventoryData.updateInventoryOpenState(true);
    }
  }

  private boolean inNetherPortal(User user) {
    World world = user.player().getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    return Collision.containsBlockInBB(world, movementData.boundingBox(), BlockTypeAccess.NETHER_PORTAL);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.CLOSE_WINDOW
    }
  )
  public void sentCloseInventory(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    plugin.eventService()
      .feedback()
      .singleSynchronize(player, user, this::closeInventory);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveCloseWindow(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    closeInventory(player, user);
  }

  private void closeInventory(Player player, User user) {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    inventoryData.updateInventoryOpenState(false);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      HELD_ITEM_SLOT
    }
//    ,packetsOut = {
//      PacketId.Server.HELD_ITEM_SLOT
//    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();

    Integer slot = packet.getIntegers().read(0);
    ItemStack item = player.getInventory().getItem(slot);

    boolean handActive = InventoryUseItemHelper.isUseItem(player, item) && inventoryData.handActive();
    if (handActive) {
      inventoryData.activateHand();
    } else {
      inventoryData.deactivateHand();
    }
    inventoryData.setHeldItemSlot(slot);
    inventoryData.pastHotBarSlotChange = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      COLLECT
    }
  )
  public void receiveHandUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    Integer entityID = packet.getIntegers().read(0);

    if (entityID == player.getEntityId()) {
      // sure this is correct? getItemInHand() might needs to be synchronized
//      ItemStack itemInHand = player.getItemInHand();
//      inventoryData.heldItemType(itemInHand);
    }
  }

  @PacketSubscription(
//    priority = ListenerPriority.HIGH,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void receiveBlockPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaPunishmentData punishmentData = meta.punishmentData();

    PacketContainer packet = event.getPacket();
    ItemStack heldItem = inventoryData.heldItem();

    boolean requestedItemUse = requestedItemUse(packet);
    boolean useItem = InventoryUseItemHelper.isUseItem(player, heldItem);

    if (requestedItemUse && AccessHelper.now() - punishmentData.timeLastBlockCancel < 5000) {
      event.setCancelled(true);
      return;
    }

    if (requestedItemUse && useItem) {
      inventoryData.activateHand();
    }
  }

  private final static boolean NEW_ITEM_REQUEST = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  private boolean requestedItemUse(PacketContainer packet) {
    if (NEW_ITEM_REQUEST) {
      return true;
    } else {
      StructureModifier<Integer> integers = packet.getIntegers();
      return integers.read(0) == 255;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockDigging(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();

    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    switch (digType) {
      case RELEASE_USE_ITEM:
      case DROP_ALL_ITEMS:
      case DROP_ITEM: {
        inventoryData.deactivateHand();
        break;
      }
    }
  }
}