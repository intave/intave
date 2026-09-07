package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.packet.view.PacketEventsWindowItemView;
import de.jpx3.intave.packet.view.ProtocolLibWindowItemView;
import de.jpx3.intave.packet.view.WindowItemView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_COMMAND;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class InventoryTracker extends Module {

  /**
   * Adventure's own JSON serialiser, or null when only PacketEvents' relocated fork of it is on the
   * runtime classpath; see {@link #titleJson(Component)}. Resolved once, because
   * {@code GsonComponentSerializer#gson()} builds its Gson instance on first use.
   */
  private static final GsonComponentSerializer TITLE_SERIALIZER = resolveTitleSerializer();

  private static GsonComponentSerializer resolveTitleSerializer() {
    try {
      return GsonComponentSerializer.gson();
    } catch (Throwable unavailable) {
      // adventure-text-serializer-gson is a compileOnly dependency: PacketEvents needs adventure-api
      // at runtime for its own signatures, but ships its own relocated copy of the gson serializer
      // and therefore does not guarantee the unrelocated one. Fall back to PacketEvents' copy.
      return null;
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Open Inventory
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Tracks if the server forces a player to open an inventory by listening to
   * the {@link de.jpx3.intave.module.linker.packet.PacketId.Server#OPEN_WINDOW}
   * packet.
   *
   * <h2>How the PacketEvents twin reproduces this</h2>
   * The window title is the whole decision here: the beacon is recognised by its title JSON
   * containing {@code container.beacon}, the translation key vanilla puts there. ProtocolLib hands
   * that title out as raw JSON, PacketEvents decodes it into a
   * {@code net.kyori.adventure.text.Component} instead, so the twin serialises the component back
   * to JSON ({@link #titleJson(Component)}) and runs the identical substring test on the result.
   * <p>
   * The container type id is deliberately <i>not</i> substituted for the title. The type id on
   * 1.14+ and the {@code legacyType} string below it identify a beacon <i>menu</i>, while this
   * check keys on the title, so a beacon opened under a custom title takes the other branch on both
   * engines - keying on the type would silently change which windows count as "the client will not
   * send a close window packet".
   * <p>
   * Two differences from the ProtocolLib path remain, neither of them able to flip the test:
   * re-serialising a component can reorder the JSON members vanilla wrote (the translation key
   * itself survives verbatim, which is all the substring test looks at), and a title PacketEvents
   * decodes as null becomes an empty string here, where the ProtocolLib path would raise a
   * {@link NullPointerException}.
   */
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW
    },
    ignoreCancelled = false
  )
  public void sentOpenInventory(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    PacketContainer packet = event.getPacket();

    WrappedChatComponent chatComponent = packet.getChatComponents().read(0);
    String json = chatComponent.getJson();
    handleOpenInventory(player, inventoryData, json);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      OPEN_WINDOW
    },
    ignoreCancelled = false
  )
  public void sentOpenInventory(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    Component title = new WrapperPlayServerOpenWindow(event).getTitle();
    handleOpenInventory(player, inventoryData, titleJson(title));
  }

  /**
   * @return the window title as the JSON ProtocolLib would have read straight off the packet.
   * Prefers Adventure's own serialiser and falls back to the copy PacketEvents bundles, which is
   * guaranteed to be present wherever the PacketEvents engine runs at all.
   */
  private static String titleJson(Component title) {
    if (title == null) {
      return "";
    }
    if (TITLE_SERIALIZER != null) {
      return TITLE_SERIALIZER.serialize(title);
    }
    return AdventureSerializer.toJson(title);
  }

  /** Engine independent open window handling; both engines supply the raw title JSON. */
  private void handleOpenInventory(
    Player player, InventoryMetadata inventoryData, String titleJson
  ) {
    // For some reason the client doesn't send a close window packet after closing
    // the beacon window. Therefore, we pretend the player does not have an open
    // inventory if he opens the beacon window to avoid further issues.
    boolean clientDoesNotSendCloseWindow = titleJson.contains("container.beacon");

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
  public void receiveClientCommand(PacketEvent event) {
    Player player = event.getPlayer();
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      openInventory(player);
    }
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveClientCommand(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    WrapperPlayClientClientStatus.Action action = new WrapperPlayClientClientStatus(event).getAction();
    if (action == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
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
  public void sentCloseInventory(PacketEvent event) {
    sentCloseInventory(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      PacketId.Server.CLOSE_WINDOW
    },
    ignoreCancelled = false
  )
  public void sentCloseInventory(Player player) {
    if (player == null) {
      return;
    }
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
  public void receiveCloseWindow(PacketEvent event) {
    closeInventory(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOW,
    packetsIn = {
      PacketId.Client.CLOSE_WINDOW
    }
  )
  public void receiveCloseWindow(Player player) {
    if (player == null) {
      return;
    }
    closeInventory(player);
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
  public void sentRespawn(PacketEvent event) {
    sentRespawn(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      RESPAWN
    },
    ignoreCancelled = false
  )
  public void sentRespawn(Player player) {
    if (player == null) {
      return;
    }
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
    handleWindowItems(user, new ProtocolLibWindowItemView(user.player(), reader));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {WINDOW_ITEMS}
  )
  public void on(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsWindowItemView view = PacketEventsWindowItemView.of(event);
    if (view == null) {
      return;
    }
    handleWindowItems(UserRepository.userOf(player), view);
  }

  /** Engine independent window content handling; see {@link WindowItemView}. */
  private void handleWindowItems(User user, WindowItemView view) {
    if (view.windowId() == 0) {
      List<String> collect = view.itemMap().values().stream().map(itemStack -> itemStack.getType().name()).collect(Collectors.toList());
      user.tickFeedback(() -> user.meta().inventory().setItems(collect));
//      System.out.println(collect);
    }
  }
}