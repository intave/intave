package de.jpx3.intave.player.fake.event;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.PLAYER_INFO;

public final class PlayerPingPacketDispatcher implements PacketEventSubscriber {
  private static final long MIN_TIME_BETWEEN_PLAYER_INFO_UPDATE = 10_000;

  public PlayerPingPacketDispatcher(IntavePlugin plugin) {
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void onPacketSending(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    if (fakePlayer == null) {
      return;
    }
    tryAppendFakePlayerToPing(fakePlayer, packet);
  }

  private void tryAppendFakePlayerToPing(FakePlayer fakePlayer, PacketContainer packet) {
    EnumWrappers.PlayerInfoAction action = packet.getPlayerInfoAction().read(0);
    if (action != EnumWrappers.PlayerInfoAction.UPDATE_LATENCY) {
      return;
    }
    if (System.currentTimeMillis() - fakePlayer.lastPingPacketSent >= MIN_TIME_BETWEEN_PLAYER_INFO_UPDATE) {
      List<PlayerInfoData> playerInfoData = packet.getPlayerInfoDataLists().readSafely(0);
      appendToPingPacket(fakePlayer, playerInfoData, packet);
    }
  }

  private void appendToPingPacket(
    FakePlayer fakePlayer,
    List<PlayerInfoData> playerInfoDataList,
    PacketContainer packet
  ) {
    int latency = fakePlayer.nextLatency();
    WrappedGameProfile profile = fakePlayer.profile();
    String name = profile.getName();
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(name);
    PlayerInfoData playerInfoData = new PlayerInfoData(profile, latency, fakePlayer.gameMode(), wrappedChatComponent);
    playerInfoDataList.add(playerInfoData);
    packet.getPlayerInfoDataLists().write(0, playerInfoDataList);
    fakePlayer.lastPingPacketSent = System.currentTimeMillis();
  }
}