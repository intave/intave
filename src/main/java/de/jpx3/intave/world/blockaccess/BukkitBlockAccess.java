package de.jpx3.intave.world.blockaccess;

import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

@Relocate
public final class BukkitBlockAccess implements BukkitEventSubscriber {
  public static void setup() {
//    IntavePlugin.singletonInstance().eventLinker().registerEventsIn(new BukkitBlockAccess());
  }

  public static Block blockAccess(Location location) {
    return blockAccess(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Block blockAccess(World blockAccess, int x, int y, int z) {
    if (isInLoadedChunk(blockAccess, x, z) || Bukkit.isPrimaryThread()) {
      return blockAccess.getBlockAt(x, y, z);
    }
    return fallbackBlock(blockAccess);
  }

  public static Material cacheAppliedTypeAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockShapeAccess().resolveType(blockX >> 4, blockZ >> 4, blockX, blockY, blockZ);
    }
    return Material.AIR;
  }

  public static Material cacheAppliedTypeAccess(User user, World blockAccess, double x, double y, double z) {
    int blockX = WrappedMathHelper.floor(x);
    int blockY = WrappedMathHelper.floor(y);
    int blockZ = WrappedMathHelper.floor(z);
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockShapeAccess().resolveType(blockX >> 4, blockZ >> 4,blockX, blockY, blockZ);
    }
    return Material.AIR;
  }

  public static int cacheAppliedDataAccess(User user, World blockAccess, int blockX, int blockY, int blockZ) {
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockShapeAccess().resolveData(blockX >> 4, blockZ >> 4, blockX, blockY, blockZ);
    }
    return 0;
  }

  public static int cacheAppliedDataAccess(User user, World blockAccess, double x, double y, double z) {
    int blockX = WrappedMathHelper.floor(x);
    int blockY = WrappedMathHelper.floor(y);
    int blockZ = WrappedMathHelper.floor(z);
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.blockShapeAccess().resolveData(blockX >> 4, blockZ >> 4, blockX, blockY, blockZ);
    }
    return BlockDataAccess.dataAccess(fallbackBlock(blockAccess));
  }

  private static Block fallbackBlock(World world) {
    Location spawnLocation = world.getSpawnLocation();
    return world.getBlockAt(spawnLocation.getBlockX(), -1, spawnLocation.getBlockZ());
  }

  public static Material cacheAppliedTypeAccess(User user, Location location) {
    return cacheAppliedTypeAccess(user, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Block blockAccess(World blockAccess, double x, double y, double z) {
    return blockAccess(blockAccess, WrappedMathHelper.floor(x), WrappedMathHelper.floor(y),WrappedMathHelper.floor(z));
  }

  public static Block blockAccess(World blockAccess, WrappedBlockPosition position) {
    return blockAccess(blockAccess, position.xCoord, position.yCoord, position.zCoord);
  }

  public static boolean isInLoadedChunk(World world, int x, int z) {
    return world.isChunkLoaded(x >> 4, z >> 4);
  }
}