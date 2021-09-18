package de.jpx3.intave.entity.datawatcher;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;
import org.bukkit.entity.Player;

public final class DataWatcherAccess {
  private final static boolean MODERN_ACCESS = MinecraftVersions.VER1_9_0.atOrAbove();

  public static final int WATCHER_BLOCKING_ID = MinecraftVersions.VER1_9_0.atOrAbove() ? 1 : 4;
  public static final int WATCHER_SNEAK_ID = 1;
  public static final int WATCHER_SPRINT_ID = 3;

  private static DataWatcherAccessor nativeDataWatcherAccessor;

  static {
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    String className = "de.jpx3.intave.entity.datawatcher.LegacyDataWatcherAccessor";
    if (MODERN_ACCESS) {
      className = "de.jpx3.intave.entity.datawatcher.ModernDataWatcherAccessor";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, className);
    try {
      nativeDataWatcherAccessor = (DataWatcherAccessor) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      exception.printStackTrace();
    }
  }

  public static void setDataWatcherFlag(Player player, int key, boolean flag) {
    nativeDataWatcherAccessor.setDataWatcherFlag(player, key, flag);
  }

  public static boolean getDataWatcherFlag(Player player, int key) {
    return nativeDataWatcherAccessor.getDataWatcherFlag(player, key);
  }
}