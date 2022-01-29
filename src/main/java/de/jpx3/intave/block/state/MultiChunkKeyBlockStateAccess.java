package de.jpx3.intave.block.state;

import com.google.common.collect.Maps;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolver;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantAccess;
import de.jpx3.intave.diagnostic.ShapeAccessFlowStudy;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.shade.Position;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

public final class MultiChunkKeyBlockStateAccess implements BlockStateAccess {
  private final Player player;
  private final ShapeResolverPipeline shapeResolver;
  private final Map<Long, BlockState> blockCache = Maps.newConcurrentMap();
  private final FastBlockStateExpiryCache replacementCache;
  private int originChunkX, originChunkZ;
  private int chunkX, chunkZ;

  private MultiChunkKeyBlockStateAccess(
    Player player, ShapeResolverPipeline resolver
  ) {
    this.player = player;
    this.shapeResolver = resolver;
    this.replacementCache = new FastBlockStateExpiryCache(player);
  }

  @Override
  public @NotNull BlockShape shapeAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return BlockShapes.emptyShape();
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.shape();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.shape();
  }

  @Override
  public @NotNull Material typeAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return Material.AIR;
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.type();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.type();
  }

  @Override
  public int variantIndexAt(int posX, int posY, int posZ) {
    if (posY < WorldHeight.LOWER_WORLD_LIMIT || WorldHeight.UPPER_WORLD_LIMIT < posY) {
      return 0;
    }
    int chunkX = posX >> 4, chunkZ = posZ >> 4;
    ShapeAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Hypot.fast(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockState blockState = replacementCache.byKey(key);
    if (blockState != null) {
      return blockState.variantIndex();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.variantIndex();
  }

  private BlockState lookup(World world, Block block, int posX, int posY, int posZ) {
    if (block.getY() < WorldHeight.LOWER_WORLD_LIMIT) {
      return BlockState.empty();
    }
    Material type = BlockTypeAccess.typeAccess(block, player);
    if (type == Material.AIR) {
      return BlockState.empty();
    } else {
      ShapeAccessFlowStudy.incremLookups();
      int variant = BlockVariantAccess.variantAccess(block);
      BlockShape shape = shapeResolver.resolve(world, player, type, variant, posX, posY, posZ);
      return new BlockState(shape, type, variant);
    }
  }

  @Override
  public void invalidateAll() {
    invalidateCache();
    replacementCache.clear();
//    player.sendMessage(ChatColor.GOLD + "invalidateAll()");
  }

  @Override
  public void invalidateCache() {
    blockCache.clear();
  }

  @Override
  public void invalidateCacheAt0(int posX, int posY, int posZ) {
    blockCache.remove(bigKey(posX, posY, posZ));

//    player.sendMessage(ChatColor.GOLD + "invalidateCacheAt0("+posX+", "+posY+", "+posZ+")");
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {
//    invalidateOverride(posX, posY, posZ);
    long key = bigKey(posX, posY, posZ);
    replacementCache.remove(key);

    BlockState blockState;
    if (type == Material.AIR) {
      blockState = BlockState.empty();
    } else {
      BlockShape shape = shapeResolver.resolve(world, player, type, variant, posX, posY, posZ);
      blockState = new BlockState(shape, type, variant);
    }
    Position position = new Position(posX, posY, posZ);
    replacementCache.insert(position, blockState);
//    player.sendMessage(ChatColor.GOLD + "override("+posX+", "+posY+", "+posZ+", "+type+")");
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    replacementCache.chunkReset(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return replacementCache.replaced(key);
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return replacementCache.byKey(key);
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    replacementCache.remove(key);
//    player.sendMessage(ChatColor.GOLD + "invalidateOverride("+posX+", "+posY+", "+posZ+")");
  }

  private long lastPurge = System.currentTimeMillis();

  public void purgeOverrides() {
    if (System.currentTimeMillis() - lastPurge > 500) {
      lastPurge = System.currentTimeMillis();
      replacementCache.internalRefresh();
    }
  }

  @Override
  @Deprecated
  public Map<Position, BlockState> locatedReplacements() {
    return replacementCache.located();
  }

  @Override
  @Deprecated
  public Map<Long, BlockState> indexedReplacements() {
    return replacementCache.indexed();
  }

  private long bigKey(Location location) {
    return bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }

  public static MultiChunkKeyBlockStateAccess forPlayer(Player player) {
    return forPlayerWithResolver(player, ShapeResolver.pipelineHead());
  }

  public static MultiChunkKeyBlockStateAccess forPlayerWithResolver(Player player, ShapeResolverPipeline resolver) {
    return new MultiChunkKeyBlockStateAccess(player, resolver);
  }
}
