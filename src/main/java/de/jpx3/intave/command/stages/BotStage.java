package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BotStage extends CommandStage {
  private static BotStage singletonInstance;
  private final IntavePlugin plugin;

  private BotStage() {
    super(InternalsStage.singletonInstance(), "bot");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "spawn",
    usage = "<player> <type>",
    permission = "intave.command.internals.bot.spawn",
    description = "Summon bots to a specified player"
  )
  public void spawn(CommandSender commandSender, Player targetPlayer, Type type) {
    User target = UserRepository.userOf(targetPlayer);
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      commandSender.sendMessage(IntavePlugin.prefix() + "Bots are currently unavailable for your server version. Please wait for upcoming updates.");
      return;
    }
    if (target.meta().attack().activeMiningStrategy != null) {
      commandSender.sendMessage(IntavePlugin.prefix() + "This player already has a bot assigned");
      return;
    }
    switch (type) {
      case INVISIBLE:
        MiningStrategy.EMULATION_LIGHT.apply(target);
        break;
      case MODERATE:
        MiningStrategy.EMULATION_MODERATE.apply(target);
        break;
      case HEAVY:
        MiningStrategy.EMULATION_HEAVY.apply(target);
        break;
    }
    commandSender.sendMessage(IntavePlugin.prefix() + "Summoned bot to " + ChatColor.RED + target.player().getName());
  }

  @KeepEnumInternalNames
  private enum Type {
    INVISIBLE,
    MODERATE,
    HEAVY
  }

  public static BotStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new BotStage();
    }
    return singletonInstance;
  }
}