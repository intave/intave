package de.jpx3.intave.command;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.permission.PermissionCheck;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IntaveSubCommand {
  private final CommandStage stage;
  private String[] selectors;
  private String usage, description, permission;

  private final Method targetMethod;

  private Class<?>[] requiredTypes;
  private Class<?>[] allTypes;

  private Class<?> forwardClass;

  private boolean requiresUserParameter;
  private boolean requiresCommandSenderParameter;

  public IntaveSubCommand(
    CommandStage stage,
    Method targetMethod
  ) {
    this.stage = stage;
    this.targetMethod = targetMethod;
    this.load();
  }

  public void load() {
    SubCommand subCommand = targetMethod.getDeclaredAnnotation(SubCommand.class);
    selectors = subCommand.selectors();
    usage = subCommand.usage();
    description = subCommand.description();
    permission = subCommand.permission();

    Forward forward = targetMethod.getDeclaredAnnotation(Forward.class);

    if(forward != null) {
      forwardClass = forward.target();
//      return;
    }

    Annotation[][] parameterAnnotations = targetMethod.getParameterAnnotations();

    List<Class<?>> requiredTypes = new ArrayList<>();
    List<Class<?>> allTypes = new ArrayList<>();

    int i = 0;
    boolean optional = false;

    for (Class<?> parameterType : targetMethod.getParameterTypes()) {
      if(i == 0) {
        requiresUserParameter = parameterType == User.class;
        requiresCommandSenderParameter = parameterType == CommandSender.class;
        if(!requiresUserParameter && !requiresCommandSenderParameter) {
          throw new IllegalStateException();
        }
        i++;
        continue;
      }

      Annotation[] parameterAnnotation = parameterAnnotations[i];
      boolean newOptional = Arrays.stream(parameterAnnotation).anyMatch(annotation -> annotation.annotationType() == Optional.class);
      if(!newOptional && optional) {
        throw new IntaveInternalException();
      }
      optional = newOptional;
      if(!optional) {
        requiredTypes.add(parameterType);
      }
      allTypes.add(parameterType);
      i++;
    }

    this.requiredTypes = requiredTypes.toArray(new Class<?>[0]);
    this.allTypes = allTypes.toArray(new Class<?>[0]);
  }

  private final static String NO_PERMISSION_MESSAGE = ChatColor.RED + "I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.";

  public CommandStage execute(CommandSender commandSender, String executedCommand) {
    String prefix = IntavePlugin.prefix();
    String[] args = executedCommand.split(" ");

    if(!PermissionCheck.permissionCheck(commandSender, permission)) {
      commandSender.sendMessage("No permission " + PermissionCheck.permissionCheck(commandSender, permission));
      commandSender.sendMessage(NO_PERMISSION_MESSAGE);
      return null;
    }

    if(args.length == 1 && args[0].isEmpty()) {
      args = new String[0];
    }

    if(args.length < requiredTypes.length /*|| args.length > allTypes.length*/) {
      StringBuilder command = new StringBuilder();
      CommandStage currentStage = stage;
      do {
        command.append(currentStage.name()).append(" ");
      } while ((currentStage = currentStage.parent()) != null);
      command.append(selectors[0]);

      commandSender.sendMessage(prefix + "Usage: " + command + " " + usage);
      return null;
    }

    List<Object> parameterTypes = new ArrayList<>();
    if(requiresCommandSenderParameter) {
      parameterTypes.add(commandSender);
    } else if(requiresUserParameter) {
      if(commandSender instanceof Player) {
        parameterTypes.add(UserRepository.userOf((Player) commandSender));
      } else {
        commandSender.sendMessage(prefix + ChatColor.RED + "This action requires you to be a player");
        return null;
      }
    }

    int i = 0;
    for (String arg : args) {
      Class<?> expectedType = allTypes[i];
      Object output = TypeTranslators.tryTranslate(commandSender, expectedType, arg, executedCommand);
      if(output instanceof String) {
        commandSender.sendMessage(prefix + ChatColor.RED + "Invalid parameter \"" + arg + "\": " + output);
        return null;
      }
      if(output == null) {
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
    } catch (IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
      return null;
    }
  }

  public List<String> tabComplete(CommandSender commandSender, String executedCommand) {
    String prefix = IntavePlugin.prefix();
    String[] args = executedCommand.split(" ");

    if(!PermissionCheck.permissionCheck(commandSender, permission)) {
      commandSender.sendMessage("No permission " + PermissionCheck.permissionCheck(commandSender, permission));
      commandSender.sendMessage(NO_PERMISSION_MESSAGE);
      return null;
    }

    if(args.length == 1 && args[0].isEmpty()) {
      args = new String[0];
    }

    if(args.length < allTypes.length) {
      Class<?> clazz = allTypes[args.length];
      return TypeTranslators.findTabCompletes(commandSender, clazz, args.length > 0 ? args[args.length - 1] : "", executedCommand);
    }

    return null;
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
