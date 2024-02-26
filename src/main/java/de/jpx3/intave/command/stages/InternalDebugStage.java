package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.message.*;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class InternalDebugStage extends CommandStage {
  private static InternalDebugStage singletonInstance;

  private InternalDebugStage() {
    super(RootStage.singletonInstance(), "debug");
  }

  @SubCommand(
    selectors = "enable",
    description = "Enable debug mode",
    permission = "sibyl"
  )
  public void enableAll(User user, @Optional MessageCategory category) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());

    if (category != null) {
      outputConfiguration.activateCategory(category);
      user.player().sendMessage(ChatColor.GREEN + "Debug mode for " + category.description().toLowerCase(Locale.ROOT) + " activated");
    } else {
      outputConfiguration.activateAllCategories();
      user.player().sendMessage(ChatColor.GREEN + "All debug modes enabled.");
    }
  }

  @SubCommand(
    selectors = "disable",
    description = "Disable debug mode",
    permission = "sibyl"
  )
  public void disableAll(User user, @Optional MessageCategory category) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());

    if (category != null) {
      outputConfiguration.deactivateCategory(category);
      user.player().sendMessage(ChatColor.GREEN + "Debug mode for " + category.description().toLowerCase(Locale.ROOT) + " deactivated");
    } else {
      outputConfiguration.deactivateAllCategories();
      user.player().sendMessage(ChatColor.GREEN + "All debug modes disabled.");
    }
  }

  @SubCommand(
    selectors = "color",
    description = "Set color",
    permission = "sibyl"
  )
  public void setColor(User user, MessageCategory category, ChatColor color) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.setColor(category, color);
    user.player().sendMessage(ChatColor.GREEN + "Color for " + category.description().toLowerCase(Locale.ROOT) + " set to " + color.name());
  }

  @SubCommand(
    selectors = "severity",
    description = "Set required severity",
    permission = "sibyl"
  )
  public void setMinimumSeverity(User user, MessageSeverity severity) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.setMinimumSeverity(severity);
    user.player().sendMessage(ChatColor.GREEN + "Minimum severity set to " + severity.name());
  }

  @SubCommand(
    selectors = "detail",
    description = "Set message",
    permission = "sibyl"
  )
  public void setOutputDetail(User user, MessageDetail detail, @Optional MessageCategory category) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    if (category != null) {
      outputConfiguration.setMessageDetail(category, detail);
      user.player().sendMessage(ChatColor.GREEN + "Detail for " + category.description().toLowerCase(Locale.ROOT) + " set to " + detail.name());
    } else {
      outputConfiguration.setDefaultMessageDetail(detail);
      user.player().sendMessage(ChatColor.GREEN + "Detail set to " + detail.name());
    }
  }

  @SubCommand(
    selectors = "prefix",
    description = "Set formatter",
    permission = "sibyl"
  )
  public void setFormatter(User user, PrefixDetail detail) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.setDefaultPrefixDetail(detail);
    user.player().sendMessage(ChatColor.GREEN + "Prefix set to " + detail.name());
  }

  @SubCommand(
    selectors = "settarget",
    description = "Set target",
    permission = "sibyl"
  )
  public void setTarget(User user, MessageCategory cat, @Optional Player[] targets) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.addConstraint(cat, player -> {
      if (targets == null) {
        return true;
      }
      for (Player target : targets) {
        if (player.equals(target)) {
          return true;
        }
      }
      return false;
    });
    user.player().sendMessage(ChatColor.GREEN + "Target set for " + cat.description().toLowerCase(Locale.ROOT));
  }

  @SubCommand(
    selectors = "selftarget",
    description = "Set self target",
    permission = "sibyl"
  )
  public void setSelfTarget(User user, MessageCategory cat) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.addConstraint(cat, player -> player.equals(user.player()));
    user.player().sendMessage(ChatColor.GREEN + "Self target set for " + cat.description().toLowerCase(Locale.ROOT));
  }

  @SubCommand(
    selectors = "remtarget",
    description = "Remove target",
    permission = "sibyl"
  )
  public void removeTarget(User user, MessageCategory cat) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    outputConfiguration.removeConstraint(cat);
    user.player().sendMessage(ChatColor.GREEN + "Target removed for " + cat.description().toLowerCase(Locale.ROOT));
  }

  @SubCommand(
    selectors = "status",
    description = "Remove self target",
    permission = "sibyl"
  )
  public void status(User user) {
    OutputConfiguration outputConfiguration = DebugBroadcast.configurationOf(user.id());
    Player player = user.player();
    player.sendMessage(IntavePlugin.prefix() + "Debug mode status");
    String prefixSelectorName = outputConfiguration.prefixSelector().name().toLowerCase(Locale.ROOT).replace("_", " ");
    player.sendMessage(IntavePlugin.prefix() + ChatColor.GRAY + "Prefix is " + prefixSelectorName + ". Example: " + outputConfiguration.prefixSelector().formatPrefix(MessageSeverity.MEDIUM, "NAME") + "");

    for (MessageCategory category : MessageCategory.values()) {
      ChatColor color = outputConfiguration.colorOf(category);
      String active = outputConfiguration.isActive(category) ? ChatColor.GREEN + "enabled" + ChatColor.GRAY : ChatColor.RED + "disabled" + ChatColor.GRAY;
      String format = outputConfiguration.detailOf(category).name().toLowerCase(Locale.ROOT);
      String description = category.description().toLowerCase(Locale.ROOT);
      player.sendMessage(color + " " + category.name() + ChatColor.GRAY + " (" + color + description + ChatColor.GRAY + ")" + " " + active + " in " + format + " format");
    }
  }

  @SubCommand(
    selectors = "jump",
    description = "Cause a physics false flag, resulting in a jump",
    permission = "sibyl"
  )
  public void falseFlag(User user) {
    user.meta().movement().baseMotionY = 2;
  }

  public static InternalDebugStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new InternalDebugStage();
    }
    return singletonInstance;
  }
}
