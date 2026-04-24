package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TAB_COMPLETE_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.TAB_COMPLETE_OUT;

public final class CommandFilter extends Filter {
  private final boolean separateEnable;
  private final boolean disabled;
  private final Map<String, String> redirects = new HashMap<>();

  public CommandFilter(IntavePlugin plugin) {
    super("command");
    separateEnable = plugin.settings().getBoolean("command.hide", true);
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);

    ConfigurationSection reroute = plugin.settings().getConfigurationSection("command.reroute");
    if (reroute != null) {
      reroute.getKeys(false).forEach(key -> redirects.put(key, plugin.settings().getString("command.reroute." + key)));
    }
  }

  @PacketSubscription(
    packetsIn = {
      CHAT_IN, TAB_COMPLETE_IN
    }
  )
  public void receiveChatPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    String message = messageOf(event);
    if (message == null) {
      return;
    }

    String trimmedMessage = message.trim().toLowerCase();

    for (Map.Entry<String, String> stringStringEntry : redirects.entrySet()) {
      if (trimmedMessage.startsWith(stringStringEntry.getKey())) {
        // remove the command and replace it with the redirect without regex
        String redirect = stringStringEntry.getValue();
        if (redirect.toLowerCase().contains("root")) {
          continue;
        }
        trimmedMessage = redirect + trimmedMessage.substring(stringStringEntry.getKey().length());
        writeMessage(event, trimmedMessage);
        trimmedMessage = trimmedMessage.trim().toLowerCase();
      }
    }

    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if ((trimmedMessage.startsWith("/iac") || trimmedMessage.startsWith("/intave")) && !permitted) {
      writeMessage(event, "/intavecommandforward");
    }
  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    WrapperPlayServerTabComplete packet = new WrapperPlayServerTabComplete((PacketSendEvent) event);
    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if (permitted) {
      return;
    }
    List<WrapperPlayServerTabComplete.CommandMatch> matches = packet.getCommandMatches();
    if (matches != null) {
      List<WrapperPlayServerTabComplete.CommandMatch> newTabCompletions = Lists.newArrayList();
      matches.stream().filter(match -> !match.getText().contains("/intave") && !match.getText().contains("/iac")).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != matches.size()) {
        packet.setCommandMatches(newTabCompletions);
        event.markForReEncode(true);
      }
    }
  }

  private String messageOf(ProtocolPacketEvent event) {
    PacketTypeCommon type = event.getPacketType();
    PacketReceiveEvent receiveEvent = (PacketReceiveEvent) event;
    if (type == PacketType.Play.Client.CHAT_MESSAGE) {
      return new WrapperPlayClientChatMessage(receiveEvent).getMessage();
    }
    if (type == PacketType.Play.Client.CHAT_COMMAND) {
      return "/" + new WrapperPlayClientChatCommand(receiveEvent).getCommand();
    }
    if (type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
      return "/" + new WrapperPlayClientChatCommandUnsigned(receiveEvent).getCommand();
    }
    if (type == PacketType.Play.Client.TAB_COMPLETE) {
      return new WrapperPlayClientTabComplete(receiveEvent).getText();
    }
    return null;
  }

  private void writeMessage(ProtocolPacketEvent event, String message) {
    PacketTypeCommon type = event.getPacketType();
    PacketReceiveEvent receiveEvent = (PacketReceiveEvent) event;
    if (type == PacketType.Play.Client.CHAT_MESSAGE) {
      new WrapperPlayClientChatMessage(receiveEvent).setMessage(message);
      event.markForReEncode(true);
    } else if (type == PacketType.Play.Client.CHAT_COMMAND) {
      new WrapperPlayClientChatCommand(receiveEvent).setCommand(stripSlash(message));
      event.markForReEncode(true);
    } else if (type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
      new WrapperPlayClientChatCommandUnsigned(receiveEvent).setCommand(stripSlash(message));
      event.markForReEncode(true);
    } else if (type == PacketType.Play.Client.TAB_COMPLETE) {
      new WrapperPlayClientTabComplete(receiveEvent).setText(message);
      event.markForReEncode(true);
    }
  }

  private String stripSlash(String command) {
    return command.startsWith("/") ? command.substring(1) : command;
  }

  @Override
  protected boolean enabled() {
    return (super.enabled() || separateEnable) && !disabled;
  }
}
