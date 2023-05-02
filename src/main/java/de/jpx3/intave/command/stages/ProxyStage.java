package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.connect.proxy.protocol.IntavePacket;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketOutExecuteCommand;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketOutPunishment;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class ProxyStage extends CommandStage {
  private static ProxyStage singletonInstance;
  private final IntavePlugin plugin;

  private ProxyStage() {
    super(BaseStage.singletonInstance(), "proxy");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = {"proxcommand", "command"},
    usage = "<player> <command...>",
    permission = "intave.command.proxy",
    description = "Remotely executes commands on the proxy"
  )
  public void proxyCommand(CommandSender sender, Player uplink, String[] commandParts) {
    if (!plugin.proxy().isChannelOpen()) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Not connected to a proxy");
      return;
    }
    String command = Arrays.stream(commandParts).map(commandPart -> commandPart + " ").collect(Collectors.joining()).trim();
    IntavePacket packet = new IntavePacketOutExecuteCommand(uplink.getUniqueId(), command);
    plugin.proxy().sendPacket(uplink, packet);
    sender.sendMessage(IntavePlugin.prefix() + "Remote command execution of \"/" + command + "\" issued");
  }

  @SubCommand(
    selectors = {"proxkick", "kick"},
    usage = "<player> [<message...>]",
    permission = "intave.command.proxy",
    description = "Remotely kicks the target player from the proxy"
  )
  public void proxyKick(CommandSender sender, Player target, @Optional String[] message) {
    if (!plugin.proxy().isChannelOpen()) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Not connected to a proxy");
      return;
    }
    String reason = message == null ? "None provided" : Arrays.stream(message).map(commandPart -> commandPart + " ").collect(Collectors.joining()).trim();
    performPunishment(target, IntavePacketOutPunishment.PunishmentType.KICK, reason);
    sender.sendMessage(IntavePlugin.prefix() + "Remote kick execution issued");
  }

  @SubCommand(
    selectors = {"proxtempban", "tempban"},
    usage = "<player> [<message...>]",
    permission = "intave.command.proxy",
    description = "Remotely temp-bans the target player from the proxy"
  )
  public void proxyTempBan(CommandSender sender, Player target, @Optional String[] reasonParts) {
    if (!plugin.proxy().isChannelOpen()) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Not connected to a proxy");
      return;
    }
    String reason = reasonParts == null ? "None provided" : Arrays.stream(reasonParts).map(commandPart -> commandPart + " ").collect(Collectors.joining()).trim();
    performPunishment(target, IntavePacketOutPunishment.PunishmentType.TEMP_BAN, reason);
    sender.sendMessage(IntavePlugin.prefix() + "Remote temp-ban execution issued");
  }

  @SubCommand(
    selectors = {"proxban", "ban"},
    usage = "<player> [<message...>]",
    permission = "intave.command.proxy",
    description = "Remotely bans the target player from the proxy"
  )
  public void proxyBan(CommandSender sender, Player target, @Optional String[] reasonParts) {
    if (!plugin.proxy().isChannelOpen()) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Not connected to a proxy");
      return;
    }
    String reason = reasonParts == null ? "None provided" : Arrays.stream(reasonParts).map(commandPart -> commandPart + " ").collect(Collectors.joining()).trim();
    performPunishment(target, IntavePacketOutPunishment.PunishmentType.BAN, reason);
    sender.sendMessage(IntavePlugin.prefix() + "Remote ban execution issued");
  }

  private void performPunishment(
    Player target,
    IntavePacketOutPunishment.PunishmentType type,
    String reason
  ) {
    long tempbanEndTimestamp = System.currentTimeMillis() + 1000 * 60 * 60;
    IntavePacket packet = new IntavePacketOutPunishment(target.getUniqueId(), type, reason.trim(), tempbanEndTimestamp);
    plugin.proxy().sendPacket(target, packet);
  }

  public static ProxyStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new ProxyStage();
    }
    return singletonInstance;
  }
}
