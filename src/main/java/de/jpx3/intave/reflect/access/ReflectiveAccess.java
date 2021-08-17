package de.jpx3.intave.reflect.access;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.reflect.hitbox.ReflectiveEntityHitBoxAccess;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;

import java.lang.reflect.Field;

public final class ReflectiveAccess {
  final static boolean DATA_WATCHER_NEW_ACCESS_VER = MinecraftVersions.VER1_9_0.atOrAbove();
  private static final boolean ENTITY_SIZE_ACCESS = MinecraftVersions.VER1_14_0.atOrAbove();

  public final static String NMS_PREFIX = "net.minecraft.server." + Lookup.version();

  public final static Class<?> NMS_WORLD_SERVER_CLASS = Lookup.serverClass("WorldServer");
  public final static Class<?> NMS_ENTITY_CLASS = Lookup.serverClass("Entity");
  public final static Class<?> NMS_CRAFT_WORLD_CLASS = Lookup.craftBukkitClass("CraftWorld");
  public final static Class<?> NMS_AABB_CLASS = Lookup.serverClass("AxisAlignedBB");

  public static void setup() {
    ReflectiveBlockAccess.setup();
    ReflectiveEntityHitBoxAccess.setup();
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.access.ReflectiveHandleAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.access.ReflectiveEntityAccess");
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.access.ReflectiveScoreboardAccess");

    // DataWatcher
    if (DATA_WATCHER_NEW_ACCESS_VER) {
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.datawatcher.NewDataWatcherAccess");
    } else {
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.reflect.datawatcher.LegacyDataWatcherAccess");
    }
  }

  public static Field ensureAccessible(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
    return field;
  }

}