package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMessageChannel;
import org.bukkit.ChatColor;
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
    description = "Toggles your message-stream for verbose messages",
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
    description = "Toggle your message-stream for notifications",
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

  public static IntaveCommandStage singletonInstance() {
    if(singletonInstance == null) {
      singletonInstance = new IntaveCommandStage();
    }
    return singletonInstance;
  }
}
