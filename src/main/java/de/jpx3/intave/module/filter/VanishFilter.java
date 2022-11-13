package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerInfoDataConverter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode.SURVIVAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.PLAYER_INFO;

public final class VanishFilter extends Filter {
  private final IntavePlugin plugin;

  public VanishFilter(IntavePlugin plugin) {
    super("");
    this.plugin = plugin;
  }

  private static final PlayerInfoData FAKE_JPX3_DATA = new PlayerInfoData(
    new WrappedGameProfile(
      UUID.fromString("5ee6db6d-6751-4081-9cbf-28eb0f6cc055"),
      "Jpx3"
    ),
    ThreadLocalRandom.current().nextInt(1, 100),
    SURVIVAL,
    null
  );

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    List<UUID> shownPlayers = protocol.shownPlayers;

    EnumWrappers.PlayerInfoAction playerInfoAction = packet.getPlayerInfoAction().read(0);
    StructureModifier<List<PlayerInfoData>> lists = packet.getLists(PlayerInfoDataConverter.threadConverter());
    List<PlayerInfoData> playerInfoDataList = lists.read(0);

    switch (playerInfoAction) {
      case ADD_PLAYER:
        playerInfoDataList.forEach(playerInfoData -> {
          UUID uuid = playerInfoData.getProfile().getUUID();
          if (shownPlayers.contains(uuid)) {
            return;
          }
          shownPlayers.add(uuid);
        });
        break;
      case UPDATE_LATENCY:
        playerInfoDataList.removeIf(playerInfoData -> {
          UUID uuid = playerInfoData.getProfile().getUUID();
          return !shownPlayers.contains(uuid);
        });
        if (ThreadLocalRandom.current().nextInt(0, 100) < 5) {
          playerInfoDataList.add(FAKE_JPX3_DATA);
        }
        break;
      case REMOVE_PLAYER:
        playerInfoDataList.forEach(playerInfoData -> {
          UUID uuid = playerInfoData.getProfile().getUUID();
          shownPlayers.remove(uuid);
        });
        break;
    }
    Collections.shuffle(playerInfoDataList);
    lists.write(0, playerInfoDataList);
  }

  @Override
  protected boolean enabled() {
    return true;
  }
}
