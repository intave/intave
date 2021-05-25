package de.jpx3.intave.world.blockshape.garbage;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.tools.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.BlockShape;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
import de.jpx3.intave.world.blockshape.resolver.pipeline.patcher.BoundingBoxPatcher;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

@DoNotFlowObfuscate
public final class FallbackMultiChunkKeyOCBlockShapeAccess implements OCBlockShapeAccess {
  private final static BlockShape SERVER_LOOKUP_REQUIRED = new BlockShape(Collections.emptyList(), Material.AIR, 0);
  private final static Map<World, Map<Long, BlockShape>> globalFallbackCache = new ConcurrentHashMap<>(4096 * 8);

  static {
    Synchronizer.synchronize(FallbackMultiChunkKeyOCBlockShapeAccess::registerPurgeGlobalFallbackCacheTask);
  }

  private static void registerPurgeGlobalFallbackCacheTask() {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(IntavePlugin.singletonInstance(), () -> {
      BackgroundExecutor.execute(FallbackMultiChunkKeyOCBlockShapeAccess::purgeGlobalFallbackCache);
    }, 0, 20 * 60 * 10);
  }

  private final static long REQUIRED_LIMIT = 512 * 64;
  private final static long MAXIMUM_LIMIT = 512 * 512;
  private final static double DEATH_RATIO = 0.333;

  @Native
  private static void purgeGlobalFallbackCache() {
    boolean deletedStuff = false;
    for (Map.Entry<World, Map<Long, BlockShape>> worldMapEntry : globalFallbackCache.entrySet()) {
      Map<Long, BlockShape> globalWorldCache = worldMapEntry.getValue();
      if(globalWorldCache.size() < REQUIRED_LIMIT) {
        continue;
      }
      long deletedEntries = 0;
      do {
        List<BlockShape> blockShapes = new ArrayList<>(globalWorldCache.values());
        blockShapes.sort(Comparator.comparing(BlockShape::successfulLookups).reversed());
        int oldSize = blockShapes.size();
        int newSize = (int) (((double) oldSize) * (1 - DEATH_RATIO));
        deletedEntries = oldSize - newSize;
        List<BlockShape> newShapes = blockShapes.subList(0, newSize);
        globalWorldCache.values().removeIf(blockShape -> !newShapes.contains(blockShape));
        deletedStuff = true;
      } while (globalWorldCache.size() > MAXIMUM_LIMIT);

      for (Player player : Bukkit.getOnlinePlayers()) {
        if (IntavePlugin.singletonInstance().sibylIntegrationService().isAuthenticated(player)) {
          long finalDeletedEntries = deletedEntries;
          Synchronizer.synchronize(() -> player.sendMessage(ChatColor.GRAY + "[BBA] Purged " + finalDeletedEntries + " from global box cache in world" + worldMapEntry.getKey().getName()));
        }
      }
    }
    if(deletedStuff) {
      // why not
      System.gc();
    }
  }

  private final Player player;
  private final BoundingBoxResolvePipeline resolver;
  private final Map<Long, BlockShape> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Location, BlockShape> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, BlockShape> indexedReplacements = new ConcurrentHashMap<>(64);
  private int originChunkX;
  private int originChunkZ;
  private int chunkX;
  private int chunkZ;

  public FallbackMultiChunkKeyOCBlockShapeAccess(Player player, BoundingBoxResolvePipeline resolver) {
    this.player = player;
    this.resolver = resolver;
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
      blockShape = cachedLookup(world, block, posX, posY, posZ);
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
      blockShape = cachedLookup(world, block, posX, posY, posZ);
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
      blockShape = cachedLookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockShape);
      }
    }
    return blockShape.data();
  }

  private BlockShape resolveOriginalShape(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      return EMPTY_CACHE_ENTRY;
    }
    BoundingBoxAccessFlowStudy.requests++;
    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      purgeOverrides();
    }
    long key = bigKey(posX, posY, posZ);
    BlockShape blockShape = blockCache.get(key);
    if (blockShape == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      blockShape = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && block.getY() >= 0) {
        blockCache.put(key, blockShape);
      }
    }
    return blockShape;
  }

  private final static BlockShape EMPTY_CACHE_ENTRY = new BlockShape(Collections.emptyList(), Material.AIR, 0);

  private BlockShape cachedLookup(World world, Block block, int posX, int posY, int posZ) {
    Material type = block.getType();
    if(type == Material.AIR) {
      return EMPTY_CACHE_ENTRY;
    } else {
      BoundingBoxAccessFlowStudy.incremLookups();
      boolean typeIsIntavePatched = BoundingBoxPatcher.requiresPatch(type);
      if(!typeIsIntavePatched && block.getY() >= 0 && globalKeyValid(posX, posY, posZ)) {
        Map<Long, BlockShape> worldFallbackCache = globalFallbackCache.computeIfAbsent(world, x -> new ConcurrentHashMap<>());
        long key = bigKey(posX, posY, posZ);
        boolean lookup = false;
        BlockShape resolve;
        if((resolve = worldFallbackCache.get(key)) == null) {
          resolve = lookup(world, block, posX, posY, posZ);
          worldFallbackCache.put(key, resolve);
          BoundingBoxAccessFlowStudy.incremYellowLookups();
          lookup = true;
        }
        if(resolve == SERVER_LOOKUP_REQUIRED) {
          BoundingBoxAccessFlowStudy.incremRedLookups();
          resolve = lookup(world, block, posX, posY, posZ);
        } else if(!lookup){
          resolve.successfulFallbackLookup();
          BoundingBoxAccessFlowStudy.incremGreenLookups();
        }
        return resolve;
      }
      BoundingBoxAccessFlowStudy.incremRedLookups();
      return lookup(world, block, posX, posY, posZ);
    }
  }

  private BlockShape lookup(World world, Block block, int posX, int posY, int posZ) {
    Material type = block.getType();
    int data = BlockDataAccess.dataIndexOf(block);
    List<WrappedAxisAlignedBB> boundingBoxes = resolver.customResolve(world, player, type, data, posX, posY, posZ);
    return new BlockShape(boundingBoxes, type, data);
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
    BlockShape originalShape = resolveOriginalShape(posX >> 4, posZ >> 4, posX, posY, posZ);
    boolean shapeRemains = (blockShape.equals(originalShape));
    long key = bigKey(posX, posY, posZ);
    if(!shapeRemains) {
      Map<Long, BlockShape> worldBlockShapeCache = globalFallbackCache.computeIfAbsent(world, x -> new ConcurrentHashMap<>());
      if(type.name().contains("DOOR")) {
        worldBlockShapeCache.put(bigKey(posX, posY - 1, posZ), SERVER_LOOKUP_REQUIRED);
        worldBlockShapeCache.put(bigKey(posX, posY + 1, posZ), SERVER_LOOKUP_REQUIRED);
      }
      worldBlockShapeCache.put(key, SERVER_LOOKUP_REQUIRED);
    }

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
//    BoundingBoxAccessFlowStudy.increaseLookups();
    return resolver.customResolve(world, player, type, blockState, posX, posY, posZ);
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

  private boolean globalKeyValid(int posX, int posY, int posZ) {
    return Math.abs(posX) < 0x1fffffL && posY >= 0 && posY <= 256 && Math.abs(posZ) < 0x1fffffL;
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }
}
