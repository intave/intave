package de.jpx3.intave.command;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public abstract class CommandStage {
  private final static Map<Class<? extends CommandStage>, CommandStage> globalInstances = new HashMap<>();
  private final CommandStage parent;
  private final String name;
  private final List<CommandExecutor> subCommandList = new ArrayList<>();

  protected CommandStage(CommandStage parent, String name) {
    this.parent = parent;
    this.name = name;
    load();
    globalInstances.put(this.getClass(), this);
  }

  private void load() {
    Arrays.stream(getClass().getMethods()).forEach(this::processMethod);
    orderSubCommands();
  }

  public void processMethod(Method method) {
    if (method.getDeclaredAnnotation(SubCommand.class) != null) {
      subCommandList.add(new CommandExecutor(this, method));
    }
  }

  public void orderSubCommands() {
    subCommandList.sort(Comparator.comparing(commandExecutor -> commandExecutor.selectors()[0]));
  }

  private final static String NO_PERMISSION_MESSAGE = ChatColor.RED + "I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.";

  @Native
  public void execute(CommandSender sender, String currentCommand) {
    if (currentCommand.isEmpty()) {
      showAllCommands(sender);
      return;
    }
    String[] command = currentCommand.split(" ", 2);
    LevenshteinPicker.SearchResult nearestLink = levenshteinSubCommandPick(sender, command[0]);
    LevenshteinPicker.Result result = nearestLink.result();

    if (result == LevenshteinPicker.Result.NONE) {
      showAllCommands(sender);
      return;
    }

    if (result == LevenshteinPicker.Result.INCONCLUSIVE) {
      showInconclusiveCommandSelection(sender, nearestLink.matches().stream().map(s -> subcommandBySelector(sender, s)).collect(Collectors.toList()));
      return;
    }

    CommandExecutor link = subcommandBySelector(sender, nearestLink.matches().get(0));
    if (link == null) {
      showAllCommands(sender);
      return;
    }

    String leftCommand = command.length > 1 ? command[1] : "";
    if (link.forwardClass() != null) {
      String permission = link.permission();
      if (permission.equalsIgnoreCase("sibyl")) {
        if (sender instanceof Player) {
          if (!IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender)) {
            showAllCommands(sender);
            return;
          }
        } else {
          showAllCommands(sender);
          return;
        }
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        sender.sendMessage(NO_PERMISSION_MESSAGE);
        return;
      }
      CommandStage commandStage = globalInstances.get(link.forwardClass());
      commandStage.execute(sender, leftCommand);
    } else {
      link.execute(sender, leftCommand);
    }
  }

  @Native
  public List<String> tabComplete(CommandSender sender, String currentCommand) {
    if (currentCommand.isEmpty()) {
      return subcommandCompletions(sender);
    }
    String[] command = currentCommand.split(" ", 2);
    CommandExecutor link = findLink(command[0]);//searchLink(command[0]);
    if (link == null) {
      return subcommandCompletions(sender);
    }
    String leftCommand = command.length > 1 ? command[1] : "";
    if (link.forwardClass() != null) {
      String permission = link.permission();
      if (permission.equals("sibyl") && !(sender instanceof Player && IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender))) {
        return null;
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        return null;
      }
      CommandStage commandStage = globalInstances.get(link.forwardClass());
      return commandStage.tabComplete(sender, leftCommand);
    } else {
      return link.tabComplete(sender, leftCommand);
    }
  }

  @Native
  private List<String> subcommandCompletions(CommandSender player) {
    return subCommandList.stream()
      .filter(subCommand -> BukkitPermissionCheck.permissionCheck(player, subCommand.permission()))
      .filter(subCommand -> !subCommand.hideInHelp())
      .map(subCommand -> subCommand.selectors()[0])
      .collect(Collectors.toList());
  }

  private final static int COMMAND_SHOW_LIMIT = 8;

  @Native
  protected void showAllCommands(CommandSender sender) {
    List<String> messages = new ArrayList<>();
    for (CommandExecutor commandExecutor : subCommandList) {
      if (commandExecutor.hideInHelp()) {
        continue;
      }
      String permission = commandExecutor.permission();
      if (permission.equals("sibyl") && !(sender instanceof Player && IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender))) {
        continue;
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        continue;
      }
      messages.add(commandExecutor.selectors()[0] + ": " + commandExecutor.description());
    }

    if (messages.isEmpty()) {
      sender.sendMessage(IntavePlugin.prefix() + "No subcommands available");
      return;
    }

    sender.sendMessage(IntavePlugin.prefix() + "Available subcommands:");

    if (messages.size() < COMMAND_SHOW_LIMIT) {
      for (String message : messages) {
        sender.sendMessage(IntavePlugin.prefix() + commandPath() + message);
      }
    } else {
      List<String> reducedMessages = messages.subList(0, COMMAND_SHOW_LIMIT);
      for (String message : reducedMessages) {
        sender.sendMessage(IntavePlugin.prefix() + commandPath() + message);
      }
      int remaining = messages.size() - COMMAND_SHOW_LIMIT;
      sender.sendMessage(IntavePlugin.prefix() + "<Use tab completion to see " + nameOf(remaining) + " more>");
    }
  }

  private static final String[] LITERALS = {
    "zero", "one", "two", "three", "four", "five",
    "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve"
  };

  private String nameOf(int number) {
    return number > 12 || number < 0 ? String.valueOf(number) : LITERALS[number];
  }

  @Native
  protected void showInconclusiveCommandSelection(CommandSender sender, List<CommandExecutor> subCommandList) {
    List<String> availableSelectors = new ArrayList<>();
    for (CommandExecutor commandExecutor : subCommandList) {
      if (commandExecutor.hideInHelp()) {
        continue;
      }
      String permission = commandExecutor.permission();
      if (permission.equals("sibyl") && !(sender instanceof Player && IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender))) {
        continue;
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        continue;
      }
      availableSelectors.add(commandExecutor.selectors()[0]);
    }
    if (availableSelectors.isEmpty()) {
      showAllCommands(sender);
    } else {
      sender.sendMessage(IntavePlugin.prefix() + "Did you mean " + describeListSelection(availableSelectors) + "?");
    }
  }

  private static String describeListSelection(List<String> elements) {
    int size = elements.size();
    if(size == 0) {
      return "";
    } else if (size == 1) {
      return elements.get(0);
    } else {
      String elementsListed = String.join(", ", elements.subList(0, size - 1));
      String lastElement = elements.get(size - 1);
      return elementsListed + " or " + lastElement;
    }
  }

  private CommandExecutor findLink(String commandName) {
    String finalCommandName = commandName.toLowerCase(Locale.ROOT);
    return subCommandList.stream()
      .filter(commandExecutor -> Arrays.asList(commandExecutor.selectors()).contains(finalCommandName))
      .findFirst().orElse(null);
  }

  @Native
  private LevenshteinPicker.SearchResult levenshteinSubCommandPick(CommandSender sender, String search) {
    List<String> haystacks = new ArrayList<>();
    for (CommandExecutor commandExecutor : subCommandList) {
      String permission = commandExecutor.permission();
      if (permission.equals("sibyl") && !(sender instanceof Player && IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender))) {
        continue;
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        continue;
      }
      Collections.addAll(haystacks, commandExecutor.selectors());
    }
    return LevenshteinPicker.search(haystacks, search);
  }

  @Native
  private CommandExecutor subcommandBySelector(CommandSender sender, String search) {
    for (CommandExecutor subCommand : subCommandList) {
      String permission = subCommand.permission();
      if (sender instanceof Player && permission.equals("sibyl") && !IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated((Player) sender)) {
        continue;
      } else if (sender instanceof Player && !permission.equals("none") && !permission.equals("sibyl") && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
        continue;
      }

      for (String selector : subCommand.selectors()) {
        if(selector.equals(search)) {
          return subCommand;
        }
      }
    }
    return null;
  }

  private String compiledCommandPath = null;

  protected String commandPath() {
    if (compiledCommandPath == null) {
      List<String> commandPath = new ArrayList<>();
      CommandStage currentStage = this;
      do {
        commandPath.add(currentStage.name());
      } while ((currentStage = currentStage.parent()) != null);
      Collections.reverse(commandPath);
      compiledCommandPath = commandPath.stream().map(s -> s + " ").collect(Collectors.joining());
    }
    return compiledCommandPath;
  }

  public CommandStage parent() {
    return parent;
  }

  public String name() {
    return name;
  }
}
