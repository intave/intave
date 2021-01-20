package de.jpx3.intave.world.collision;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.patches.BoundingBoxPatcher;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BoundingBoxAccess {
  private final static BoundingBoxResolver globalBoundingBoxResolver;
  static {
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.world.collision.LegacyBoundingBoxResolver");
    globalBoundingBoxResolver = new LegacyBoundingBoxResolver();
  }

  private final Player player;
  private final Map<Integer, List<WrappedAxisAlignedBB>> blockCache = new HashMap<>(4096);

  private final Map<Integer, List<WrappedAxisAlignedBB>> localReplacements = new HashMap<>(16);
  private final Map<Location, List<WrappedAxisAlignedBB>> globalReplacements = new HashMap<>(64);

  private WeakReference<Chunk> activeChunk = new WeakReference<>(null);
  private int chunkXPos;
  private int chunkZPos;

  public BoundingBoxAccess(Player player) {
    this.player = player;
  }

  public List<WrappedAxisAlignedBB> resolve(Chunk chunk, int posX, int posY, int posZ) {
    if(posY < 0 || 255 < posY) {
      posY = 256;
    }

    Chunk chunkX = activeChunk.get();
    if(chunkX == null || (chunk.getX() != chunkX.getX() || chunk.getZ() != chunkX.getZ())) {
      translateLocalReplacements(chunk.getWorld());
      activeChunk = new WeakReference<>(chunk);
      chunkXPos = chunk.getX() << 4;
      chunkZPos = chunk.getZ() << 4;
      blockCache.clear();
    }

    byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
    int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);

    // local replacements (fast access)
    if(!localReplacements.isEmpty()) {
      List<WrappedAxisAlignedBB> replacementBlock = localReplacements.get(blockPositionKey);
      if(replacementBlock != null) {
        return replacementBlock;
      }
    }

    // global replacements (escape current-chunk constrain)
    if(!globalReplacements.isEmpty()) {
      for (Location location : globalReplacements.keySet()) {
        if(location.getX() == posX && location.getZ() == posZ && location.getY() == posY) {
          return globalReplacements.get(location);
        }
      }
    }

    List<WrappedAxisAlignedBB> boundingBoxes = blockCache.get(blockPositionKey);
    if(boundingBoxes == null) {
      World world = chunk.getWorld();
      boundingBoxes = BoundingBoxPatcher.patch(
        world, player,
        BlockAccessor.blockAccess(world, posX, posY, posZ),
        globalBoundingBoxResolver.resolve(world, posX, posY, posZ)
      );
      blockCache.put(blockPositionKey, boundingBoxes);
    }
    return boundingBoxes;
  }

  private void translateLocalReplacements(World world) {
    if(localReplacements.isEmpty()) {
      return;
    }
    for (Map.Entry<Integer, List<WrappedAxisAlignedBB>> entry : localReplacements.entrySet()) {
      int blockPositionKey = entry.getKey();
      int posY = (blockPositionKey >> 16) & 0x1FF;
      int posX = chunkXPos + ((blockPositionKey >> 8) & 0xFF);
      int posZ = chunkZPos + ((blockPositionKey >> 0) & 0xFF);
      globalReplacements.put(new Location(world, posX, posY, posZ), entry.getValue());
    }
    localReplacements.clear();
  }

  public void identityInvalidate() {
    invalidate();
    localReplacements.clear();
    globalReplacements.clear();
  }

  public void invalidate() {
    blockCache.clear();
  }

  public void invalidate(int posX, int posY, int posZ) {
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
    List<WrappedAxisAlignedBB> boundingBoxes = BoundingBoxPatcher.patch(
      world, player,
      typeId, blockState,
      globalBoundingBoxResolver.resolve(world, posX, posY, posZ, typeId, blockState)
    );
    int chunkX = this.chunkXPos;
    int chunkZ = this.chunkZPos;
    boolean useLocalList = posX >= chunkX && posZ >= chunkZ && chunkX + 16 > posX && chunkZ + 16 > posZ;
    if(useLocalList) {
      byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
      int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);
      localReplacements.put(blockPositionKey, boundingBoxes);
    } else {
      globalReplacements.put(new Location(world, posX, posY, posZ), boundingBoxes);
    }
  }

  public void invalidateOverride(World world, int posX, int posY, int posZ) {
    int chunkX = this.chunkXPos;
    int chunkZ = this.chunkZPos;
    boolean useLocalList = posX >= chunkX && posZ >= chunkZ && chunkX + 16 > posX && chunkZ + 16 > posZ;
    if(useLocalList) {
      byte dx = (byte) (chunkXPos - posX), dz = (byte) (chunkZPos - posZ);
      int blockPositionKey = (posY & 0x1FF) << 16 | (dx & 0x0FF) << 8 | (dz & 0x0FF);
      localReplacements.remove(blockPositionKey);
    }
    globalReplacements.remove(new Location(world, posX, posY, posZ));
  }
}
