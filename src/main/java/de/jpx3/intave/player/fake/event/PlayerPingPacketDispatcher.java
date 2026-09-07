package de.jpx3.intave.player.fake.event;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.PacketEventsPlayerInfoView;
import de.jpx3.intave.packet.view.PlayerInfoView;
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

  /**
   * Appends the fake player's tab list latency row to the latency updates the server already sends.
   * <p>
   * Unlike every other ported subscription this one does not read a packet, it writes an entry into
   * one, so the twin below needs {@code WrapperPlayServerPlayerInfo.PlayerData} whose constructors
   * take an Adventure {@code Component} display name. That is now on the compile classpath, and
   * {@link PacketEventsPlayerInfoView#appendLegacyEntry} builds the entry with
   * {@code Component.text(name)}, the exact counterpart of the
   * {@code WrappedChatComponent.fromText(name)} used here.
   *
   * <h2>Version range</h2>
   * Both paths are legacy only, and by construction rather than by choice. ProtocolLib's
   * {@code getPlayerInfoAction()} resolves against the single action field the pre 1.19.3 packet
   * carries; 1.19.3 replaced that field with an {@code EnumSet}, so the modifier finds no field and
   * the first line of {@link #tryAppendFakePlayerToPing(FakePlayer, PacketContainer)} throws there.
   * PacketEvents models the same split as two wrappers, {@code WrapperPlayServerPlayerInfo} and
   * {@code WrapperPlayServerPlayerInfoUpdate}, and this subscription resolves to the former only,
   * so the twin simply never fires on 1.19.3 and above.
   * <p>
   * PacketEvents <i>could</i> cover the modern packet - {@code WrapperPlayServerPlayerInfoUpdate}
   * exposes a mutable entry list of its own - but a fake latency row that suddenly appears on 1.19.3
   * and above is a behaviour change, not a port, so the twin declines it explicitly through
   * {@link PacketEventsPlayerInfoView#isLegacy()} rather than by accident.
   */
  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void onPacketSending(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    if (fakePlayer == null) {
      return;
    }
    tryAppendFakePlayerToPing(fakePlayer, packet);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      PLAYER_INFO
    }
  )
  public void onPacketSending(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    FakePlayer fakePlayer = user.meta().attack().fakePlayer();
    if (fakePlayer == null) {
      return;
    }
    PacketEventsPlayerInfoView view = PacketEventsPlayerInfoView.of(event);
    if (view == null) {
      return;
    }
    tryAppendFakePlayerToPing(fakePlayer, view);
    // Writes the grown entry list back and marks the packet for re-encode, but only if an entry
    // was actually appended; an untouched packet leaves the buffer alone.
    view.release();
  }

  private void tryAppendFakePlayerToPing(FakePlayer fakePlayer, PacketEventsPlayerInfoView view) {
    // The legacy packet carries exactly one action, so containment here is the same test the
    // ProtocolLib path's equality check performs. isLegacy() keeps the 1.19.3+ packet out.
    if (!view.isLegacy() || !view.actions().contains(PlayerInfoView.Action.UPDATE_LATENCY)) {
      return;
    }
    if (System.currentTimeMillis() - fakePlayer.lastPingPacketSent >= MIN_TIME_BETWEEN_PLAYER_INFO_UPDATE) {
      appendToPingPacket(fakePlayer, view);
    }
  }

  private void appendToPingPacket(FakePlayer fakePlayer, PacketEventsPlayerInfoView view) {
    int latency = fakePlayer.nextLatency();
    WrappedGameProfile profile = fakePlayer.profile();
    boolean appended = view.appendLegacyEntry(
      profile.getUUID(), profile.getName(), gameModeOf(fakePlayer.gameMode()), latency
    );
    if (appended) {
      fakePlayer.lastPingPacketSent = System.currentTimeMillis();
    }
  }

  /**
   * Translates the fake player's ProtocolLib game mode into the PacketEvents enum. The legacy
   * packet only encodes a game mode for {@code ADD_PLAYER} and {@code UPDATE_GAME_MODE}, so this
   * value never reaches the wire on the latency update the twin appends to; it is mapped anyway so
   * the appended entry describes the same player the ProtocolLib entry describes. PacketEvents has
   * no counterpart for {@code NOT_SET} and {@code NONE}, which the fake player never uses.
   */
  private static GameMode gameModeOf(EnumWrappers.NativeGameMode gameMode) {
    if (gameMode == EnumWrappers.NativeGameMode.CREATIVE) {
      return GameMode.CREATIVE;
    }
    if (gameMode == EnumWrappers.NativeGameMode.ADVENTURE) {
      return GameMode.ADVENTURE;
    }
    if (gameMode == EnumWrappers.NativeGameMode.SPECTATOR) {
      return GameMode.SPECTATOR;
    }
    return GameMode.SURVIVAL;
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