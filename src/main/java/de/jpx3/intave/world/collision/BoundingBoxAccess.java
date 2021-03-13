package de.jpx3.intave.world.collision;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.IntaveControl.DISABLE_BLOCK_CACHING_ENTIRELY;

public final class BoundingBoxAccess {
  private static BoundingBoxResolver globalBoundingBoxResolver;

  public static void setup() {
    String className = "de.jpx3.intave.world.collision.resolver.v8BoundingBoxResolver";
    String acClass = "de.jpx3.intave.world.collision.resolver.ac.v8AlwaysCollidingBoundingBox";

    if (ProtocolLibAdapter.COMBAT_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.collision.resolver.v9BoundingBoxResolver";
      acClass = "de.jpx3.intave.world.collision.resolver.ac.v9AlwaysCollidingBoundingBox";
    }

    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), acClass);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    globalBoundingBoxResolver = instanceOf(className);
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
  private final Map<Location, CacheEntry> globalReplacements = new ConcurrentHashMap<>(64);

  private WeakReference<Chunk> activeChunk = new WeakReference<>(null);
  private int chunkXPos;
  private int chunkZPos;

  public BoundingBoxAccess(Player player) {
    this.player = player;
  }

  public List<WrappedAxisAlignedBB> resolve(Chunk chunk, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    Chunk chunkX = activeChunk.get();
    if (chunkX == null || (chunk.getX() != chunkX.getX() || chunk.getZ() != chunkX.getZ())) {
      activeChunk = new WeakReference<>(chunk);
      chunkXPos = chunk.getX() << 4;
      chunkZPos = chunk.getZ() << 4;
      blockCache.clear();
    }

    byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    // global replacements (escape current-chunk constrain)
    if (!globalReplacements.isEmpty()) {
      for (Location location : globalReplacements.keySet()) {
        if (location.getX() == posX && location.getZ() == posZ && location.getY() == posY) {
          CacheEntry cacheEntry = globalReplacements.get(location);
          if(cacheEntry != null) {
            return cacheEntry.boundingBoxes();
          }
        }
      }
    }

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      List<WrappedAxisAlignedBB> boundingBoxes;
      World world = chunk.getWorld();
      Block block = BlockAccessor.blockAccess(world, posX, posY, posZ);
      boundingBoxes = BoundingBoxPatcher.patch(
        world, player,
        block,
        globalBoundingBoxResolver.resolve(world, posX, posY, posZ)
      );
      cacheEntry = new CacheEntry(boundingBoxes, block.getType(), block.getData());
      if (!DISABLE_BLOCK_CACHING_ENTIRELY) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.boundingBoxes();
  }

  public Material resolveType(Chunk chunk, int posX, int posY, int posZ) {
    if (posY < 0 || 255 < posY) {
      posY = 256;
    }

    Chunk chunkX = activeChunk.get();
    if (chunkX == null || (chunk.getX() != chunkX.getX() || chunk.getZ() != chunkX.getZ())) {
      activeChunk = new WeakReference<>(chunk);
      chunkXPos = chunk.getX() << 4;
      chunkZPos = chunk.getZ() << 4;
      blockCache.clear();
    }

    byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    // global replacements (escape current-chunk constrain)
    if (!globalReplacements.isEmpty()) {
      for (Location location : globalReplacements.keySet()) {
        if (location.getX() == posX && location.getZ() == posZ && location.getY() == posY) {
          return globalReplacements.get(location).type();
        }
      }
    }

    CacheEntry cacheEntry = blockCache.get(blockPositionKey);
    if (cacheEntry == null) {
      List<WrappedAxisAlignedBB> boundingBoxes;
      World world = chunk.getWorld();
      Block block = BlockAccessor.blockAccess(world, posX, posY, posZ);
      boundingBoxes = BoundingBoxPatcher.patch(
        world, player,
        block,
        globalBoundingBoxResolver.resolve(world, posX, posY, posZ)
      );
      cacheEntry = new CacheEntry(boundingBoxes, block.getType(), block.getData());
      if (!DISABLE_BLOCK_CACHING_ENTIRELY) {
        blockCache.put(blockPositionKey, cacheEntry);
      }
    }
    return cacheEntry.type();
  }

  public void identityInvalidate() {
    invalidate();
    globalReplacements.clear();
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

  public void override(World world, int posX, int posY, int posZ, int typeId, int blockState) {
    List<WrappedAxisAlignedBB> boundingBoxes = constructBlock(world, posX, posY, posZ, typeId, blockState);
    CacheEntry cacheEntry = new CacheEntry(boundingBoxes, Material.getMaterial(typeId), blockState);
    globalReplacements.put(new Location(world, posX, posY, posZ), cacheEntry);
  }

  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    // global replacements (escape current-chunk constrain)
    if (!globalReplacements.isEmpty()) {
      for (Location location : globalReplacements.keySet()) {
        if (location.getBlockX() == posX && location.getBlockZ() == posZ && location.getBlockY() == posY) {
          return true;
        }
      }
    }
    return false;
  }

  public CacheEntry overrideOf(int posX, int posY, int posZ) {
    if (!globalReplacements.isEmpty()) {
      for (Map.Entry<Location, CacheEntry> entry : globalReplacements.entrySet()) {
        Location location = entry.getKey();
        if (location.getBlockX() == posX && location.getBlockZ() == posZ && location.getBlockY() == posY) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  public List<WrappedAxisAlignedBB> constructBlock(World world, int posX, int posY, int posZ, int typeId, int blockState) {
    return BoundingBoxPatcher.patch(
      world, player,
      posX, posY, posZ,
      typeId, blockState,
      globalBoundingBoxResolver.resolve(world, posX, posY, posZ, typeId, blockState)
    );
  }

  public void invalidateOverride(World world, int posX, int posY, int posZ) {
    globalReplacements.remove(new Location(world, posX, posY, posZ));
  }

  public static class CacheEntry {
    private final List<WrappedAxisAlignedBB> boundingBoxes;
    private final Material type;
    private final int data;

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
  }
}
