package de.jpx3.intave.reflect;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.reflect.locate.Locator;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;

public final class Lookup {
  final static String NMS_PACKAGE_NAME = Bukkit.getServer().getClass().getPackage().getName().substring(23);
  private final static String CRAFT_BUKKIT_PREFIX = "org.bukkit.craftbukkit." + NMS_PACKAGE_NAME;

  public static Field serverField(String serverClassName, String fieldName) {
    return Locator.fieldByKey(serverClassName, fieldName);
  }

  public static Class<?> serverClass(String key) {
    return Locator.classByKey(key);
  }

  public static Class<?> craftBukkitClass(String className) {
    return classByName(appendCraftBukkitPrefixToClass(className));
  }

  private static <T> Class<T> classByName(String className) {
    try {
      //noinspection unchecked
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static String appendCraftBukkitPrefixToClass(String className) {
    return CRAFT_BUKKIT_PREFIX + "." + className;
  }

  public static Field declaredFieldIn(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new IntaveInternalException(e);
    }
  }

  public static String version() {
    return NMS_PACKAGE_NAME;
  }
}
