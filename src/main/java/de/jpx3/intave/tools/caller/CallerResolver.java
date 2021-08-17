package de.jpx3.intave.tools.caller;

import com.google.common.collect.Maps;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
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

  private final static String BUKKIT_CLASSLOADER_LOCATION = "org.bukkit.plugin.java.PluginClassLoader";
  private final static String NO_PLUGIN_FOUND = "null";
  private static Field classLoaderClassPluginField;

  private static String loadPluginFrom(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      ClassLoader classLoader = clazz.getClassLoader();
      if (classLoader == null) {
        return NO_PLUGIN_FOUND;
      }
      Class<? extends ClassLoader> classLoaderClass = classLoader.getClass();
      if (classLoaderClass != null && classLoader.getClass().getName().equalsIgnoreCase(BUKKIT_CLASSLOADER_LOCATION)) {
        if (classLoaderClassPluginField == null) {
          classLoaderClassPluginField = classLoader.getClass().getDeclaredField("plugin");
          classLoaderClassPluginField.setAccessible(true);
        }
        JavaPlugin plugin = (JavaPlugin) classLoaderClassPluginField.get(classLoader);
        return plugin.getName();
      }
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    return NO_PLUGIN_FOUND;
  }
}
