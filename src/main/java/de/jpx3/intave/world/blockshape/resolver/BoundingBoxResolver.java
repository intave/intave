package de.jpx3.intave.world.blockshape.resolver;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.world.blockshape.resolver.pipeline.*;

public final class BoundingBoxResolver {
  private static ResolverPipeline resolver;

  public static void createNew() {
    // ugly, the way ZKM likes it
    String drillClassName = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.v8BoundingBoxDrill";
    String acClass = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.acbbs.v8AlwaysCollidingBoundingBox";

    if (MinecraftVersions.VER1_17_1.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.v17b1BoundingBoxDrill";
      acClass = "";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.v13BoundingBoxDrill";
      acClass = "";
    } else if (MinecraftVersions.VER1_12_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.v12BoundingBoxDrill";
      acClass = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.acbbs.v12AlwaysCollidingBoundingBox";
    } else if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.v9BoundingBoxDrill";
      acClass = "de.jpx3.intave.world.blockshape.resolver.pipeline.drill.acbbs.v9AlwaysCollidingBoundingBox";
    }

    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), acClass);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), drillClassName);

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
