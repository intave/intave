package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import net.minecraft.server.v1_13_R2.Chunk;
import net.minecraft.server.v1_13_R2.IBlockData;
import net.minecraft.server.v1_13_R2.Item;
import net.minecraft.server.v1_13_R2.WorldServer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v13BlockAccessor implements BlockAccessor {
  @Override
  public Material typeOf(Block block) {
    return block.getType();
  }

  @Override
  public int variantOf(Block block) {
    return block.getData();
  }

  @Override
  public Object blockHandle(Block block) {
    return ((CraftBlock) block).getNMS();
  }

    @Override
  @PatchyAutoTranslation
  public float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) player.getWorld()).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return 0.0f;
    }
    IBlockData blockData = chunk.getBlockData(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    return blockData.getBlock().getDamage(blockData, ((CraftPlayer) player).getHandle(), worldServer, new net.minecraft.server.v1_13_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ()));
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return false;
    }
    User user = UserRepository.userOf(player);
    int heldItemType = user.meta().inventory().handSlot();
    IBlockData blockData = chunk.getBlockData(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    Item heldItem = ((CraftPlayer) player).getHandle().inventory.getItem(heldItemType).getItem();
    return blockData.getMaterial().isReplaceable() && blockData.getBlock().getItem().getItem() == heldItem;
  }
}
