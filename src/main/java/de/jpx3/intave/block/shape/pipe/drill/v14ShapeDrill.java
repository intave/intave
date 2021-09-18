package de.jpx3.intave.block.shape.pipe.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.List;

@PatchyAutoTranslation
public final class v14ShapeDrill extends AbstractShapeDrill {
  @Override
  @PatchyAutoTranslation
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    net.minecraft.server.v1_14_R1.World handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    // do not attempt to merge this class with v13BoundingBoxDrill
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawBlockDataOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.empty();
    }
    IBlockAccess blockAccess = handle.getChunkProvider().c(posX >> 4, posZ >> 4);
    if (blockAccess == null) {
      return BlockShapes.empty();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(blockAccess, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    BlockShape blockShape = translateWithOffset(nativeBoxes, posX, posY, posZ);
    return blockShape;
  }
}