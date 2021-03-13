package de.jpx3.intave.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Map;
import java.util.WeakHashMap;

@Relocate
public final class BlockAccessor implements BukkitEventSubscriber {
  private static final Map<World, Block> invalidRequestBlockMap = new WeakHashMap<>();

  @BukkitEventSubscription
  public void onWorldLoad(WorldLoadEvent event) {
    World world = event.getWorld();
    Block block = world.getBlockAt(0, -1, 0);
    invalidRequestBlockMap.put(world, block);
  }

  public static void setup() {
    Bukkit.getWorlds().forEach(world -> invalidRequestBlockMap.put(world, world.getBlockAt(0, -1, 0)));
    IntavePlugin.singletonInstance().eventLinker().registerEventsIn(new BlockAccessor());
  }

  public static Block blockAccess(Location location) {
    return blockAccess(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Block blockAccess(World blockAccess, int x, int y, int z) {
    if (isInLoadedChunk(blockAccess, x, z) || Bukkit.isPrimaryThread()) {
      return blockAccess.getBlockAt(x, y, z);
    }
    return invalidRequestBlockMap.get(blockAccess);
  }

  public static Material cacheAppliedTypeAccess(User user, World blockAccess, int x, int y, int z) {
    if (isInLoadedChunk(blockAccess, x, z) || Bukkit.isPrimaryThread()) {
      return user.boundingBoxAccess().resolveType(blockAccess.getChunkAt(x >> 4, z >> 4), x, y, z);
    }
    return invalidRequestBlockMap.get(blockAccess).getType();
  }

  public static Material cacheAppliedTypeAccess(User user, World blockAccess, double x, double y, double z) {
    int blockX = WrappedMathHelper.floor(x);
    int blockY = WrappedMathHelper.floor(y);
    int blockZ = WrappedMathHelper.floor(z);
    if (isInLoadedChunk(blockAccess, blockX, blockZ) || Bukkit.isPrimaryThread()) {
      return user.boundingBoxAccess().resolveType(blockAccess.getChunkAt(blockX >> 4, blockZ >> 4), blockX, blockY, blockZ);
    }
    return invalidRequestBlockMap.get(blockAccess).getType();
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