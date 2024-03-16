package de.jpx3.intave.block.cache;

import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.ShapeResolver;
import org.bukkit.entity.Player;

public final class BlockCaches {
  public static BlockCache cacheForPlayer(Player player) {
    return cacheForPlayerWithResolver(player, ShapeResolver.pipelineHead());
  }

  public static BlockCache cacheForPlayerWithResolver(Player player, ShapeResolverPipeline resolver) {
    return new MultiChunkKeyBlockCache(player, resolver);
  }

  public static BlockCache passthroughCacheWithNativeDrill(Player player) {
    return new PassthroughBlockCache(player, ShapeResolver.pipelineDrill());
  }

  public static BlockCache emptyCache() {
    return new EmptyExtendedBlockStateCache();
  }
}
