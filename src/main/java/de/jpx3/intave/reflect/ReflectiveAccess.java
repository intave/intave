package de.jpx3.intave.reflect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.hitbox.ReflectiveEntityHitBoxAccess;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;

public final class ReflectiveAccess {
  private final static String NMS_PACKAGE_NAME = Bukkit.getServer().getClass().getPackage().getName().substring(23);
  private final static String NMS_PREFIX = "net.minecraft.server." + NMS_PACKAGE_NAME;
  private final static String CRAFT_BUKKIT_PREFIX = "org.bukkit.craftbukkit." + NMS_PACKAGE_NAME;

  public final static Class<?> NMS_WORLD_SERVER_CLASS = lookupServerClass("WorldServer");
  public final static Class<?> NMS_ENTITY_CLASS = lookupServerClass("Entity");
  public final static Class<?> NMS_CRAFT_WORLD_CLASS = lookupCraftBukkitClass("CraftWorld");
  public final static Class<?> NMS_AABB_CLASS = lookupServerClass("AxisAlignedBB");

  public static void setup() {
    ReflectiveBlockAccess.setup();
    ReflectiveEntityHitBoxAccess.setup();
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveDataWatcherAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveHandleAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveEntityAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.reflect.ReflectiveScoreboardAccess");
  }

  public static <T> Class<T> classByName(String className) {
    try {
      //noinspection unchecked
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IntaveInternalException(e);
    }
  }

  public static Field ensureAccessible(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
    return field;
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
}