package de.jpx3.intave.world.permission;

import de.jpx3.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.access.BlockPlacePermissionCheck;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.event.CraftEventFactory;
import org.bukkit.entity.Player;

public final class CraftBukkitPlacePermissionResolver extends BlockPlacePermissionCheck {
  @Override
  @PatchyAutoTranslation
  public boolean hasPermission(Player player, World world, int blockX, int blockY, int blockZ, int typeId, byte data) {
    if(world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
      CraftChunk chunk = (CraftChunk) world.getChunkAt(blockX >> 4, blockZ >> 4);
      CraftBlockState replacedBlockState = new CraftBlockState(new CustomCraftBlock(chunk, blockX, blockY, blockZ, typeId, data));
      return CraftEventFactory.callBlockPlaceEvent(
        ((CraftWorld) world).getHandle(),
        ((CraftPlayer) player).getHandle(),
        replacedBlockState,
        blockX,
        blockY,
        blockZ
      ).isCancelled();
    }
    return false;
  }
}