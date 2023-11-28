package de.jpx3.intave.command;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandExecutor {
  private final CommandStage stage;
  private String[] selectors;
  private String usage, description, permission;
  private boolean hideInHelp;

  private final Method targetMethod;

  private Class<?>[] requiredTypes;
  private Class<?>[] allTypes;

  private Class<?> forwardClass;

  private boolean requiresUserParameter;
  private boolean requiresCommandSenderParameter;

  public CommandExecutor(
    CommandStage stage,
    Method targetMethod
  ) {
    this.stage = stage;
    this.targetMethod = targetMethod;
    this.compile();
  }

  public void compile() {
    SubCommand subCommand = targetMethod.getDeclaredAnnotation(SubCommand.class);
    selectors = subCommand.selectors();
    usage = subCommand.usage();
    description = subCommand.description();
    permission = subCommand.permission();
    hideInHelp = subCommand.hideInHelp();
    Forward forward = targetMethod.getDeclaredAnnotation(Forward.class);
    if (forward != null) {
      forwardClass = forward.target();
    }
    Annotation[][] parameterAnnotations = targetMethod.getParameterAnnotations();
    List<Class<?>> requiredTypes = new ArrayList<>();
    List<Class<?>> allTypes = new ArrayList<>();
    int i = 0;
    boolean optionalBefore = false;
    for (Class<?> parameterType : targetMethod.getParameterTypes()) {
      if (i == 0) {
        requiresUserParameter = parameterType == User.class;
        requiresCommandSenderParameter = parameterType == CommandSender.class;
        if (!requiresUserParameter && !requiresCommandSenderParameter) {
          throw new IllegalStateException();
        }
        i++;
        continue;
      }
      Annotation[] parameterAnnotation = parameterAnnotations[i];
      boolean isOptional = Arrays.stream(parameterAnnotation).anyMatch(annotation -> annotation.annotationType() == Optional.class);
      if (!isOptional && optionalBefore) {
        throw new IntaveInternalException("Required parameter after optional parameter");
      }
      optionalBefore = isOptional;
      if (!optionalBefore) {
        requiredTypes.add(parameterType);
      }
      allTypes.add(parameterType);
      i++;
    }
    this.requiredTypes = requiredTypes.toArray(new Class<?>[0]);
    this.allTypes = allTypes.toArray(new Class<?>[0]);
  }

  private static final String NO_PERMISSION_MESSAGE = ChatColor.RED + "I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.";

  @Native
  public CommandStage execute(CommandSender sender, String executedCommand) {
    String prefix = IntavePlugin.prefix();
    String[] args = executedCommand.split(" ");
    if ("sibyl".equalsIgnoreCase(permission)) {
      if (sender instanceof Player) {
        if (!IntavePlugin.singletonInstance().sibyl().isAuthenticated(((Player) sender).getPlayer())) {
          sender.sendMessage(NO_PERMISSION_MESSAGE);
          return null;
        }
      } else {
        sender.sendMessage(NO_PERMISSION_MESSAGE);
        return null;
      }
    } else if (sender instanceof Player && !"none".equals(permission) && !"sibyl".equalsIgnoreCase(permission) && !BukkitPermissionCheck.permissionCheck(sender, permission)) {
      sender.sendMessage(NO_PERMISSION_MESSAGE);
      return null;
    }
    if (args.length == 1 && args[0].isEmpty()) {
      args = new String[0];
    }
    if (args.length < requiredTypes.length) {
      List<String> commandPath = new ArrayList<>();
      CommandStage currentStage = stage;
      do {
        commandPath.add(currentStage.name());
      } while ((currentStage = currentStage.parent()) != null);
      Collections.reverse(commandPath);
      commandPath.add(selectors[0]);
      String commandPathAsString = commandPath.stream().map(s -> s + " ").collect(Collectors.joining());
      sender.sendMessage(prefix + "Usage: " + commandPathAsString + usage);
      return null;
    }
    List<Object> parameterTypes = new ArrayList<>();
    if (requiresCommandSenderParameter) {
      parameterTypes.add(sender);
    } else if (requiresUserParameter) {
      if (sender instanceof Player) {
        parameterTypes.add(UserRepository.userOf((Player) sender));
      } else {
        sender.sendMessage(prefix + ChatColor.RED + "This action requires you to be a player");
        return null;
      }
    }
    int i = 0;
    for (String arg : args) {
      if (allTypes.length <= i) {
        continue;
      }
      Class<?> expectedType = allTypes[i];
      StringBuilder followingCommand = new StringBuilder();
      for (int j = i; j < args.length; j++) {
        followingCommand.append(args[j]).append(" ");
      }
      Object output = TypeTranslators.tryTranslate(sender, expectedType, arg, followingCommand.toString());
      if (output == null) {
        return null;
      }
      if (output instanceof String && expectedType != String.class) {
        sender.sendMessage(prefix + ChatColor.RED + output);
        return null;
      }
      parameterTypes.add(output);
      i++;
    }
    while (parameterTypes.size() - 1 < allTypes.length) {
      parameterTypes.add(null);
    }
    try {
      Object output = targetMethod.invoke(stage, parameterTypes.toArray(new Object[0]));
      return output instanceof CommandStage ? (CommandStage) output : null;
    } catch (IllegalAccessException | InvocationTargetException exception) {
      exception.printStackTrace();
      return null;
    }
  }

  public List<String> tabComplete(CommandSender commandSender, String executedCommand) {
    String[] args = executedCommand.split(" ");
    if (!"none".equals(permission) && !BukkitPermissionCheck.permissionCheck(commandSender, permission)) {
      return null;
    }
    if (args.length == 1 && args[0].isEmpty()) {
      args = new String[0];
    }
    if (args.length < allTypes.length) {
      Class<?> clazz = allTypes[args.length];
      return TypeTranslators.findTabCompletes(commandSender, clazz, args.length > 0 ? args[args.length - 1] : "", executedCommand);
    }
    return null;
  }

  public boolean hideInHelp() {
    return hideInHelp;
  }

  public String permission() {
    return permission;
  }

  public Class<?> forwardClass() {
    return forwardClass;
  }

  public String[] selectors() {
    return selectors;
  }

  public String description() {
    return description;
  }
}
