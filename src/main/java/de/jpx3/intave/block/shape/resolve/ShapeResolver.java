package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_13_0;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_9_0;

/**
 * The {@link ShapeResolver} is a factory object that constructs the main bounding box resolver pipeline.
 * <br>
 * The pipeline consists of
 * <ul>
 *   <li>{@link PatcherReshaperPipe}</li>
 *   <li>{@link CubeMemoryPipe}</li>
 *   <li>{@link EmptyPrefetchPipe}</li>
 *   <li>{@link CorruptedFilteringPipe}</li>
 *   <li>{@link VariantCachePipe} (only on 1.14+)</li>
 *   <li>drill (generic, version specified)</li>
 * </ul>
 * in the given order.
 * <br>
 * Use {@link ShapeResolver#globalPipeline()} to retrieve the pipelines head.
 *
 * @see ShapeResolverPipeline
 * @see ExtendedBlockStateCache
 */
public final class ShapeResolver {
  private static final ShapeResolverPipeline GLOBAL = createPipelineFor(DrillResolver.selectedDrill());

  public static ShapeResolverPipeline createPipelineFor(ShapeResolverPipeline drill) {
    ShapeResolverPipeline resolver = drill;
    // drill failure subroutine
    if (!VER1_13_0.atOrAbove()) {
      resolver = new DrillRescuePipe(resolver);
    }
    if (VER1_9_0.atOrAbove()) {
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
    return resolver;
  }

  public static ShapeResolverPipeline globalPipeline() {
    return GLOBAL;
  }
}
