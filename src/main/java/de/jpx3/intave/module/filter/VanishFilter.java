package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.view.PacketEventsPlayerInfoView;
import de.jpx3.intave.packet.view.PacketEventsTabCompleteView;
import de.jpx3.intave.packet.view.PlayerInfoView;
import de.jpx3.intave.packet.view.ProtocolLibPlayerInfoView;
import de.jpx3.intave.packet.view.ProtocolLibTabCompleteView;
import de.jpx3.intave.packet.view.TabCompleteView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class VanishFilter extends Filter {
  private final boolean disabled;

  public VanishFilter(IntavePlugin plugin) {
    super("vanish");
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);
  }

  @PacketSubscription(
    packetsOut = {PLAYER_INFO}
  )
  public void on(PacketEvent event) {
    handlePlayerInfo(new ProtocolLibPlayerInfoView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {PLAYER_INFO}
  )
  public void on(PacketSendEvent event) {
    PacketEventsPlayerInfoView view = PacketEventsPlayerInfoView.of(event);
    if (view == null || view.isRemoval()) {
      return;
    }
    handlePlayerInfo(view);
  }

  /** Engine independent tab list vanish handling; see {@link PlayerInfoView}. */
  private void handlePlayerInfo(PlayerInfoView view) {
    Player player = view.player();
    if (player == null) {
      view.release();
      return;
    }
//    System.out.println("Player info packet: " + packet);

    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    for (PlayerInfoView.Action action : view.actions()) {
      switch (action) {
        case ADD_PLAYER:
          // The client learns about these profiles here, so they become visible to it from now on.
          shownPlayers.addAll(view.entryIds());
          break;
        case UPDATE_GAME_MODE:
        case UPDATE_LATENCY:
          // An update about a profile the client was never told about would reveal it.
          view.removeEntriesIf(uuid -> !shownPlayers.contains(uuid));
          break;
        case REMOVE_PLAYER:
          // remove() both forgets the profile and reports whether the client had ever seen it;
          // a removal for a profile it never saw added is dropped.
          view.removeEntriesIf(uuid -> !shownPlayers.remove(uuid));
          break;
        default:
          // Display name, listed flag, chat initialisation and friends: nothing to filter.
          break;
      }
    }

    if (view.entriesEmpty()) {
      view.setCancelled(true);
//      System.out.println("Cancelled empty player info packet");
    }

    view.shuffleEntries();
    view.release();
  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    prioritySlot = PrioritySlot.EXTERNAL,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketEvent event) {
    handleTabComplete(new ProtocolLibTabCompleteView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    prioritySlot = PrioritySlot.EXTERNAL,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketSendEvent event) {
    PacketEventsTabCompleteView view = PacketEventsTabCompleteView.of(event);
    if (view == null) {
      return;
    }
    handleTabComplete(view);
  }

  /** Engine independent tab completion vanish handling; see {@link TabCompleteView}. */
  private void handleTabComplete(TabCompleteView view) {
    Player player = view.player();
    if (player == null) {
      view.release();
      return;
    }
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    String[] stuff = view.matches();
    if (stuff != null) {
      List<String> playerNames = Bukkit.getOnlinePlayers().stream()
        .map(Player::getName).collect(Collectors.toList());
      List<String> hiddenPlayers = Lists.newArrayList();
      for (String name : playerNames) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
          continue;
        }
        if (!shownPlayers.contains(target.getUniqueId())) {
          hiddenPlayers.add(name);
        }
      }
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff).filter(string -> !hiddenPlayers.contains(string)).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        view.setMatches(newTabCompletions.toArray(new String[0]));
//        Synchronizer.synchronize(() -> {
//          player.sendMessage("Removed " + (stuff.length - newTabCompletions.size()) + " hidden players from tab complete");
//        });
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage("Tab: " + Arrays.toString(stuff) + " -> " + newTabCompletions);
//      });
    }
    view.release();
  }

//  @PacketSubscription(
//    packetsOut = {
//      SCOREBOARD_TEAM
//    }
//  )
//  public void onTeam(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    User user = UserRepository.userOf(player);
//    ProtocolMetadata protocol = user.meta().protocol();
//    Set<UUID> shownPlayers = protocol.shownPlayers;
//    String teamName = packet.getStrings().readSafely(0);
//    shownPlayers.removeIf(uuid -> teamName.contains(Bukkit.getPlayer(uuid).getName()));
//  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO_REMOVE
    }
  )
  public void onRemoval(PacketEvent event) {
    handlePlayerInfoRemoval(new ProtocolLibPlayerInfoView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      PLAYER_INFO_REMOVE
    }
  )
  public void onRemoval(PacketSendEvent event) {
    PacketEventsPlayerInfoView view = PacketEventsPlayerInfoView.of(event);
    if (view == null || !view.isRemoval()) {
      return;
    }
    handlePlayerInfoRemoval(view);
  }

  /** Engine independent tab list removal handling; see {@link PlayerInfoView}. */
  private void handlePlayerInfoRemoval(PlayerInfoView view) {
    Player player = view.player();
    if (player == null) {
      view.release();
      return;
    }
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;
    view.removeEntriesIf(uuid -> !shownPlayers.contains(uuid));
    view.release();
  }

  @Override
  protected boolean enabled() {
    return !disabled && super.enabled();
  }
}
