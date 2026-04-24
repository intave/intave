package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Forward;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.LongTermViolationStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class InternalsStage extends CommandStage {
  private static InternalsStage singletonInstance;
  private final IntavePlugin plugin;

  private InternalsStage() {
    super(BaseStage.singletonInstance(), "internals");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "sendnotify",
    usage = "<message...>",
    permission = "intave.command.internals.sendnotify",
    description = "Send notifications"
  )
  public void internalCommand(CommandSender commandSender, String[] message) {
    String fullMessage = Arrays.stream(message).map(s -> s + " ").collect(Collectors.joining()).trim();
    Modules.violationProcessor().broadcastNotify(fullMessage);
  }

  @SubCommand(
    selectors = "bot",
    usage = "<player> <type>",
    description = "Bot related commands",
    permission = "intave.command.internals.bot"
  )
  @Forward(
    target = BotStage.class
  )
  public void botCommand(CommandSender commandSender) {
  }

//  @SubCommand(
//    selectors = "entitylag",
//    usage = "<player>",
//    permission = "intave.command.internals.entitylag",
//    description = "Causes severe lag to a user"
//  )
//  public void lagPlayer(CommandSender commandSender, Player target) {
//    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Command no longer available.");
//    int[] task = new int[]{0};
//    task[0] = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
//      if (!target.isOnline()) {
//        Bukkit.getScheduler().cancelTask(task[0]);
//        TaskTracker.stopped(task[0]);
//        return;
//      }
//      Synchronizer.synchronize(() -> {
//        for (int i = 0; i < 1000; i++) {
//          sendPacket(target);
//        }
//        target.closeInventory();
//      });
//    }, 20 * 2, 20);
//    TaskTracker.begun(task[0]);
//
//    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + target.getName() + " " + IntavePlugin.defaultColor() + "will now slowly begin to lag");
//  }

  @SubCommand(
    selectors = "storelog",
    usage = "<player> <check> <vl>",
    permission = "intave.command.internals.storelog",
    description = "Store the log of a player"
  )
  public void storeLog(CommandSender commandSender, Player target, String checkName, Double violationLevel) {
    User user = UserRepository.userOf(target);
    LongTermViolationStorage violationStorage = user.storageOf(LongTermViolationStorage.class);
    violationStorage.noteViolation(checkName, violationLevel.intValue());
    if (LongTermViolationStorage.USE_AUTO_STORAGE) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Auto storage is enabled. This command will not work.");
      return;
    }
    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED +"Added history entry for "+ target.getName() + " " + IntavePlugin.defaultColor() + "reaching " + ChatColor.RED + violationLevel.intValue() + " VL " + IntavePlugin.defaultColor() + "on " + ChatColor.RED + checkName);
  }

  @SubCommand(
    selectors = {"collectivekick", "kickip"},
    usage = "<player> <message>",
    permission = "intave.command.internals.collectivekick",
    description = "Kicks all players with the same ip as the target player"
  )
  public void collectiveKick(CommandSender commandSender, Player target, String[] messageParts) {
    String message = Arrays.stream(messageParts).map(s -> s + " ").collect(Collectors.joining()).trim();
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.equals(target) && onlinePlayer.getAddress().getAddress().equals(target.getAddress().getAddress())) {
        String parsedMessage = ChatColor.translateAlternateColorCodes('&', message);
        Synchronizer.synchronize(() -> onlinePlayer.kickPlayer(parsedMessage));
      }
    }
  }

  public static InternalsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new InternalsStage();
    }
    return singletonInstance;
  }
}
