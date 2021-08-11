package de.jpx3.intave.world.blockshape.boxresolver;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.PatchyLoadingInjector;

public final class BoundingBoxResolver {
  private static ResolverPipeline resolver;

  public static void setup() {
    // ugly, the way ZKM likes it
    String drillClassName, acClassName = "";

    if (MinecraftVersions.VER1_17_1.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v17b1BoundingBoxDrill";
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v14BoundingBoxDrill";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v13BoundingBoxDrill";
    } else if (MinecraftVersions.VER1_12_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v12BoundingBoxDrill";
      acClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v12AlwaysCollidingBoundingBox";
    } else if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v9BoundingBoxDrill";
      acClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v9AlwaysCollidingBoundingBox";
    } else {
      drillClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.v8BoundingBoxDrill";
      acClassName = "de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v8AlwaysCollidingBoundingBox";
    }

    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, acClassName);
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, drillClassName);

    // server resolver
    resolver = instanceOf(drillClassName);
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      // cache
      resolver = new VariantCachePipe(resolver);
    }
    // corrupted filter
    resolver = new CorruptedFilteringPipe(resolver);
    // empty prefilter
    resolver = new EmptyPrefetchPipe(resolver);
    // cube prefilter
    resolver = new CubeMemoryPipe(resolver);
    // patch reshaper
    resolver = new PatcherReshaperPipe(resolver);
  }

  public static ResolverPipeline pipelineHead() {
    return resolver;
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }
}
