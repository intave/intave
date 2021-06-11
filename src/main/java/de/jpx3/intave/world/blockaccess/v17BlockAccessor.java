package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v17BlockAccessor implements BlockAccessor {
  @Override
  @PatchyAutoTranslation
  public float blockDamage(Player player, ItemStack itemInHand, BlockPosition nativeBlockPosition) {
//    WorldServer worldServer = ((CraftWorld) player.getWorld()).getHandle();
//    Chunk chunk = worldServer.getChunkIfLoaded(nativeBlockPosition.getX() >> 4, nativeBlockPosition.getZ() >> 4);
//    if (chunk == null) {
//      return 0.0f;
//    }
//    net.minecraft.core.BlockPosition blockPosition = new net.minecraft.core.BlockPosition(nativeBlockPosition.getX(), nativeBlockPosition.getY(), nativeBlockPosition.getZ());
//    IBlockData blockData = chunk.getType(blockPosition);
//    return blockData.getBlock().getDamage(blockData, ((CraftPlayer) player).getHandle(), worldServer, blockPosition);
    return 0.0f;
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition nativeBlockPosition) {
//    WorldServer worldServer = ((CraftWorld) world).getHandle();
//    Chunk chunk = worldServer.getChunkIfLoaded(nativeBlockPosition.getX() >> 4, nativeBlockPosition.getZ() >> 4);
//    if (chunk == null) {
//      return false;
//    }
//    User user = UserRepository.userOf(player);
//    int heldItemType = user.meta().inventoryData().handSlot();
//    net.minecraft.core.BlockPosition blockPosition = new net.minecraft.core.BlockPosition(nativeBlockPosition.getX(), nativeBlockPosition.getY(), nativeBlockPosition.getZ());
//    IBlockData blockData = chunk.getType(blockPosition);
//    Item heldItem = ((CraftPlayer) player).getHandle().getInventory().getItem(heldItemType).getItem();
//    return blockData.getMaterial().isReplaceable() && blockData.getBlock().getItem().getItem() == heldItem;
    return false;
  }
}
