package de.jpx3.intave.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.CHAT;

public final class ChatWordFilter extends Filter {
  private final IntavePlugin plugin;

  public ChatWordFilter(IntavePlugin plugin) {
    super("chat-insults");
    this.plugin = plugin;
  }

  @PacketSubscription(
    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      CHAT
    }
  )
  public void sendChatMessage(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    WrappedChatComponent wrappedChatComponent = packet.getChatComponents().readSafely(0);

    if (wrappedChatComponent == null) {
      return;
    }

    StringBuilder messageBuilder = new StringBuilder();
    BaseComponent[] baseComponents = baseComponentsOf(wrappedChatComponent.getJson());
    for (BaseComponent baseComponent : baseComponents) {
      messageBuilder.append(baseComponent.toLegacyText());
    }
    String message = messageBuilder.toString();
    message = ChatColor.stripColor(message);

    boolean messageContainsOwner = message.contains(player.getName());
    boolean messageContainsSomeoneWithsReceiversIp = false;

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (message.contains(player.getName()) && onlinePlayer.getAddress().equals(player.getAddress())) {
        messageContainsSomeoneWithsReceiversIp = true;
        break;
      }
    }

    boolean bypassRestrictions = messageContainsOwner || messageContainsSomeoneWithsReceiversIp;

    if (bypassRestrictions) {
      return;
    }

    for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
      message = message.replace(onlinePlayer.getName(), "");
      message = message.replace(onlinePlayer.getName().toLowerCase(Locale.ROOT), "");
      message = message.replace(onlinePlayer.getName().toUpperCase(Locale.ROOT), "");
    }

    String messageToLower = message.toLowerCase(Locale.ROOT);
    List<String> badwords = Arrays.asList("prestige", "koks", "liquidbounce", "eject");

    for (String badword : badwords) {
      if (messageToLower.contains(badword)) {
        event.setCancelled(true);
      }
    }
  }

  public static BaseComponent[] baseComponentsOf(String json) {
    // Remove the json identifier prefix
    json = json.replace("[JSON]", "");

    // Parse it
    return ComponentSerializer.parse(json);
  }
}
