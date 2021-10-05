package de.jpx3.intave.player.fake;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TablistMutator {
  private final static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

  public static void addToTabList(
    Player player,
    WrappedGameProfile wrappedGameProfile,
    String tabListName
  ) {
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(tabListName);
    addToTabList(player, wrappedGameProfile, wrappedChatComponent);
  }

  private static void addToTabList(
    Player player,
    WrappedGameProfile profile,
    WrappedChatComponent wrappedChatComponent
  ) {
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
    PlayerInfoData playerInfoData = new PlayerInfoData(
      profile, ThreadLocalRandom.current().nextInt(20, 200),
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    PacketSender.sendServerPacket(player, packet);
  }

  public static void removeFromTabList(
    Player player,
    WrappedGameProfile profile
  ) {
    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(profile.getName());
    PlayerInfoData playerInfoData = new PlayerInfoData(
      profile, ThreadLocalRandom.current().nextInt(20, 200),
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    PacketSender.sendServerPacket(player, packet);
  }
}
