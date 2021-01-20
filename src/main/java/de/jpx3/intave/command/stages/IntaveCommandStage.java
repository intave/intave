package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Forward;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.permission.PermissionCheck;
import de.jpx3.intave.security.LicenseVerification;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.DurationTranslator;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.update.VersionInformation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMessageChannel;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IntaveCommandStage extends CommandStage {
  private static IntaveCommandStage singletonInstance;

  private IntaveCommandStage() {
    super(null, "/intave", 0);
  }

  @SubCommand(
    selectors = "verbose",
    usage = "[<player>]",
    description = "Toggle verbose messages",
    permission = "intave.command.verbose"
  )
  public void verboseCommand(User user, @Optional Player[] selectedPlayers) {
    Player player = user.player();
    boolean receivesVerbose = user.receives(UserMessageChannel.VERBOSE);
    user.toggleReceive(UserMessageChannel.VERBOSE);

    if(receivesVerbose) {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving verbose output");
    } else {
      if(selectedPlayers == null) {
        String target = ChatColor.RED + "global";
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving verbose output for: " + target);
      } else {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).collect(Collectors.toList());
        String names = Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> s + " ").collect(Collectors.joining()).trim();
        user.setChannelConstraint(UserMessageChannel.VERBOSE, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String target = ChatColor.RED + names;
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving verbose output for: " + target);
      }
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

    boolean receiveNotify = user.receives(UserMessageChannel.NOTIFY);
    user.toggleReceive(UserMessageChannel.NOTIFY);

    if(receiveNotify) {
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
  public void versionCommand(User user) {
    Player player = user.player();
    sendVersionMessage(player);
  }

  @SubCommand(
    selectors = "internals",
    usage = "",
    description = "",
    permission = "intave.command.internals.*",
    hideInHelp = true
  )
  @Forward(
    target = IntaveInternalsStage.class
  )
  public void internalCommand(CommandSender commandSender) {
    // not executed
  }

  @Override
  protected void showInfo(CommandSender sender) {
    boolean hasIntavePermission = PermissionCheck.permissionCheck(sender, "intave.command");

    if(hasIntavePermission) {
      super.showInfo(sender);
    } else {
      sendVersionMessage(sender);
    }
  }

  @Native
  private void sendVersionMessage(CommandSender player) {
    boolean hasVersionViewPermission = PermissionCheck.permissionCheck(player, "intave.command");
    boolean versionViewAllowed = false;

    VersionInformation versionInformation = IntavePlugin.singletonInstance().versionList().versionInformation(IntavePlugin.version());

    String version;

    if(versionInformation != null) {
      version = IntavePlugin.version() + " (" + DurationTranslator.translateDuration(AccessHelper.now() - versionInformation.release()) + " old)";
    } else {
      version = IntavePlugin.version() + " (experimental)";
    }

    if(!hasVersionViewPermission) {
      version = "(version hidden)";
    }

    String prefix = IntavePlugin.prefix();
    player.sendMessage(new String[]{
      prefix + "Running Intave " + version,
      prefix + "Made in Germany by the Intave development team",
      prefix + "Visit our website for a full list of contributors"
    });

    if(IntavePlugin.isInOfflineMode()) {
      player.sendMessage(prefix + "Unable to verify certificate " + LicenseVerification.licenseKey() + ". Intave servers down?");
    } else if(LicenseVerification.network().equals("~bypass")){
      player.sendMessage(prefix + "This self-issued version does not require certification");
    } else {
      player.sendMessage(prefix + "Certified for " + LicenseVerification.network() + " / " + LicenseVerification.licenseKey());
    }
  }

  public static IntaveCommandStage singletonInstance() {
    if(singletonInstance == null) {
      singletonInstance = new IntaveCommandStage();
    }
    return singletonInstance;
  }
}
