package de.jpx3.intave.block.access;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.metadata.MetadataValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.world.WorldHeight.LOWER_WORLD_LIMIT;

@Relocate
public final class VolatileBlockAccess {
  public static void setup() {
  }

  public static Block fakeBlockAccess(User user, Location location) {
    int blockX = location.getBlockX();
    int blockY = location.getBlockY();
    int blockZ = location.getBlockZ();
    return new FakeFallbackBlock(location.getWorld()) {
      @Override
      public Material getType() {
        return typeAccess(user, blockX, blockY, blockZ);
//        return BlockTypeAccess.typeAccess(this);
      }

      @Override
      @Deprecated
      public int getTypeId() {
        return getType().getId();
      }

      @Override
      public int getX() {
        return blockX;
      }

      @Override
      public int getY() {
        return blockY;
      }

      @Override
      public int getZ() {
        return blockZ;
      }

      @Override
      public Block getRelative(int x, int y, int z) {
        return fakeBlockAccess(user, getLocation().clone().add(x, y, z));
      }

      @Override
      public Block getRelative(BlockFace blockFace) {
        return getRelative(blockFace, 1);
      }

      @Override
      public Block getRelative(BlockFace blockFace, int length) {
        return fakeBlockAccess(user, getLocation().clone().add(blockFace.getModX() * length, blockFace.getModY() * length, blockFace.getModZ() * length));
      }

      @Override
      public boolean hasMetadata(String key) {
        return blockAccess(getLocation()).hasMetadata(key);
      }

      @Override
      public List<MetadataValue> getMetadata(String key) {
        return blockAccess(getLocation()).getMetadata(key);
      }
    };
  }

  public static Block blockAccess(Location location) {
    return blockAccess(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Block blockAccess(World blockAccess, BlockPosition position) {
    return blockAccess(blockAccess, position.xCoord, position.yCoord, position.zCoord);
  }

  public static Block blockAccess(World blockAccess, double x, double y, double z) {
    return blockAccess(blockAccess, floor(x), floor(y), floor(z));
  }

  public static Block blockAccess(World blockAccess, int x, int y, int z) {
    if (isInLoadedChunk(blockAccess, x, z) || Bukkit.isPrimaryThread()) {
      try {
        return blockAccess.getBlockAt(x, y, z);
      } catch (IllegalStateException exception) {
        // problems with async chunk loading
        exception.printStackTrace();
        return fallbackBlock(blockAccess);
      }
    }
    return fallbackBlock(blockAccess);
  }

  private static final Map<World, Block> EMERGENCY_FALLBACK_BLOCKS = GarbageCollector.watch(new HashMap<>());

  private static Block fallbackBlock(World world) {
    Location spawnLocation = world.getSpawnLocation();
    if (!world.isChunkLoaded(spawnLocation.getBlockX(), spawnLocation.getBlockZ())) {
      // too expensive
//      try {
//        Chunk[] loadedChunks = world.getLoadedChunks();
//        if (loadedChunks.length > 0) {
//          Chunk anyChunk = loadedChunks[0];
//          return world.getBlockAt(anyChunk.getX() << 4, LOWER_WORLD_LIMIT - 1, anyChunk.getZ() << 4);
//        }
//      } catch (ConcurrentModificationException ignored) {}
      return EMERGENCY_FALLBACK_BLOCKS.computeIfAbsent(world, FakeFallbackBlock::new);
    }
    return world.getBlockAt(spawnLocation.getBlockX(), LOWER_WORLD_LIMIT - 1, spawnLocation.getBlockZ());
  }


  public static Fluid fluidAccess(User user, Location location) {
    return fluidAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Fluid fluidAccess(User user, BlockPosition position) {
    return fluidAccess(user, user.player().getWorld(), position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static Fluid fluidAccess(User user, World blockAccess, double x, double y, double z) {
    return fluidAccess(user, blockAccess, floor(x), floor(y), floor(z));
  }

  public static Fluid fluidAccess(User user, Position lastPosition) {
    return fluidAccess(user, user.player().getWorld(), lastPosition.getBlockX(), lastPosition.getBlockY(), lastPosition.getBlockZ());
  }

  public static Fluid fluidAccess(User user, World world, int blockX, int blockY, int blockZ) {
    Material type = typeAccess(user, world, blockX, blockY, blockZ);
    int variantIndex = variantIndexAccess(user, world, blockX, blockY, blockZ);
    return Fluids.fluidStateOf(type, variantIndex);
  }

  public static Material typeAccess(User user, Location location) {
    return typeAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Material typeAccess(User user, BlockPosition position) {
    return typeAccess(user, user.player().getWorld(), position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static Material typeAccess(User user, World world, Position position) {
    return typeAccess(user, world, position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static Material typeAccess(User user, int blockX, int blockY, int blockZ) {
    World world = user.player().getWorld();
    return typeAccess(user, world, blockX, blockY, blockZ);
  }

  public static Material typeAccess(User user, World blockAccess, double x, double y, double z) {
    return typeAccess(user, blockAccess, floor(x), floor(y), floor(z));
  }

  public static Material typeAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    if (blockAccess == null || isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockStates().typeAt(blockX, blockY, blockZ);
    }
    return Material.AIR;
  }

  public static BlockVariant variantAccess(User user, Location location) {
    return variantAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static BlockVariant variantAccess(User user, BlockPosition position) {
    return variantAccess(user, user.player().getWorld(), position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static BlockVariant variantAccess(User user, World blockAccess, double x, double y, double z) {
    return variantAccess(user, blockAccess, floor(x), floor(y), floor(z));
  }

  public static BlockVariant variantAccess(User user, Position lastPosition) {
    return variantAccess(user, user.player().getWorld(), lastPosition.getBlockX(), lastPosition.getBlockY(), lastPosition.getBlockZ());
  }

  public static BlockVariant variantAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    Material type = typeAccess(user, blockAccess, blockX, blockY, blockZ);
    int variantIndex = variantIndexAccess(user, blockAccess, blockX, blockY, blockZ);
    return BlockVariantRegister.variantOf(type, variantIndex);
  }

  public static int variantIndexAccess(User user, Position position) {
    return variantIndexAccess(user, user.player().getWorld(), position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static int variantIndexAccess(User user, Location location) {
    return variantIndexAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static int variantIndexAccess(User user, BlockPosition position) {
    return variantIndexAccess(user, user.player().getWorld(), position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static int variantIndexAccess(User user, World blockAccess, double x, double y, double z) {
    return variantIndexAccess(user, blockAccess, floor(x), floor(y), floor(z));
  }

  public static int variantIndexAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockStates().variantIndexAt(blockX, blockY, blockZ);
    }
    return 0;
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < (double) i ? i - 1 : i;
  }

  public static boolean isInLoadedChunk(World world, int x, int z) {
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    return world.isChunkLoaded(chunkX, chunkZ) &&
      world.isChunkInUse(chunkX, chunkZ);
  }
}