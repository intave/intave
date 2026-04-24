package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
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
  public void on(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
      WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate((PacketSendEvent) event);
      handleModernInfo(packet, shownPlayers);
      if (packet.getEntries().isEmpty()) {
        event.setCancelled(true);
        return;
      }
      Collections.shuffle(packet.getEntries());
      event.markForReEncode(true);
      return;
    }

    WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo((PacketSendEvent) event);
    handleLegacyInfo(packet, shownPlayers);
    if (packet.getPlayerDataList().isEmpty()) {
      event.setCancelled(true);
      return;
    }
    Collections.shuffle(packet.getPlayerDataList());
    event.markForReEncode(true);
  }

  private void handleLegacyInfo(WrapperPlayServerPlayerInfo packet, Set<UUID> shownPlayers) {
    WrapperPlayServerPlayerInfo.Action action = packet.getAction();
    List<WrapperPlayServerPlayerInfo.PlayerData> entries = packet.getPlayerDataList();
    if (action == WrapperPlayServerPlayerInfo.Action.ADD_PLAYER) {
      entries.forEach(data -> {
        UUID uuid = profileId(data.getUserProfile());
        if (uuid != null) {
          shownPlayers.add(uuid);
        }
      });
    } else if (action == WrapperPlayServerPlayerInfo.Action.UPDATE_GAME_MODE ||
      action == WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY) {
      entries.removeIf(data -> {
        UUID uuid = profileId(data.getUserProfile());
        return uuid != null && !shownPlayers.contains(uuid);
      });
    } else if (action == WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER) {
      entries.removeIf(data -> {
        UUID uuid = profileId(data.getUserProfile());
        return uuid != null && !shownPlayers.remove(uuid);
      });
    }
  }

  private void handleModernInfo(WrapperPlayServerPlayerInfoUpdate packet, Set<UUID> shownPlayers) {
    EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = packet.getActions();
    List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = packet.getEntries();
    if (actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) {
      entries.forEach(data -> shownPlayers.add(data.getProfileId()));
    }
    if (actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE) ||
      actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY) ||
      actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME) ||
      actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED)) {
      entries.removeIf(data -> !shownPlayers.contains(data.getProfileId()));
    }
  }

  private UUID profileId(UserProfile profile) {
    return profile == null ? null : profile.getUUID();
  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    prioritySlot = PrioritySlot.EXTERNAL,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    WrapperPlayServerTabComplete packet = new WrapperPlayServerTabComplete((PacketSendEvent) event);
    List<WrapperPlayServerTabComplete.CommandMatch> matches = packet.getCommandMatches();
    if (matches != null) {
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
      boolean changed = matches.removeIf(match -> hiddenPlayers.contains(match.getText()));
      if (changed) {
        event.markForReEncode(true);
      }
    }
  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO_REMOVE
    }
  )
  public void onRemoval(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;
    WrapperPlayServerPlayerInfoRemove packet = new WrapperPlayServerPlayerInfoRemove((PacketSendEvent) event);
    List<UUID> uuids = packet.getProfileIds();
    uuids.removeIf(uuid -> !shownPlayers.contains(uuid));
    event.markForReEncode(true);
  }

  @Override
  protected boolean enabled() {
    return !disabled && super.enabled();
  }
}
