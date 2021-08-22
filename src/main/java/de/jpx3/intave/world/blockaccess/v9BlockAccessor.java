package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_9_R2.Chunk;
import net.minecraft.server.v1_9_R2.WorldServer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_9_R2.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@PatchyAutoTranslation
public final class v9BlockAccessor implements BlockAccessor {
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
    return CraftMagicNumbers.getBlock(block);
  }

  @Override
  @PatchyAutoTranslation
  public float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) player.getWorld()).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return 0.0f;
    }
    net.minecraft.server.v1_9_R2.BlockPosition blockposition = new net.minecraft.server.v1_9_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    return chunk.getBlockData(blockposition).getBlock().getDamage(chunk.getBlockData(blockposition), ((CraftPlayer) player).getHandle(), worldServer, blockposition);
  }

  @Override
  @PatchyAutoTranslation
  public boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Chunk chunk = worldServer.getChunkIfLoaded(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    if (chunk == null) {
      return false;
    }
    net.minecraft.server.v1_9_R2.BlockPosition blockposition = new net.minecraft.server.v1_9_R2.BlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    return chunk.getBlockData(blockposition).getBlock().a(worldServer, blockposition);
  }
}
