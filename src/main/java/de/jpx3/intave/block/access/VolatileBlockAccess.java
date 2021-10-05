package de.jpx3.intave.block.access;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.shade.BlockPosition;
import de.jpx3.intave.shade.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

@Relocate
public final class VolatileBlockAccess {
  public static void setup() {
  }

  public static Block blockAccess(Location location) {
    return blockAccess(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Block blockAccess(World blockAccess, double x, double y, double z) {
    return blockAccess(blockAccess, floor(x), floor(y), floor(z));
  }

  public static Block blockAccess(World blockAccess, BlockPosition position) {
    return blockAccess(blockAccess, position.xCoord, position.yCoord, position.zCoord);
  }

  public static Block blockAccess(World blockAccess, int x, int y, int z) {
    if (isInLoadedChunk(blockAccess, x, z) || Bukkit.isPrimaryThread()) {
      return blockAccess.getBlockAt(x, y, z);
    }
    return fallbackBlock(blockAccess);
  }

  public static Material typeAccess(User user, Location location) {
    return typeAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Material typeAccess(User user, Position position) {
    return typeAccess(user, user.player().getWorld(), position.blockX(), position.blockY(), position.blockZ());
  }

  public static Material typeAccess(User user, int blockX, int blockY, int blockZ) {
    World world = user.player().getWorld();
    return typeAccess(user, world, blockX, blockY, blockZ);
  }

  public static Material typeAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockStates().typeAt(blockX, blockY, blockZ);
    }
    return Material.AIR;
  }

  public static Material typeAccess(User user, World blockAccess, double x, double y, double z) {
    int blockX = floor(x);
    int blockY = floor(y);
    int blockZ = floor(z);
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockStates().typeAt(blockX, blockY, blockZ);
    }
    return Material.AIR;
  }

  public static BlockVariant variantAccess(User user, Location location) {
    return variantAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static BlockVariant variantAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    Material type = typeAccess(user, blockAccess, blockX, blockY, blockZ);
    int variantIndex = variantIndexAccess(user, blockAccess, blockX, blockY, blockZ);
    return BlockVariantRegister.variantOf(type, variantIndex);
  }

  public static int variantIndexAccess(User user, Position position) {
    return variantIndexAccess(user, user.player().getWorld(), position.blockX(), position.blockY(), position.blockZ());
  }

  public static int variantIndexAccess(User user, Location location) {
    return variantIndexAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
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

  private static Block fallbackBlock(World world) {
    Location spawnLocation = world.getSpawnLocation();
    return world.getBlockAt(spawnLocation.getBlockX(), -1, spawnLocation.getBlockZ());
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < (double) i ? i - 1 : i;
  }

  public static boolean isInLoadedChunk(World world, int x, int z) {
    return world.isChunkLoaded(x >> 4, z >> 4);
  }
}