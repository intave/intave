package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class WorldPermission {
  private static BlockPlacePermissionCheck blockPlacePermissionCheck;
  private static BlockBreakPermissionCheck blockBreakPermissionCheck;
  private static BucketActionPermissionCheck bucketActionPermissionCheck;

  public static void setup(IntavePlugin plugin) {
    blockPlacePermissionCheck = new EventPlacePermissionResolver(plugin);
    blockBreakPermissionCheck = new EventBreakPermissionResolver(plugin);
    bucketActionPermissionCheck = new EventBukkitActionPermissionResolver(plugin);
  }

  public static boolean blockPlacePermission(
    Player player, World world, boolean mainHand, int blockX, int blockY, int blockZ, int enumDirection, Material type, byte data
  ) {
    return blockPlacePermissionCheck.hasPermission(player, world, mainHand, blockX, blockY, blockZ, enumDirection, type, data);
  }

  public static boolean blockBreakPermission(
    Player player, Block block
  ) {
    return blockBreakPermissionCheck.hasPermission(player, block);
  }

  public static boolean bukkitActionPermission(
    Player player, BucketAction bucketAction, Block blockClicked, BlockFace blockFace, Material bucket, ItemStack itemInHand
  ) {
    return bucketActionPermissionCheck.hasPermission(player, bucketAction, blockClicked, blockFace, bucket, itemInHand);
  }
}
