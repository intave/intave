package de.jpx3.intave.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import static com.comphenix.protocol.PacketType.Play.Server.CHAT;
import static com.comphenix.protocol.PacketType.Play.Server.SET_ACTION_BAR_TEXT;

public final class ActionBar {
	private static final boolean TYPE_AS_GAME_INFO = MinecraftVersions.VER1_12_0.atOrAbove();
	private static final boolean DEDICATED_ACTION_BAR_PACKET = MinecraftVersions.VER1_17_0.atOrAbove();

	public static void sendActionBar(Player player, String message) {
		PacketContainer packet = new PacketContainer(DEDICATED_ACTION_BAR_PACKET ? SET_ACTION_BAR_TEXT : CHAT);
		packet.getChatComponents().write(0, WrappedChatComponent.fromText(message));

		if (!DEDICATED_ACTION_BAR_PACKET) {
			if (TYPE_AS_GAME_INFO) {
				packet.getChatTypes().write(0, EnumWrappers.ChatType.GAME_INFO);
			} else {
				packet.getBytes().write(0, (byte) 2);
			}
		}

		PacketSender.sendServerPacketWithoutEvent(player, packet);
	}
}
