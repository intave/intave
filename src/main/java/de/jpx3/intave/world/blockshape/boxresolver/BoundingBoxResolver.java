package de.jpx3.intave.world.blockshape.boxresolver;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.world.blockshape.MultiChunkKeyOCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.boxresolver.drill.AbstractBoundingBoxDrill;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_14_0;

/**
 * The {@link BoundingBoxResolver} is a factory object that constructs the main bounding box resolver pipeline.
 * <br>
 * The pipeline consists of
 * <ul>
 *   <li>{@link PatcherReshaperPipe}</li>
 *   <li>{@link CubeMemoryPipe}</li>
 *   <li>{@link EmptyPrefetchPipe}</li>
 *   <li>{@link CorruptedFilteringPipe}</li>
 *   <li>{@link VariantCachePipe} (only on 1.14+)</li>
 *   <li>{@link AbstractBoundingBoxDrill} (generic, version specified)</li>
 * </ul>
 * in the given order.
 * <br>
 * Use {@link BoundingBoxResolver#pipelineHead()} to retrieve the pipelines head.
 *
 * @see ResolverPipeline
 * @see OCBlockShapeAccess
 * @see MultiChunkKeyOCBlockShapeAccess
 */
public final class BoundingBoxResolver {
  private static ResolverPipeline resolver;

  public static void setup() {
//    PatchyClassSwitchLoader<?> acbbResolver = PatchyClassSwitchLoader
//      .builderFor("de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v{ver}AlwaysCollidingBoundingBox")
//      .withVersions(VER1_8_0, VER1_9_0, VER1_12_0)
//      .ignoreFrom(VER1_13_0)
//      .complete();
//
//    acbbResolver.loadIfAvailable();
//
//    PatchyClassSwitchLoader<ResolverPipeline> drillResolver = PatchyClassSwitchLoader
//      .<ResolverPipeline>builderFor("de.jpx3.intave.world.blockshape.boxresolver.drill.v{ver}BoundingBoxDrill")
//      .withVersions(VER1_8_0, VER1_9_0, VER1_12_0, VER1_13_0, VER1_14_0, VER1_17_1)
//      .complete();

    // ugly, the FUCKING way ZKM FUCKING likes it
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
    if (VER1_14_0.atOrAbove()) {
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
