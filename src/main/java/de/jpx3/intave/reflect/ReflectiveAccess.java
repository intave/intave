package de.jpx3.intave.reflect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import org.bukkit.Bukkit;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

public final class ReflectiveAccess {
  private final static String NMS_PACKAGE_NAME = Bukkit.getServer().getClass().getPackage().getName().substring(23);
  private final static String NMS_PREFIX = "net.minecraft.server." + NMS_PACKAGE_NAME;
  private final static String CRAFT_BUKKIT_PREFIX = "org.bukkit.craftbukkit." + NMS_PACKAGE_NAME;

  public final static Class<?> NMS_WORLD_SERVER_CLASS = lookupServerClass("WorldServer");
  public final static Class<?> NMS_ENTITY_CLASS = lookupServerClass("Entity");
  public final static Class<?> NMS_AABB_CLASS = lookupServerClass("AxisAlignedBB");

  public static void setup() {
    ReflectiveBlockAccess.setup();
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveDataWatcherAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveHandleAccess");
  }

  public static <T> Class<T> classByName(String className) {
    try {
      //noinspection unchecked
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new ReflectionFailureException(e);
    }
  }

  public static String version() {
    return NMS_PACKAGE_NAME;
  }

  public static Class<?> lookupServerClass(String className) {
    return classByName(appendNMSPrefixToClass(className));
  }

  public static Class<?> lookupCraftBukkitClass(String className) {
    return classByName(appendCraftBukkitPrefixToClass(className));
  }

  public static String appendNMSPrefixToClass(String className) {
    return NMS_PREFIX + "." + className;
  }

  public static String appendCraftBukkitPrefixToClass(String className) {
    return CRAFT_BUKKIT_PREFIX + "." + className;
  }

  public static <C, R, I> R invokeField(Class<C> clazz, String fieldName, I obj) {
    try {
      Field field = clazz.getField(fieldName);
      ensureAccessible(field);
      Object invoke = field.get(obj);
      //noinspection unchecked
      return (R) invoke;
    } catch (Exception e) {
      throw new ReflectionFailureException(e);
    }
  }

  private static void ensureAccessible(AccessibleObject accessibleObject) {
    if (!accessibleObject.isAccessible()) {
      accessibleObject.setAccessible(true);
    }
  }
}