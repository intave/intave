package de.jpx3.intave.world.blockshape;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
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
  private final Player player;
  private final BoundingBoxResolvePipeline boundingBoxResolver;
  private final Map<Long, BlockShape> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Location, BlockShape> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, BlockShape> indexedReplacements = new ConcurrentHashMap<>(64);
  private int originChunkX;
  private int originChunkZ;
  private int chunkX;
  private int chunkZ;

  public MultiChunkKeyOCBlockShapeAccess(Player player, BoundingBoxResolvePipeline resolver) {
    this.player = player;
    this.boundingBoxResolver = resolver;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      return Collections.emptyList();
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if(distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }

    long key = bigKey(posX, posY, posZ);

    BlockShape blockShape = indexedReplacements.get(key);
    if(blockShape != null) {
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
    if (posY < 0 || 255 < posY) {
      return Material.AIR;
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if(distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }

    long key = bigKey(posX, posY, posZ);

    BlockShape blockShape = indexedReplacements.get(key);
    if(blockShape != null) {
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
  public int resolveData(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      return 0;
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      double distance = Math.hypot(chunkX - originChunkX, chunkZ - originChunkZ);
      if(distance > 2 || blockCache.size() > 4096) {
        this.originChunkX = chunkX;
        this.originChunkZ = chunkZ;
        blockCache.clear();
      }
      purgeOverrides();
    }

    long key = bigKey(posX, posY, posZ);

    BlockShape blockShape = indexedReplacements.get(key);
    if(blockShape != null) {
      return blockShape.data();
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
    return blockShape.data();
  }

  private final static BlockShape EMPTY_CACHE_ENTRY = new BlockShape(Collections.emptyList(), Material.AIR, 0);

  private BlockShape lookup(World world, Block block, int posX, int posY, int posZ) {
    Material type = block.getType();
    if(type == Material.AIR) {
      return EMPTY_CACHE_ENTRY;
    } else {
      BoundingBoxAccessFlowStudy.incremLookups();
      int data = BlockDataAccess.dataIndexOf(block);
      List<WrappedAxisAlignedBB> boundingBoxes = boundingBoxResolver.customResolve(world, player, type, data, posX, posY, posZ);
      return new BlockShape(boundingBoxes, type, data);
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
//    int chunkX = this.originChunkX;
//    int chunkZ = this.originChunkZ;
//    if (posX < chunkX || posZ < chunkZ || chunkX + 16 <= posX || chunkZ + 16 <= posZ) {
//      return;
//    }
    blockCache.remove(bigKey(posX, posY, posZ));
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int blockState) {
    invalidateOverride(posX, posY, posZ);
    BlockShape blockShape;
    if(type == Material.AIR) {
      blockShape = EMPTY_CACHE_ENTRY;
    } else {
      blockShape = new BlockShape(
        constructBlock(world, posX, posY, posZ, type, blockState),
        type, blockState
      );
    }
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.put(key, blockShape);
    locatedReplacements.put(new Location(world, posX, posY, posZ), blockShape);
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Location location : locatedReplacements.keySet()) {
      if(location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        long key = bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
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
  public List<WrappedAxisAlignedBB> constructBlock(World world, int posX, int posY, int posZ, Material type, int blockState) {
    BoundingBoxAccessFlowStudy.incremLookups();
    return boundingBoxResolver.customResolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.remove(key);
  }

  public void purgeOverrides() {
    if(indexedReplacements.isEmpty()) {
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

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }
}
