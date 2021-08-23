package de.jpx3.intave.reflect.caller;

import com.google.common.collect.Maps;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class CallerResolver {
  private CallerResolver() {
    throw new SecurityException("Can not instantiate utility class");
  }

  @Deprecated
  public static PluginInvocation callerPluginInfo() {
    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
    int i = 0;
    for (StackTraceElement element : stacktrace) {
      String callerClassName = element.getClassName();
      String callerMethodName = element.getMethodName();
      String pluginName = pluginFromClass(callerClassName);
      if (!pluginName.equalsIgnoreCase(NO_PLUGIN_FOUND)
        && i++ > 1
      ) {
        return new PluginInvocation(
          pluginName,
          callerClassName,
          callerMethodName
        );
      }
    }
    return null;
  }

  private final static Map<String, String> classToPluginNameMap = Maps.newHashMap();

  private static String pluginFromClass(String className) {
    return classToPluginNameMap.computeIfAbsent(className, CallerResolver::loadPluginFrom);
  }

  private final static String NO_PLUGIN_FOUND = "null";

  private static String loadPluginFrom(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      JavaPlugin plugin = JavaPlugin.getProvidingPlugin(clazz);
      return plugin.getName();
    } catch (ClassNotFoundException | IllegalArgumentException | IllegalStateException exception) {
      return NO_PLUGIN_FOUND;
    }
  }
}
