package de.jpx3.intave.block.shape.pipe.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
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
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    IBlockAccess blockAccess = handle.getChunkProvider().c(posX >> 4, posZ >> 4);
    if (blockAccess == null) {
      return BlockShapes.emptyShape();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(blockAccess, blockPosition);
    // check if shape is static empty
    if (VoxelShapes.a() == collisionShape) {
      return BlockShapes.emptyShape();
    }
    // check if shape is static cube
    if (VoxelShapes.b() == collisionShape) {
      return BlockShapes.cubicShapeAt(posX, posY, posZ);
    }
    // convert complex blocks to native BBs
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    return translateWithOffset(nativeBoxes, posX, posY, posZ);
  }
}