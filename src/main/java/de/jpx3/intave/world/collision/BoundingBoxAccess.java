package de.jpx3.intave.world.collision;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
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
  private final static CacheEntry EMPTY_CACHE_ENTRY = new CacheEntry(Collections.emptyList(), Material.AIR, 0);
  private final static int REQUIRED_CHUNK_RESETS_FOR_FREQUENCY_SWITCH = Integer.MAX_VALUE;// 4 default, but currently deactivated;
  private static BoundingBoxResolver globalBoundingBoxResolver;

  public static void setup() {
    // ugly, the way ZKM likes it

    String className = "de.jpx3.intave.world.collision.resolver.v8BoundingBoxResolver";
    String acClass = "de.jpx3.intave.world.collision.resolver.ac.v8AlwaysCollidingBoundingBox";

    if(ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v13BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v13AlwaysCollidingBoundingBox";
    } else if(ProtocolLibAdapter.COLOR_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v12BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v12AlwaysCollidingBoundingBox";
    } else if(ProtocolLibAdapter.EXPLORATION_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v11BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v11AlwaysCollidingBoundingBox";
    } else if (ProtocolLibAdapter.COMBAT_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v9BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v9AlwaysCollidingBoundingBox";
    }

    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), acClass);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    globalBoundingBoxResolver = new CubicBoundingBoxResolverFilter(instanceOf(className));
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
  private final Map<Location, CacheEntry> locatedReplacements = new ConcurrentHashMap<>(64);
  private final Map<Long, CacheEntry> indexedReplacements = new ConcurrentHashMap<>(64);

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

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();
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

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
//      player.sendMessage(Integer.toBinaryString(posX) + /*" " + posY + " " + posZ + " " */" "+ Long.toBinaryString(bigKey(posX, posY, posZ)));
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      Material type = block.getType();
      if(type == Material.AIR) {
        cacheEntry = EMPTY_CACHE_ENTRY;
      } else {
        List<WrappedAxisAlignedBB> boundingBoxes = globalBoundingBoxResolver.resolve(world, type, posX, posY, posZ);
        boundingBoxes = BoundingBoxPatcher.patch(world, player, block, boundingBoxes);
        cacheEntry = new CacheEntry(boundingBoxes, type, block.getData());
      }
      if (!DISABLE_BLOCK_CACHING_ENTIRELY) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.boundingBoxes();
  }

  public Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();
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

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      Material type = block.getType();
      if(type == Material.AIR) {
        cacheEntry = EMPTY_CACHE_ENTRY;
      } else {
        List<WrappedAxisAlignedBB> boundingBoxes = globalBoundingBoxResolver.resolve(world, type, posX, posY, posZ);
        boundingBoxes = BoundingBoxPatcher.patch(world, player, block, boundingBoxes);
        cacheEntry = new CacheEntry(boundingBoxes, type, block.getData());
      }
      if (!DISABLE_BLOCK_CACHING_ENTIRELY) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.type();
  }

  public int resolveData(int chunkX, int chunkZ, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    if ((chunkX != this.chunkX || chunkZ != this.chunkZ)) {
      this.chunkXPos = (chunkX) << 4;
      this.chunkZPos = (chunkZ) << 4;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      blockCache.clear();
      purgeOverrides();
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

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      World world = player.getWorld();
      Block block = BukkitBlockAccess.blockAccess(world, posX, posY, posZ);
      Material type = block.getType();
      if(type == Material.AIR) {
        cacheEntry = EMPTY_CACHE_ENTRY;
      } else {
        List<WrappedAxisAlignedBB> boundingBoxes;
        boundingBoxes = globalBoundingBoxResolver.resolve(world, type, posX, posY, posZ);
        boundingBoxes = BoundingBoxPatcher.patch(world, player, block, boundingBoxes);
        cacheEntry = new CacheEntry(boundingBoxes, type, block.getData());
      }
      if (!DISABLE_BLOCK_CACHING_ENTIRELY) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.data();
  }

  public void identityInvalidate() {
    invalidate();
    locatedReplacements.clear();
    indexedReplacements.clear();
  }

  public void invalidate() {
    blockCache.clear();
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
    return (long) (posX & 0b111111111111111111111111111) << 38 | (long) posY << 30 | (posZ & 0b111111111111111111111111111);
  }

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
