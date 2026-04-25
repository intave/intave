package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_COMMAND;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class InventoryTracker extends Module {

  //////////////////////////////////////////////////////////////////////////////
  // Open Inventory
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Tracks if the server forces a player to open an inventory by listening to
   * the {@link de.jpx3.intave.module.linker.packet.PacketId.Server#OPEN_WINDOW}
   * packet.
   */
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW
    },
    ignoreCancelled = false
  )
  public void sentOpenInventory(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    PacketContainer packet = event.getPacket();

    // For some reason the client doesn't send a close window packet after closing
    // the beacon window. Therefore, we pretend the player does not have an open
    // inventory if he opens the beacon window to avoid further issues.
    WrappedChatComponent chatComponent = packet.getChatComponents().read(0);
    String json = chatComponent.getJson();
    boolean clientDoesNotSendCloseWindow = json.contains("container.beacon");

    if (!clientDoesNotSendCloseWindow) {
      Modules.feedback()
        .synchronize(player, null, (p, x) -> openInventory(p));
      inventoryData.forceInventoryOnClickOpen = true;
    } else {
      inventoryData.forceInventoryOnClickOpen = false;
    }
  }

  /**
   * Tracks if the client opens the inventory by listening to the
   * {@link de.jpx3.intave.module.linker.packet.PacketId.Client#CLIENT_COMMAND}
   * packet.
   * However, this functionality was removed in Minecraft 1.9.
   */
  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveClientCommand(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      openInventory(player);
    }
  }

  /**
   * Updates the inventory's state of the specified player internally.
   *
   * @param player The player
   */
  private void openInventory(Player player) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();

    if (!inNetherPortal(user)) {
      inventoryData.updateInventoryOpenState(true);
    }
  }

  /**
   * Checks whether the specified user is inside a nether portal.
   *
   * @param user The user
   * @return whether the user is inside a nether portal
   */
  private boolean inNetherPortal(User user) {
    MovementMetadata movementData = user.meta().movement();
    return Collision.rasterizedTypeSearch(user, movementData.boundingBox(), BlockTypeAccess.NETHER_PORTAL);
  }

  //////////////////////////////////////////////////////////////////////////////
  // Close Inventory
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Tracks if the server forces a player to close the inventory by listening to
   * the {@link de.jpx3.intave.module.linker.packet.PacketId.Server#CLOSE_WINDOW}
   * packet.
   */
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.CLOSE_WINDOW
    },
    ignoreCancelled = false
  )
  public void sentCloseInventory(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    Modules.feedback()
      .synchronize(player, null, (p, x) -> closeInventory(p));
  }

  /**
   * Tracks if the player closes the inventory by listening to the
   * {@link de.jpx3.intave.module.linker.packet.PacketId.Client#CLOSE_WINDOW}
   * packet.
   */
  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveCloseWindow(ProtocolPacketEvent event) {
    closeInventory(event.getPlayer());
  }

  /**
   * Updates the inventory's state of the specified internally.
   *
   * @param player The player
   */
  private void closeInventory(Player player) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    inventoryData.updateInventoryOpenState(false);
  }

  /**
   * Closes a player's inventory if the player dies internally by listening to
   * Bukkit's {@link org.bukkit.event.player.PlayerRespawnEvent}.
   */
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      RESPAWN
    },
    ignoreCancelled = false
  )
  public void sentRespawn(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    inventoryData.updateInventoryOpenState(false);
  }

  @PacketSubscription(
    packetsOut = {WINDOW_ITEMS}
  )
  public void on(
    User user, WindowItemReader reader
  ) {
    if (reader.windowId() == 0) {
      List<String> collect = reader.itemMap().values().stream().map(itemStack -> itemStack.getType().name()).collect(Collectors.toList());
      user.tickFeedback(() -> user.meta().inventory().setItems(collect));
//      System.out.println(collect);
    }
  }
}