package de.jpx3.intave.world.permission;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

  public static void setup() {
    blockPlacePermissionCheck = new EventPlacePermissionResolver();
    blockBreakPermissionCheck = new EventBreakPermissionResolver();
    bucketActionPermissionCheck = new EventBukkitActionPermissionResolver();
  }

  public static boolean blockPlacePermission(
    Player player, World world, boolean mainHand, int blockX, int blockY, int blockZ, int enumDirection, Material type, int variant
  ) {
    if (IntaveControl.DISALLOW_ALL_BLOCK_PLACEMENTS) {
//      Synchronizer.synchronize(() -> {
//        player.sendMessage(ChatColor.GRAY + "Place of " + MathHelper.formatPosition(new Location(world, blockX, blockY, blockZ)) + " type "+type+"/"+variant+" with "+(mainHand ?"main":"off")+"hand facing "+enumDirection+" is denied");
//      });
      return false;
    }
    boolean permission = blockPlacePermissionCheck.hasPermission(player, world, mainHand, blockX, blockY, blockZ, enumDirection, type, variant);
    if (IntaveControl.DEBUG_PLACE_AND_BREAK_PERMISSIONS) {
      Synchronizer.synchronize(() -> {
        player.sendMessage(ChatColor.GRAY + "Place of " + MathHelper.formatPosition(new Location(world, blockX, blockY, blockZ)) + " is " + (permission ? "allowed" : "denied"));
      });
    }
    return permission;
  }

  public static boolean blockBreakPermission(
    Player player, Block block
  ) {
    boolean permission = blockBreakPermissionCheck.hasPermission(player, block);
    if (IntaveControl.DEBUG_PLACE_AND_BREAK_PERMISSIONS) {
      Synchronizer.synchronize(() -> {
        player.sendMessage(ChatColor.GRAY + "Break of " + MathHelper.formatPosition(block.getLocation()) + " is " + (permission ? "allowed" : "denied"));
      });
    }
    return permission;
  }

  public static boolean bukkitActionPermission(
    Player player, BucketAction bucketAction, Block blockClicked, BlockFace blockFace, Material bucket, ItemStack itemInHand
  ) {
    return bucketActionPermissionCheck.hasPermission(player, bucketAction, blockClicked, blockFace, bucket, itemInHand);
  }
}
