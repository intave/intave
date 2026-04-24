package de.jpx3.intave.klass.trace;

import com.google.common.collect.Maps;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

@Deprecated
public final class Caller {
  private Caller() {
    throw new SecurityException("Can not instantiate utility class");
  }

  @Deprecated
  public static PluginInvocation pluginInfo(boolean onlyExternal) {
    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
    int i = 0;
    for (StackTraceElement element : stacktrace) {
      String callerClassName = element.getClassName();
      String callerMethodName = element.getMethodName();
      String pluginName = pluginFromClass(callerClassName);
      if (!pluginName.equalsIgnoreCase(NO_PLUGIN_FOUND)
        && i++ > 1
      ) {
        if (onlyExternal && ("Intave".equalsIgnoreCase(pluginName) || "packetevents".equalsIgnoreCase(pluginName))) {
          continue;
        }
        return new PluginInvocation(
          pluginName,
          callerClassName,
          callerMethodName
        );
      }
    }
    return null;
  }

  private static final Map<String, String> classToPluginNameMap = Maps.newHashMap();

  private static String pluginFromClass(String className) {
    return classToPluginNameMap.computeIfAbsent(className, Caller::loadPluginFrom);
  }

  private static final String NO_PLUGIN_FOUND = "null";

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
