package de.jpx3.intave.block.access;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;

public final class BlockAccess {
  private static BlockAccessor blockAccessor;
  public static void setup() {
    String resolverName = "de.jpx3.intave.block.access.v8BlockAccessor";
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.block.access.v9BlockAccessor";
    }
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.block.access.v13BlockAccessor";
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      resolverName = "de.jpx3.intave.block.access.v14BlockAccessor";
    }
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, resolverName);
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

  public static BlockAccessor global() {
    return blockAccessor;
  }
}
