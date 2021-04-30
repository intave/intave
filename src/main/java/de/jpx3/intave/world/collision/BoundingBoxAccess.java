package de.jpx3.intave.world.collision;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collision.dynamic.CubeDynamicResolver;
import de.jpx3.intave.world.collision.dynamic.LiquidDynamicResolver;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
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

public final class BoundingBoxAccess {
  private final static int FREQUENCY_OVERFLOW = 2;
  private static BoundingBoxResolver globalBoundingBoxResolver;

  public static void setup() {
    // ugly, the way ZKM likes it

    String className = "de.jpx3.intave.world.collision.resolver.v8BoundingBoxResolver";
    String acClass = "de.jpx3.intave.world.collision.resolver.ac.v8AlwaysCollidingBoundingBox";

    if(MinecraftVersions.VER1_14_0.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v14BoundingBoxResolver";
    } else if(MinecraftVersions.VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v13BoundingBoxResolver";
//      acClass = "de.jpx3.intave.world.collision.resolver.ac.v13AlwaysCollidingBoundingBox";
    } else if(MinecraftVersions.VER1_12_0.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v12BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v12AlwaysCollidingBoundingBox";
    } else if(MinecraftVersions.VER1_11_0.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v11BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v11AlwaysCollidingBoundingBox";
    } else if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v9BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v9AlwaysCollidingBoundingBox";
    }

    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), acClass);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    globalBoundingBoxResolver = instanceOf(className);
    globalBoundingBoxResolver = new LiquidDynamicResolver(globalBoundingBoxResolver);
    globalBoundingBoxResolver = new CubeDynamicResolver(globalBoundingBoxResolver);
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private final Player player;
  private final Map<Integer, CacheEntry> blockCache = new ConcurrentHashMap<>(4096);
  private final Map<Long, CacheEntry> frequencyCache = new ConcurrentHashMap<>(4096);

  private final Map<Location, CacheEntry> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, CacheEntry> indexedReplacements = new ConcurrentHashMap<>(64);

  private int frequencyCounter;
  private long frequencyCheckReset;

  private int chunkXPos;
  private int chunkX;
  private int chunkZPos;
  private int chunkZ;

  public BoundingBoxAccess(Player player) {
    this.player = player;
  }

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

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      CacheEntry entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.boundingBoxes();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      CacheEntry cacheEntry = frequencyCache.get(daBigKey);
      if (cacheEntry == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        if ((cacheEntry = blockCache.get(blockPositionKey)) == null) {
          cacheEntry = lookup(world, block, posX, posY, posZ);
        }
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, cacheEntry);
        }
      }
      return cacheEntry.boundingBoxes();
    }

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      cacheEntry = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.boundingBoxes();
  }

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

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      CacheEntry entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.type();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      CacheEntry cacheEntry = frequencyCache.get(daBigKey);
      if (cacheEntry == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        if ((cacheEntry = blockCache.get(blockPositionKey)) == null) {
          cacheEntry = lookup(world, block, posX, posY, posZ);
        }
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, cacheEntry);
        }
      }
      return cacheEntry.type();
    }

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      cacheEntry = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.type();
  }

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

    byte dx = (byte) (this.chunkXPos - posX), dz = (byte) (this.chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    if(!indexedReplacements.isEmpty()) {
      long key = bigKey(posX, posY, posZ);
      CacheEntry entry = indexedReplacements.get(key);
      if(entry != null) {
        return entry.data();
      }
    }

    if(frequencyCounter > FREQUENCY_OVERFLOW) {
      long daBigKey = bigKey(posX, posY, posZ);
      CacheEntry cacheEntry = frequencyCache.get(daBigKey);
      if (cacheEntry == null) {
        World world = player.getWorld();
        Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
        if ((cacheEntry = blockCache.get(blockPositionKey)) == null) {
          cacheEntry = lookup(world, block, posX, posY, posZ);
        }
        boolean cacheType = /* to avoid saving unloaded chunk data*/ block.getY() >= 0;
        if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
          frequencyCache.put(daBigKey, cacheEntry);
        }
      }
      return cacheEntry.data();
    }

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      boolean cacheType = block.getY() >= 0;
      cacheEntry = lookup(world, block, posX, posY, posZ);
      if (!DISABLE_BLOCK_CACHING_ENTIRELY && cacheType) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.data();
  }

  private final static CacheEntry EMPTY_CACHE_ENTRY = new CacheEntry(Collections.emptyList(), Material.AIR, 0);

  private CacheEntry lookup(World world, Block block, int posX, int posY, int posZ) {
    Material type = block.getType();
    if(type == Material.AIR) {
      return EMPTY_CACHE_ENTRY;
    } else {
      BoundingBoxAccessFlowStudy.increaseLookups();
      List<WrappedAxisAlignedBB> boundingBoxes;
      boundingBoxes = globalBoundingBoxResolver.resolve(world, type, posX, posY, posZ);
      boundingBoxes = BoundingBoxPatcher.patch(world, player, block, boundingBoxes);
      return new CacheEntry(boundingBoxes, type, BlockDataAccess.dataIndexOf(block));
    }
  }

  public void identityInvalidate() {
    invalidate();
    locatedReplacements.clear();
    indexedReplacements.clear();
  }

  public void invalidate() {
    blockCache.clear();
    frequencyCache.clear();
  }

  public void invalidate(int posX, int posY, int posZ) {
    invalidate0(posX + 1, posY, posZ);
    invalidate0(posX - 1, posY, posZ);
    invalidate0(posX, posY, posZ + 1);
    invalidate0(posX, posY, posZ - 1);
    invalidate0(posX, posY + 1, posZ);
    invalidate0(posX, posY - 1, posZ);
    invalidate0(posX, posY, posZ);
  }

  private void invalidate0(int posX, int posY, int posZ) {
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

  public void override(World world, int posX, int posY, int posZ, Material type, int blockState) {
    invalidateOverride(posX, posY, posZ);
    CacheEntry cacheEntry;
    if(type == Material.AIR) {
      cacheEntry = EMPTY_CACHE_ENTRY;
    } else {
      cacheEntry = new CacheEntry(
        constructBlock(world, posX, posY, posZ, type, blockState),
        type, blockState
      );
    }
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.put(key, cacheEntry);
    locatedReplacements.put(new Location(world, posX, posY, posZ), cacheEntry);
  }

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

  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return indexedReplacements.containsKey(key);
  }

  public CacheEntry overrideOf(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    return indexedReplacements.get(key);
  }

  public List<WrappedAxisAlignedBB> constructBlock(World world, int posX, int posY, int posZ, Material type, int blockState) {
    List<WrappedAxisAlignedBB> resolve;
    resolve = globalBoundingBoxResolver.resolve(world, posX, posY, posZ, type, blockState);
    resolve = BoundingBoxPatcher.patch(world, player, posX, posY, posZ, type, blockState, resolve);
    return resolve;
  }

  public void invalidateOverride(int posX, int posY, int posZ) {
    long key = bigKey(posX, posY, posZ);
    indexedReplacements.remove(key);
  }

  public void purgeOverrides() {
    if(indexedReplacements.isEmpty()) {
      return;
    }
    indexedReplacements.values().removeIf(CacheEntry::expired);
    locatedReplacements.values().removeIf(CacheEntry::expired);
  }

  public Map<Location, CacheEntry> locatedReplacements() {
    return locatedReplacements;
  }

  public Map<Long, CacheEntry> indexedReplacements() {
    return indexedReplacements;
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3FFFFFL) << 42 | (posY & 0xFFFFFL) | (posZ & 0x3FFFFFL) << 20;
  }

  @Deprecated
  // the global bb resolver should not be available to external classes, please remove this method ~richy
  public static BoundingBoxResolver globalBoundingBoxResolver() {
    return globalBoundingBoxResolver;
  }

  public static class CacheEntry {
    private final List<WrappedAxisAlignedBB> boundingBoxes;
    private final Material type;
    private final int data;
    private final long creation = AccessHelper.now();

    public CacheEntry(List<WrappedAxisAlignedBB> boundingBoxes, Material type, int data) {
      this.boundingBoxes = boundingBoxes;
      this.type = type;
      this.data = data;
    }

    public List<WrappedAxisAlignedBB> boundingBoxes() {
      return boundingBoxes;
    }

    public Material type() {
      return type;
    }

    public int data() {
      return data;
    }

    public boolean expired() {
      return AccessHelper.now() - creation > 10000;
    }
  }
}
