package de.jpx3.intave.world.blockshape;

import de.jpx3.intave.diagnostic.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockaccess.BlockVariantAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.boxresolver.BoundingBoxResolver;
import de.jpx3.intave.world.blockshape.boxresolver.ResolverPipeline;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

public final class MultiChunkKeyOCBlockShapeAccess implements OCBlockShapeAccess {
  private final static int BUILD_LIMIT = 255;
  private final Player player;
  private final ResolverPipeline boundingBoxResolver;
  private final Map<Long, BlockShape> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Location, BlockShape> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, BlockShape> indexedReplacements = new ConcurrentHashMap<>(64);
  private int originChunkX, originChunkZ;
  private int chunkX, chunkZ;

  private MultiChunkKeyOCBlockShapeAccess(
    Player player, ResolverPipeline resolver
  ) {
    this.player = player;
    this.boundingBoxResolver = resolver;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return Collections.emptyList();
    }
    BoundingBoxAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockShape blockShape = indexedReplacements.get(key);
    if (blockShape != null) {
      return blockShape.boundingBoxes();
    }
    blockShape = blockCache.get(key);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockShape);
      }
    }
    return blockShape.boundingBoxes();
  }

  @Override
  public Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return Material.AIR;
    }
    BoundingBoxAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockShape blockShape = indexedReplacements.get(key);
    if (blockShape != null) {
      return blockShape.type();
    }
    blockShape = blockCache.get(key);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockShape);
      }
    }
    return blockShape.type();
  }

  @Override
  public int resolveVariant(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || BUILD_LIMIT < posY) {
      return 0;
    }
    BoundingBoxAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if (distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockShape blockShape = indexedReplacements.get(key);
    if (blockShape != null) {
      return blockShape.variant();
    }
    blockShape = blockCache.get(key);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockShape);
      }
    }
    return blockShape.variant();
  }

  private final static BlockShape EMPTY_CACHE_ENTRY = new BlockShape(Collections.emptyList(), Material.AIR, 0);

  private BlockShape lookup(World world, Block block, int posX, int posY, int posZ) {
    if (block.getY() < 0) {
      return EMPTY_CACHE_ENTRY;
    }
    Material type = BlockTypeAccess.typeAccess(block, player);
    if (type == Material.AIR) {
      return EMPTY_CACHE_ENTRY;
    } else {
      BoundingBoxAccessFlowStudy.incremLookups();
      int variant = BlockVariantAccess.variantAccess(block);
      List<WrappedAxisAlignedBB> boundingBoxes = boundingBoxResolver.resolve(world, player, type, variant, posX, posY, posZ);
      return new BlockShape(boundingBoxes, type, variant);
    }
  }

  @Override
  public void identityInvalidate() {
    invalidate();
    locatedReplacements.clear();
    indexedReplacements.clear();
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
    BlockShape blockShape;
    if (type == Material.AIR) {
      blockShape = EMPTY_CACHE_ENTRY;
    } else {
      blockShape = new BlockShape(
        boundingBoxResolver.resolve(world, player, type, variant, posX, posY, posZ),
        type, variant
      );
    }
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.put(key, blockShape);
    locatedReplacements.put(new Location(world, posX, posY, posZ), blockShape);
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Location location : locatedReplacements.keySet()) {
      if (location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        long key = bigKey(location);
        locatedReplacements.remove(location);
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
  public BlockShape overrideOf(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return indexedReplacements.get(key);
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.remove(key);
  }

  public void purgeOverrides() {
    if (indexedReplacements.isEmpty()) {
      return;
    }
    indexedReplacements.values().removeIf(BlockShape::expired);
    locatedReplacements.values().removeIf(BlockShape::expired);
  }

  @Override
  @Deprecated
  public Map<Location, BlockShape> locatedReplacements() {
    return locatedReplacements;
  }

  @Override
  @Deprecated
  public Map<Long, BlockShape> indexedReplacements() {
    return indexedReplacements;
  }

  private long bigKey(Location location) {
    return bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }

  public static MultiChunkKeyOCBlockShapeAccess withDefaultResolverOf(Player player) {
    return ofCustomResolver(player, BoundingBoxResolver.pipelineHead());
  }

  public static MultiChunkKeyOCBlockShapeAccess ofCustomResolver(Player player, ResolverPipeline resolver) {
    return new MultiChunkKeyOCBlockShapeAccess(player, resolver);
  }
}
