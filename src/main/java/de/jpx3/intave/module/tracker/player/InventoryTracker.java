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

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.share.ClientCommand;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.event.Cancellable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static de.jpx3.intave.packet.ListenerPriority.*;
import static de.jpx3.intave.packet.ListenerStage.AFTER;

public final class InventoryTracker extends Module {

  //////////////////////////////////////////////////////////////////////////////
  // Open Inventory
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Tracks if the server forces a player to open an inventory by listening to
   * the {@link PacketType.Server#OPEN_WINDOW}
   * packet.
   */
  @OnSend(priority = LATER)
  public void sentOpenInventory(User user, OpenScreenPacket packet, PacketSendEvent event) {
    int windowId = packet.containerId();
    if (event.isCancelled()) {
      debug(user, () -> "Server open cancelled for window " + windowId);
      return;
    }
    InventoryMetadata inventoryData = user.meta().inventory();
    // Closing a beacon can omit the client close-window packet.
    if (packet.isBeaconMenu()) {
      inventoryData.forceInventoryOnClickOpen = false;
      debug(user, () -> "Skipping open tracking for beacon window " + windowId);
      return;
    }
    Modules.feedback().synchronize(user, event, () -> openInventory(user, "Open feedback received", windowId));
    inventoryData.forceInventoryOnClickOpen = true;
    debug(user, () -> "Server open for window " + windowId + ", awaiting feedback");
  }

  /** Clients through 1.11.2 report opening their own inventory with this command. */
  @OnReceive(priority = EARLY)
  public void receiveClientCommand(User user, ClientCommandPacket packet) {
    if (packet.command() == ClientCommand.OPEN_INVENTORY) {
      openInventory(user, "Client open command", 0);
    }
  }

  private void openInventory(User user, String source, int windowId) {
    if (inNetherPortal(user)) {
      debug(user, () -> source + " for window " + windowId + ", skipped inside a nether portal");
      return;
    }
    user.meta().inventory().updateInventoryOpenState(true);
    debug(user, () -> source + " for window " + windowId);
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
   * the {@link PacketType.Server#CLOSE_WINDOW}
   * packet.
   */
  @OnSend(priority = LATER)
  public void sentCloseInventory(User user, WindowClosePacket packet, PacketSendEvent event) {
    int windowId = packet.containerId();
    if (event.isCancelled()) {
      debug(user, () -> "Server close cancelled for window " + windowId);
      return;
    }
    Modules.feedback().synchronize(user, event, () -> {
      user.meta().inventory().updateInventoryOpenState(false);
      debug(user, () -> "Close feedback received for window " + windowId);
    });
    debug(user, () -> "Server close for window " + windowId + ", awaiting feedback");
  }

  /**
   * Tracks if the player closes the inventory by listening to the
   * {@link PacketType.Client#CLOSE_WINDOW}
   * packet.
   */
  @OnReceive(priority = EARLY)
  public void receiveCloseWindow(User user, WindowClosePacket packet) {
    user.meta().inventory().updateInventoryOpenState(false);
    debug(user, () -> "Client closed window " + packet.containerId());
  }

  @OnReceive(priority = LAST, stage = AFTER)
  public void receiveWindowClick(User user, Cancellable cancellable, WindowClickHeaderPacket packet) {
    debug(user, () -> "Client click in window " + packet.containerId() + ", slot=" + packet.slot()
      + ", button=" + packet.button() + ", type=" + packet.clickType() + ", cancelled=" + cancellable.isCancelled());
  }

  @OnSend(priority = LATER)
  public void sentRespawn(User user, RespawnPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    Modules.feedback().synchronize(user, event, () -> {
      user.meta().inventory().updateInventoryOpenState(false);
      debug(user, () -> "Respawn feedback received");
    });
  }

  @OnSend
  public void sentWindowItems(User user, Cancellable cancellable, WindowBulkItemsPacket packet) {
    if (packet.containerId() != 0 || cancellable.isCancelled()) {
      return;
    }
    List<String> items = packet.items().stream().map(InventoryTracker::materialName).collect(Collectors.toList());
    user.tickFeedback(() -> user.meta().inventory().setItems(items));
  }

  private static String materialName(ItemStack stack) {
    if (stack == null) {
      return Material.AIR.name();
    }
    return stack.bukkitMaterial().map(Material::name)
      .orElseGet(() -> stack.type().path().toUpperCase(Locale.ROOT));
  }

  private static void debug(User user, Supplier<String> event) {
    if (user.receives(MessageChannel.DEBUG_INVENTORY)) {
      InventoryMetadata inventory = user.meta().inventory();
      user.sendMessage(IntavePlugin.prefix() + "[Inventory] " + event.get()
        + " | tracked=" + (inventory.inventoryOpen() ? "open" : "closed")
        + ", forceInventoryOnClickOpen=" + inventory.forceInventoryOnClickOpen);
    }
  }
}
