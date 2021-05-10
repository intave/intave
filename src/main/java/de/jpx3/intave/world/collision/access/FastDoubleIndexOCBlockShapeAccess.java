package de.jpx3.intave.world.collision.access;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collision.resolver.BoundingBoxResolvePipelineElement;
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

public final class FastDoubleIndexOCBlockShapeAccess implements OCBlockShapeAccess {
  private final static int FREQUENCY_OVERFLOW = 2;

  private final Player player;
  private final BoundingBoxResolvePipelineElement resolver;
  private final Map<Integer, BlockShape> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Long, BlockShape> frequencyCache = new ConcurrentHashMap<>(4096);

  private final Map<Location, BlockShape> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, BlockShape> indexedReplacements = new ConcurrentHashMap<>(64);

  private int frequencyCounter;
  private long frequencyCheckReset;

  private int chunkXPos;
  private int chunkX;
  private int chunkZPos;
  private int chunkZ;

  public FastDoubleIndexOCBlockShapeAccess(Player player, BoundingBoxResolvePipelineElement resolver) {
    this.player = player;
    this.resolver = resolver;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();

      if(AccessHelper.now() - frequencyCheckReset > 1000) {
        frequencyCache.clear();
        frequencyCounter = 0;
        frequencyCheckReset = AccessHelper.now();
      }
      frequencyCounter++;
    }

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      BlockShape entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.boundingBoxes();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      BlockShape blockShape = frequencyCache.get(daBigKey);
      if (blockShape == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        blockShape = lookup(world, block, posX, posY, posZ);
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, blockShape);
        }
      }
      return blockShape.boundingBoxes();
    }

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int localKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    BlockShape blockShape = blockCache.get(localKey);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(localKey, blockShape);
      }
    }
    return blockShape.boundingBoxes();
  }

  @Override
  public Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();

      if(AccessHelper.now() - frequencyCheckReset > 1000) {
        frequencyCache.clear();
        frequencyCounter = 0;
        frequencyCheckReset = AccessHelper.now();
      }
      frequencyCounter++;
    }

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      BlockShape entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.type();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      BlockShape blockShape = frequencyCache.get(daBigKey);
      if (blockShape == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        blockShape = lookup(world, block, posX, posY, posZ);
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, blockShape);
        }
      }
      return blockShape.type();
    }

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int localKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    BlockShape blockShape = blockCache.get(localKey);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(localKey, blockShape);
      }
    }
    return blockShape.type();
  }

  @Override
  public int resolveData(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    BoundingBoxAccessFlowStudy.requests++;

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();

      if(AccessHelper.now() - frequencyCheckReset > 1000) {
        frequencyCache.clear();
        frequencyCounter = 0;
        frequencyCheckReset = AccessHelper.now();
      }
      frequencyCounter++;
    }

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      BlockShape entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.data();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      BlockShape blockShape = frequencyCache.get(daBigKey);
      if (blockShape == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        blockShape = lookup(world, block, posX, posY, posZ);
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, blockShape);
        }
      }
      return blockShape.data();
    }

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int localKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    BlockShape blockShape = blockCache.get(localKey);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(localKey, blockShape);
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
      BoundingBoxAccessFlowStudy.increaseLookups();
      int data = BlockDataAccess.dataIndexOf(block);
      List<WrappedAxisAlignedBB> boundingBoxes = resolver.customResolve(world, player, type, data, posX, posY, posZ);
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
    frequencyCache.clear();
  }

  @Override
  public void invalidate0(int posX, int posY, int posZ) {
    int chunkX = this.chunkXPos;
    int chunkZ = this.chunkZPos;
    if (posX < chunkX || posZ < chunkZ || chunkX + 16 <= posX || chunkZ + 16 <= posZ) {
      return;
    }
    byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);
    blockCache.remove(blockPositionKey);
    frequencyCache.remove(bigKey(posX, posY, posZ));
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int blockState) {
    invalidateOverride(posX, posY, posZ);
    BlockShape BlockShape;
    if(type == Material.AIR) {
      BlockShape = EMPTY_CACHE_ENTRY;
    } else {
      BlockShape = new BlockShape(
        constructBlock(world, posX, posY, posZ, type, blockState),
        type, blockState
      );
    }
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.put(key, BlockShape);
    locatedReplacements.put(new Location(world, posX, posY, posZ), BlockShape);
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Location location : locatedReplacements.keySet()) {
      if(
        location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
          location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos
      ) {
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
    return resolver.customResolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.remove(key);
  }

  @Override
  public void purgeOverrides() {
    if(indexedReplacements.isEmpty()) {
      return;
    }
    indexedReplacements.values().removeIf(BlockShape::expired);
    locatedReplacements.values().removeIf(BlockShape::expired);
  }

  @Override
  public Map<Location, BlockShape> locatedReplacements() {
    return locatedReplacements;
  }

  @Override
  public Map<Long, BlockShape> indexedReplacements() {
    return indexedReplacements;
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }
}
