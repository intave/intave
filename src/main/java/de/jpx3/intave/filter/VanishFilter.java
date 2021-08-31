package de.jpx3.intave.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.AccessHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerInfoDataConverter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction.UPDATE_LATENCY;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.PLAYER_INFO;

public final class VanishFilter extends Filter {
  private final IntavePlugin plugin;

  public VanishFilter(IntavePlugin plugin) {
    super("");
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerInfoAction playerInfoAction = packet.getPlayerInfoAction().read(0);
    if (playerInfoAction == UPDATE_LATENCY) {
      List<PlayerInfoData> playerInfoDataList = packet.getLists(PlayerInfoDataConverter.threadConverter()).read(0);
      playerInfoDataList.removeIf(playerInfoData -> {
        UUID otherId = playerInfoData.getProfile().getUUID();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(otherId);
        if (AccessHelper.isOnline(offlinePlayer)) {
          Player otherPlayer = offlinePlayer.getPlayer();
          return !player.canSee(otherPlayer);
        }
        return false;
      });
      Collections.shuffle(playerInfoDataList);
    }
  }

  @Override
  protected boolean enabled() {
    return true;
  }
}
