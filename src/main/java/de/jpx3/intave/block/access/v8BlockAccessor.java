package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v8BlockAccessor implements BlockAccessor {
  @Override
  public Material typeOf(Block block) {
    return block.getType();
  }

  @Override
  public int variantIndexOf(Block block) {
    return block.getData();
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantOf(Block block) {
    return CraftMagicNumbers.getBlock(block).getBlockData();
  }

  @Override
  @PatchyAutoTranslation
  public Object nativeVariantBy(int blockId) {
    return net.minecraft.server.v1_8_R3.Block.getById(blockId);
  }

  @Override
  @PatchyAutoTranslation
  public float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) player.getWorld()).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return 0.0f;
    }
    net.minecraft.server.v1_8_R3.BlockPosition blockposition = new net.minecraft.server.v1_8_R3.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    return chunk.getBlockData(blockposition).getBlock().getDamage(((CraftPlayer) player).getHandle(), worldServer, blockposition);
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return false;
    }
    net.minecraft.server.v1_8_R3.BlockPosition blockposition = new net.minecraft.server.v1_8_R3.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    return chunk.getBlockData(blockposition).getBlock().a(worldServer, blockposition);
  }
}
