package de.jpx3.intave.world.blockaccess;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;

public final class BlockAccessProvider {
  private static BlockAccessor blockAccessor;
  public static void setup() {
    String resolverName = "de.jpx3.intave.world.blockaccess.v8BlockAccessor";
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v9BlockAccessor";
    }
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v13BlockAccessor";
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.world.blockaccess.v14BlockAccessor";
    }
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, resolverName);
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.world.state.BlockStateData$BlockStateServerBridge");
    blockAccessor = instanceOf(resolverName);
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  static BlockAccessor blockAccessor() {
    return blockAccessor;
  }
}
