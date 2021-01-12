package de.jpx3.intave.command;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.permission.PermissionCheck;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public abstract class CommandStage {
  private final static Map<Class<? extends CommandStage>, CommandStage> globalInstances = new HashMap<>();
  private final CommandStage parent;
  private final String name;
  private final int height;
  private final List<IntaveSubCommand> subCommandList = new ArrayList<>();

  protected CommandStage(CommandStage parent, String name, int height) {
    this.parent = parent;
    this.name = name;
    this.height = height;
    load();
    globalInstances.put(this.getClass(), this);
  }

  private void load() {
    Arrays.stream(getClass().getMethods()).forEach(this::processMethod);
  }

  public void processMethod(Method method) {
//    Bukkit.broadcastMessage("Processing " + method + " " + method.getDeclaredAnnotation(SubCommand.class));
    if(method.getDeclaredAnnotation(SubCommand.class) != null) {
      subCommandList.add(new IntaveSubCommand(this, method));
    }
  }

  public void execute(CommandSender sender, String currentCommand) {
    if(currentCommand.isEmpty()) {
      showInfo(sender);
      return;
    }

    String[] command = currentCommand.split(" ", 2);
    IntaveSubCommand link = tryFindNearestLink(command[0]);

    if(link == null) {
      showInfo(sender);
      return;
    }

    String leftCommand = command.length > 1 ? command[1] : "";

    if(link.forwardClass() != null) {
      CommandStage commandStage = globalInstances.get(link.forwardClass());
      commandStage.execute(sender, leftCommand);
    } else {
      link.execute(sender, leftCommand);
    }
  }

  public List<String> tabComplete(CommandSender sender, String currentCommand) {
    if(currentCommand.isEmpty()) {
      return subcommandCompletions(sender);
    }

    String[] command = currentCommand.split(" ", 2);
    IntaveSubCommand link = tryFindNearestLink(command[0]);//searchLink(command[0]);

    if(link == null) {
      return subcommandCompletions(sender);
    }

    String leftCommand = command.length > 1 ? command[1] : "";
    if(link.forwardClass() != null) {
      CommandStage commandStage = globalInstances.get(link.forwardClass());
      return commandStage.tabComplete(sender, leftCommand);
    } else {
      return link.tabComplete(sender, leftCommand);
    }
  }

  private List<String> subcommandCompletions(CommandSender player) {
    return subCommandList.stream()
      .filter(subCommand -> PermissionCheck.permissionCheck(player, subCommand.permission()))
      .map(intaveSubCommand -> intaveSubCommand.selectors()[0]).collect(Collectors.toList());
  }

  protected void showInfo(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Available subcommands:");
    StringBuilder commandPath = new StringBuilder();
    CommandStage currentStage = this;
    do {
      commandPath.append(currentStage.name()).append(" ");
    } while ((currentStage = currentStage.parent()) != null);

    for (IntaveSubCommand intaveSubCommand : subCommandList) {
      sender.sendMessage(IntavePlugin.prefix() + commandPath + intaveSubCommand.selectors()[0] + ": " + intaveSubCommand.description());
    }
  }

  private IntaveSubCommand searchLink(String commandName) {
    String finalCommandName = commandName.toLowerCase(Locale.ROOT);
    return subCommandList.stream()
      .filter(intaveSubCommand -> Arrays.asList(intaveSubCommand.selectors()).contains(finalCommandName))
      .findFirst().orElse(null);
  }

  private IntaveSubCommand tryFindNearestLink(String commandName) {
    return subCommandList.stream()
      .max(Comparator.comparingDouble(o -> bestEqualityIn(o.selectors(), commandName)))
      .filter(intaveSubCommand -> bestEqualityIn(intaveSubCommand.selectors(), commandName) > 0.5)
      .orElse(null);
  }

  private double bestEqualityIn(String[] rootStrings, String needleString) {
    double bestRatio = 0;
    for (String rootString : rootStrings) {
      bestRatio = Math.max(bestRatio, equality(rootString, needleString));
    }
    return bestRatio;
  }

  private double equality(String rootString, String needleString) {
    int succeeded = 0;
    char[] rootChars = rootString.toCharArray();
    char[] needleChars = needleString.toCharArray();
    int minLength = Math.min(rootChars.length, needleChars.length);
    for (int i = 0; i < minLength; i++) {
      if (rootChars[i] == needleChars[i]) {
        succeeded++;
      }
    }
    return succeeded / (double) Math.max(rootChars.length, needleChars.length);
  }

  public CommandStage parent() {
    return parent;
  }

  public String name() {
    return name;
  }

  public int height() {
    return height;
  }
}
