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

package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.PlayerInfoReader;
import de.jpx3.intave.share.PlayerInfoAction;
import de.jpx3.intave.packet.reader.PlayerInfoReader.PlayerInfoEntry;
import de.jpx3.intave.packet.reader.PlayerInfoRemoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

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
  public void on(Player player, Cancellable cancellable, PlayerInfoReader reader) {
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    Set<PlayerInfoAction> actions = reader.playerInfoActions();
    if (actions.isEmpty() || actions.contains(null)) {
      return;
    }
    List<PlayerInfoEntry> playerInfos = reader.playerInfoEntries();
    if (playerInfos == null) {
      return;
    }

    synchronized (protocol) {
      Set<UUID> updatedShownPlayers = new HashSet<>(shownPlayers);

      for (PlayerInfoAction action : actions) {
        switch (action) {
          case ADD_PLAYER:
            playerInfos.forEach(data -> updatedShownPlayers.add(data.profileId()));
            break;
          case UPDATE_GAME_MODE:
          case UPDATE_LATENCY:
            playerInfos.removeIf(data -> !updatedShownPlayers.contains(data.profileId()));
            break;
          case REMOVE_PLAYER:
            playerInfos.removeIf(data -> !updatedShownPlayers.remove(data.profileId()));
            break;
        }
      }

      if (playerInfos.isEmpty()) {
        cancellable.setCancelled(true);
        return;
      }

      Collections.shuffle(playerInfos);
      if (reader.writePlayerInfoEntries(playerInfos)) {
        shownPlayers.clear();
        shownPlayers.addAll(updatedShownPlayers);
      }
    }
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
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    PacketContainer packet = event.getPacket();
    String[] stuff = packet.getStringArrays().readSafely(0);
    if (stuff != null) {
      List<String> playerNames = Bukkit.getOnlinePlayers().stream()
        .map(Player::getName).collect(Collectors.toList());
      List<String> hiddenPlayers = Lists.newArrayList();
      synchronized (protocol) {
        for (String name : playerNames) {
          Player target = Bukkit.getPlayerExact(name);
          if (target == null) {
            continue;
          }
          if (!shownPlayers.contains(target.getUniqueId())) {
            hiddenPlayers.add(name);
          }
        }
      }
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff).filter(string -> !hiddenPlayers.contains(string)).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        packet.getStringArrays().writeSafely(0, newTabCompletions.toArray(new String[0]));
//        Synchronizer.synchronize(() -> {
//          player.sendMessage("Removed " + (stuff.length - newTabCompletions.size()) + " hidden players from tab complete");
//        });
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage("Tab: " + Arrays.toString(stuff) + " -> " + newTabCompletions);
//      });
    }
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
  public void onRemoval(Player player, PlayerInfoRemoveReader reader) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;
    List<UUID> uuids = reader.playersToRemove();
    synchronized (protocol) {
      uuids.removeIf(uuid -> !shownPlayers.contains(uuid));
    }
  }

  @Override
  protected boolean enabled() {
    return !disabled && super.enabled();
  }
}
