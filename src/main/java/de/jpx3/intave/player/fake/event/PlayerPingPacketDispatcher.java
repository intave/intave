package de.jpx3.intave.player.fake.event;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.PLAYER_INFO;

public final class PlayerPingPacketDispatcher implements PacketEventSubscriber {
  private static final long MIN_TIME_BETWEEN_PLAYER_INFO_UPDATE = 10_000;

  public PlayerPingPacketDispatcher(IntavePlugin plugin) {
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void onPacketSending(ProtocolPacketEvent event) {
    User user = UserRepository.userOf((Player) event.getPlayer());
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    if (fakePlayer == null || !readyForUpdate(fakePlayer)) {
      return;
    }
    if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
      appendModernLatency(event, fakePlayer);
    } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
      appendLegacyLatency(event, fakePlayer);
    }
  }

  private boolean readyForUpdate(FakePlayer fakePlayer) {
    return System.currentTimeMillis() - fakePlayer.lastPingPacketSent >= MIN_TIME_BETWEEN_PLAYER_INFO_UPDATE;
  }

  private void appendModernLatency(ProtocolPacketEvent event, FakePlayer fakePlayer) {
    WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate((PacketSendEvent) event);
    if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY)) {
      return;
    }
    UserProfile profile = fakePlayer.profile();
    List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = packet.getEntries();
    entries.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
      profile,
      true,
      fakePlayer.nextLatency(),
      fakePlayer.gameMode(),
      Component.text(profile.getName()),
      null
    ));
    fakePlayer.lastPingPacketSent = System.currentTimeMillis();
    event.markForReEncode(true);
  }

  private void appendLegacyLatency(ProtocolPacketEvent event, FakePlayer fakePlayer) {
    WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo((PacketSendEvent) event);
    if (packet.getAction() != WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY) {
      return;
    }
    UserProfile profile = fakePlayer.profile();
    packet.getPlayerDataList().add(new WrapperPlayServerPlayerInfo.PlayerData(
      Component.text(profile.getName()),
      profile,
      fakePlayer.gameMode(),
      fakePlayer.nextLatency()
    ));
    fakePlayer.lastPingPacketSent = System.currentTimeMillis();
    event.markForReEncode(true);
  }
}
