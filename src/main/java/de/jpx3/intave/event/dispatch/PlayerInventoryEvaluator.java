package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

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

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "RESPAWN"),
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
      if (!InventoryUseItemHelper.foodItemRegistry().foodConsumable(foodLevel, inventoryData.heldItemType())) {
        inventoryData.deactivateHand();
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "OPEN_WINDOW"),
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
        .transactionFeedbackService()
        .requestPong(player, user, this::openInventory);
      inventoryData.forceInventoryOnClickOpen = true;
    } else {
      inventoryData.forceInventoryOnClickOpen = false;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "CLIENT_COMMAND"),
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
    return Collision.containsBlockInBB(world, movementData.boundingBox(), Material.PORTAL);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "CLOSE_WINDOW")
    }
  )
  public void sentCloseInventory(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    plugin.eventService()
      .transactionFeedbackService()
      .requestPong(player, user, this::closeInventory);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "CLOSE_WINDOW"),
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
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "HELD_ITEM_SLOT"),
//      @PacketDescriptor(sender = Sender.SERVER, packetName = "HELD_ITEM_SLOT")
    }
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
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "COLLECT")
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
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE")
    }
  )
  public void receiveBlockPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();

    PacketContainer packet = event.getPacket();
    ItemStack heldItem = inventoryData.heldItem();

    boolean requestedItemUse = requestedItemUse(packet);
    boolean useItem = InventoryUseItemHelper.isUseItem(player, heldItem);

    if (requestedItemUse && useItem) {
      inventoryData.activateHand();
    }
  }

  private final static boolean NEW_ITEM_REQUEST = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE);

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
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveBlockDigging(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();

    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    switch (digType) {
      case RELEASE_USE_ITEM: {
        inventoryData.deactivateHand();
        break;
      }
      case DROP_ALL_ITEMS:
      case DROP_ITEM: {
        inventoryData.deactivateHand();
//        inventoryData.setHeldItem(null);
        break;
      }
    }
  }
}