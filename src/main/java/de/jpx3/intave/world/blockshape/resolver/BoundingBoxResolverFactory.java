package de.jpx3.intave.world.blockshape.resolver;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.world.blockshape.resolver.pipeline.DynamicCorruptedBlockFilter;
import de.jpx3.intave.world.blockshape.resolver.pipeline.DynamicCubePreFilter;
import de.jpx3.intave.world.blockshape.resolver.pipeline.DynamicEmptyBlockPreFilter;
import de.jpx3.intave.world.blockshape.resolver.pipeline.DynamicPatcherReshaper;

public final class BoundingBoxResolverFactory {
  private static BoundingBoxResolvePipeline resolver;

  public static void createNew() {
    // ugly, the way ZKM likes it
    String className = "de.jpx3.intave.world.blockshape.resolver.server.v8BoundingBoxResolver";
    String acClass = "de.jpx3.intave.world.blockshape.resolver.acbbs.v8AlwaysCollidingBoundingBox";

    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v17BoundingBoxResolver";
      acClass = "";
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v14BoundingBoxResolver";
      acClass = "";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v13BoundingBoxResolver";
      acClass = "";
    } else if (MinecraftVersions.VER1_12_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v12BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.blockshape.resolver.acbbs.v12AlwaysCollidingBoundingBox";
    } else if (MinecraftVersions.VER1_11_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v11BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.blockshape.resolver.acbbs.v11AlwaysCollidingBoundingBox";
    } else if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      className = "de.jpx3.intave.world.blockshape.resolver.server.v9BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.blockshape.resolver.acbbs.v9AlwaysCollidingBoundingBox";
    }

    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), acClass);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);

    // server resolver
    resolver = instanceOf(className);
    // corrupted filter
    resolver = new DynamicCorruptedBlockFilter(resolver);
    // empty prefilter
    resolver = new DynamicEmptyBlockPreFilter(resolver);
    // cube prefilter
    resolver = new DynamicCubePreFilter(resolver);
    // patch reshaper
    resolver = new DynamicPatcherReshaper(resolver);
  }

  public static BoundingBoxResolvePipeline resolver() {
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
