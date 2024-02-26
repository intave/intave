package de.jpx3.intave.command;

import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.command.stages.*;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@HighOrderService
public final class CommandForwarder implements CommandExecutor, TabCompleter {
  private final CommandStage rootCommandStage = BaseStage.singletonInstance();

  static {
    BaseStage.singletonInstance();
    InternalsStage.singletonInstance();
    BotStage.singletonInstance();
    RootStage.singletonInstance();
    CloudStage.singletonInstance();
    DiagnosticsStage.singletonInstance();
    ProxyStage.singletonInstance();
    InternalDebugStage.singletonInstance();
    PerformanceStage.singletonInstance();
    SampleStage.singletonInstance();
  }

  @Override
  public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
    if (commandSender instanceof CommandBlock) {
      return false;
    }
    String rawCommand = Arrays.stream(strings).map(s1 -> s1 + " ").collect(Collectors.joining()).trim();
    rootCommandStage.execute(commandSender, rawCommand);
    return false;
  }

  @Override
  public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] arguments) {
    if (arguments.length == 0) {
      return Collections.emptyList();
    }
    String rawCommand = Arrays.stream(arguments).map(s1 -> s1 + " ").collect(Collectors.joining()).trim();
    List<String> tabCompletions = rootCommandStage.tabComplete(commandSender, rawCommand);
    if (tabCompletions == null) {
      return null;
    }
    List<String> completions = new ArrayList<>();
    StringUtil.copyPartialMatches(arguments[arguments.length - 1], tabCompletions, completions);
    return completions;
  }
}
