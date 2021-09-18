package de.jpx3.intave.block.state;

import com.google.common.collect.Lists;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolver;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.diagnostic.ShapeAccessFlowStudy;
import de.jpx3.intave.math.Hypot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

public final class MultiChunkKeyBlockStateAccess implements BlockStateAccess {
  private final static int BUILD_LIMIT = 255;
  private final Player player;
  private final ShapeResolverPipeline shapeResolver;
  private final Map<Long, BlockState> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Location, BlockState> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, BlockState> indexedReplacements = new ConcurrentHashMap<>(64);
  private final List<Location> replacementLocations = Lists.newCopyOnWriteArrayList();
  private int originChunkX, originChunkZ;
  private int chunkX, chunkZ;

  private MultiChunkKeyBlockStateAccess(
    Player player, ShapeResolverPipeline resolver
  ) {
    this.player = player;
    this.shapeResolver = resolver;
  }

  @Override
  public @NotNull BlockShape resolveShape(int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return BlockShapes.empty();
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
    BlockState blockState = indexedReplacements.get(key);
    if (blockState != null) {
      return blockState.shape();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.unsafe__BlockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.shape();
  }

  @Override
  public @NotNull Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return Material.AIR;
    }
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
    BlockState blockState = indexedReplacements.get(key);
    if (blockState != null) {
      return blockState.type();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.unsafe__BlockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.type();
  }

  @Override
  public int resolveVariantIndex(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return 0;
    }
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
    BlockState blockState = indexedReplacements.get(key);
    if (blockState != null) {
      return blockState.variantIndex();
    }
    blockState = blockCache.get(key);
    if (blockState == null) {
      World world = player.getWorld();
      Block block = VolatileBlockAccess.unsafe__BlockAccess(world, posX, posY, posZ);
      blockState = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockState);
      }
    }
    return blockState.variantIndex();
  }

  private BlockState lookup(World world, Block block, int posX, int posY, int posZ) {
    if (block.getY() < 0) {
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
  public void identityInvalidate() {
    invalidate();
    locatedReplacements.clear();
    indexedReplacements.clear();
    replacementLocations.clear();
  }

  @Override
  public void invalidate() {
    blockCache.clear();
  }

  @Override
  public void invalidate0(int posX, int posY, int posZ) {
    blockCache.remove(bigKey(posX, posY, posZ));
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {
    invalidateOverride(posX, posY, posZ);
    BlockState blockState;
    if (type == Material.AIR) {
      blockState = BlockState.empty();
    } else {
      BlockShape shape = shapeResolver.resolve(world, player, type, variant, posX, posY, posZ);
      blockState = new BlockState(shape, type, variant);
    }
    long key = bigKey(posX, posY, posZ);
    Location position = new Location(null, posX, posY, posZ);
    locatedReplacements.put(position, blockState);
    replacementLocations.add(position);
    indexedReplacements.put(key, blockState);
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Location location : locatedReplacements.keySet()) {
      if (location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        long key = bigKey(location);
        locatedReplacements.remove(location);
        replacementLocations.remove(location);
        indexedReplacements.remove(key);
      }
    }
  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return indexedReplacements.containsKey(key);
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return indexedReplacements.get(key);
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.remove(key);
  }

  public void purgeOverrides() {
    for (Location replacementLocation : replacementLocations) {
      BlockState blockState = locatedReplacements.get(replacementLocation);
      if (blockState == null || blockState.expired()) {
        replacementLocations.remove(replacementLocation);
        locatedReplacements.remove(replacementLocation);
        indexedReplacements.remove(bigKey(replacementLocation));
      }
    }
  }

  @Override
  @Deprecated
  public Map<Location, BlockState> locatedReplacements() {
    return locatedReplacements;
  }

  @Override
  @Deprecated
  public Map<Long, BlockState> indexedReplacements() {
    return indexedReplacements;
  }

  private long bigKey(Location location) {
    return bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }

  public static MultiChunkKeyBlockStateAccess withDefaultResolverOf(Player player) {
    return ofCustomResolver(player, ShapeResolver.pipelineHead());
  }

  public static MultiChunkKeyBlockStateAccess ofCustomResolver(Player player, ShapeResolverPipeline resolver) {
    return new MultiChunkKeyBlockStateAccess(player, resolver);
  }
}
