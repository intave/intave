package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import de.jpx3.intave.adapter.MinecraftVersions;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public final class TablistMutator {
  public static void addToTabList(
    Player player,
    UserProfile profile,
    String tabListName
  ) {
    int latency = ThreadLocalRandom.current().nextInt(20, 200);
    Component displayName = Component.text(tabListName);
    if (modernPlayerInfo()) {
      send(player, new WrapperPlayServerPlayerInfoUpdate(
        EnumSet.of(
          WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
          WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
          WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
          WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
          WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
        ),
        new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
          profile,
          true,
          latency,
          GameMode.SURVIVAL,
          displayName,
          null
        )
      ));
    } else {
      send(player, new WrapperPlayServerPlayerInfo(
        WrapperPlayServerPlayerInfo.Action.ADD_PLAYER,
        new WrapperPlayServerPlayerInfo.PlayerData(displayName, profile, GameMode.SURVIVAL, latency)
      ));
    }
  }

  public static void removeFromTabList(
    Player player,
    UserProfile profile
  ) {
    if (modernPlayerInfo()) {
      send(player, new WrapperPlayServerPlayerInfoRemove(profile.getUUID()));
    } else {
      send(player, new WrapperPlayServerPlayerInfo(
        WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,
        new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.SURVIVAL, 0)
      ));
    }
  }

  public static void updateLatency(
    Player player,
    UserProfile profile,
    int latency,
    String displayName
  ) {
    Component component = Component.text(displayName);
    if (modernPlayerInfo()) {
      send(player, new WrapperPlayServerPlayerInfoUpdate(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
        new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
          profile,
          true,
          latency,
          GameMode.SURVIVAL,
          component,
          null
        )
      ));
    } else {
      send(player, new WrapperPlayServerPlayerInfo(
        WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY,
        new WrapperPlayServerPlayerInfo.PlayerData(component, profile, GameMode.SURVIVAL, latency)
      ));
    }
  }

  private static boolean modernPlayerInfo() {
    return MinecraftVersions.VER1_19_3.atOrAbove();
  }

  private static void send(Player player, PacketWrapper<?> packet) {
    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
  }
}
