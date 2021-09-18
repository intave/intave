package de.jpx3.intave.block.shape.pipe.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.List;

@PatchyAutoTranslation
public final class v17b1ShapeDrill extends AbstractShapeDrill {
  @Override
  @PatchyAutoTranslation
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawBlockDataOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.empty();
    }
    IBlockAccess blockAccess = handle.getChunkProvider().c(posX >> 4, posZ >> 4);
    if (blockAccess == null) {
      return BlockShapes.empty();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(blockAccess, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.toList();
    return translateWithOffset(nativeBoxes, posX, posY, posZ);
  }
}