package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Forward;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.security.LicenseAccess;
import de.jpx3.intave.tool.AccessHelper;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BaseStage extends CommandStage {
  private static BaseStage singletonInstance;

  private BaseStage() {
    super(null, "/intave");
  }

  @SubCommand(
    selectors = "verbose",
    usage = "[<player...>]",
    description = "Toggle verbose messages",
    permission = "intave.command.verbose"
  )
  public void verboseCommand(User user, @Optional Player[] selectedPlayers) {
    Player player = user.player();
    boolean receivesVerbose = user.receives(MessageChannel.VERBOSE);

    if (user.receives(MessageChannel.VERBOSE)) {
      if (selectedPlayers != null && !user.hasChannelConstraint(MessageChannel.VERBOSE)) {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.VERBOSE, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You have specified verbose output to " + names);
        return;
      }
    }

    user.toggleReceive(MessageChannel.VERBOSE);
    user.removeChannelConstraint(MessageChannel.VERBOSE);

    if (receivesVerbose) {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving verbose output");
    } else {
      if (selectedPlayers == null) {
        String target = ChatColor.RED + "everyone";
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving verbose output for " + target);
      } else {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.VERBOSE, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now" + IntavePlugin.defaultColor() + " receiving verbose output for " + names);
      }
    }
  }

  @SubCommand(
    selectors = {"alert", "alerts"},
    hideInHelp = true,
    description = ""
  )
  public void redirectToVerbose(CommandSender sender) {
    if (!BukkitPermissionCheck.permissionCheck(sender, "intave.command.verbose")) {
      showAllCommands(sender);
    } else {
      sender.sendMessage(IntavePlugin.prefix() + "Did you mean verbose or notify?");
    }
  }

  private static String describePlayerList(List<String> elements) {
    int size = elements.size();
    String defaultColor = IntavePlugin.defaultColor();
    if(size == 0) {
      return defaultColor + "nobody";
    } else if (size == 1) {
      return elements.get(0);
    } else {
      return defaultColor + String.join(defaultColor + ", ", elements.subList(0, size - 1)) + defaultColor + " and " + elements.get(size - 1);
    }
  }

  @SubCommand(
    selectors = "notify",
    usage = "",
    description = "Toggle notifications",
    permission = "intave.command.notify"
  )
  public void notifyCommand(User user) {
    Player player = user.player();

    boolean receiveNotify = user.receives(MessageChannel.NOTIFY);
    user.toggleReceive(MessageChannel.NOTIFY);

    if (receiveNotify) {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving notifications");
    } else {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving notifications");
    }
  }

  @SubCommand(
    selectors = "version",
    usage = "",
    description = "Show version info"
  )
  public void versionCommand(CommandSender commandSender) {
    sendVersionMessage(commandSender);
  }

  @SubCommand(
    selectors = "proxy",
    usage = "",
    description = "Access proxy related features",
    permission = "intave.command.proxy"
  )
  @Forward(
    target = ProxyStage.class
  )
  public void proxyCommand(CommandSender sender) {
  }

  @SubCommand(
    selectors = "root",
    usage = "",
    description = "",
    permission = "sibyl",
    hideInHelp = true
  )
  @Forward(
    target = RootStage.class
  )
  public void rootCommand(User user) {
  }

  @SubCommand(
    selectors = "diagnostics",
    usage = "",
    description = "Runtime and performance data output",
    permission = "intave.command.diagnostics.*"
  )
  @Forward(
    target = DiagnosticsStage.class
  )
  public void diagnosticsCommand(CommandSender commandSender) {
  }

  @SubCommand(
    selectors = "internals",
    usage = "",
    description = "Console-reserved commands",
    permission = "intave.command.internals.*"
  )
  @Forward(
    target = InternalsStage.class
  )
  public void internalCommand(User user) {
  }

  @Override
  protected void showAllCommands(CommandSender sender) {
    boolean hasIntavePermission = BukkitPermissionCheck.permissionCheck(sender, "intave.command");
    if (hasIntavePermission) {
      super.showAllCommands(sender);
    } else {
      sendVersionMessage(sender);
    }
  }

  @Native
  private void sendVersionMessage(CommandSender player) {
    boolean hasVersionViewPermission = BukkitPermissionCheck.permissionCheck(player, "intave.command");

    IntaveVersion versionInformation = IntavePlugin.singletonInstance().versions().versionInformation(IntavePlugin.version());
    String version;
    if (!hasVersionViewPermission) {
      version = "(version hidden)";
    } else if (versionInformation != null) {
      boolean outdated = versionInformation.outdated();
      version = IntavePlugin.version() + " (" + (outdated ? "outdated, " : "") + DurationTranslator.translateDuration(AccessHelper.now() - versionInformation.release()) + " old)";
    } else {
      version = IntavePlugin.version() + " (unlisted)";
    }

    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;

    String prefix = IntavePlugin.prefix();
    player.sendMessage(new String[]{
      prefix + "Running Intave " + version,
      prefix + "Serving as automated cheat-removal and defense tool",
      prefix + "Visit " + ChatColor.UNDERLINE + "intave.de" + IntavePlugin.defaultColor() + " for more information",
    });

    if (IntaveControl.GOMME_MODE) {
      player.sendMessage(prefix + "Certified for GommeHDnet (trusted)");
    } else if (IntavePlugin.isInOfflineMode()) {
      player.sendMessage(prefix + "Certification failed, Intave servers down?");
    } else if (LicenseAccess.network().equals("~bypass")) {
      player.sendMessage(prefix + "Certification disabled, trust release");
    } else {
      player.sendMessage(prefix + "Certified for " + LicenseAccess.network() + (enterprise && !partner ? " (verified)" : (partner ? " (trusted)" : "")));
    }
  }

  public static BaseStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new BaseStage();
    }
    return singletonInstance;
  }
}
