package de.jpx3.intave.world.border;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.World;

public final class WorldBorders {
  private static WorldBorderAccess worldBorderAccess = new BukkitWorldBorderAccess();

  public static void setup() {
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      worldBorderAccess = new BukkitWorldBorderAccess();
    } else {
      ClassLoader classLoader = WorldBorders.class.getClassLoader();
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.world.border.CarefulWorldBorderAccess");
      worldBorderAccess = new CarefulWorldBorderAccess();
    }
    worldBorderAccess = new CachedForwardingWorldBorderAccess(worldBorderAccess);
  }

  public static double sizeOfWorldBorderIn(User user, World world) {
    // this cast is necessary, trust me
    return (float)worldBorderAccess.sizeOf(world);
  }

  public static Location centerOfWorldBorderIn(User user, World world) {
    return worldBorderAccess.centerOf(world);
  }
}
