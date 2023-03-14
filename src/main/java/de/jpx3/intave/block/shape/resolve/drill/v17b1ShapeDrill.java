package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class v17b1ShapeDrill extends AbstractShapeDrill {
  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    IBlockAccess blockAccess = chunkAccessOf(world, posX, posZ);
    if (blockData == null || blockAccess == null) {
      return BlockShapes.emptyShape();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(blockAccess, blockPositionOf(posX, posY, posZ));
    return shapeFromVoxel(collisionShape, posX, posY, posZ);
  }

  @Override
  @PatchyAutoTranslation
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    IBlockAccess blockAccess = chunkAccessOf(world, posX, posZ);
    if (blockData == null || blockAccess == null) {
      return BlockShapes.emptyShape();
    }
    VoxelShape shape = blockData.getShape(blockAccess, blockPositionOf(posX, posY, posZ));
    return shapeFromVoxel(shape, posX, posY, posZ);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private BlockPosition blockPositionOf(int posX, int posY, int posZ) {
    return new BlockPosition(posX, posY, posZ);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private BlockShape shapeFromVoxel(VoxelShape shape, int posX, int posY, int posZ) {
    // should never happen, but just in case
    if (shape == null) {
      return BlockShapes.emptyShape();
    }
    // check if shape is static empty
    if (VoxelShapes.a() == shape) {
      return BlockShapes.emptyShape();
    }
    // check if shape is static cube
    if (VoxelShapes.b() == shape) {
      return BlockShapes.cubeAt(posX, posY, posZ);
    }
    // convert complex blocks to native BBs
    return translateWithOffset(shape.toList(), posX, posY, posZ);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private IBlockAccess chunkAccessOf(World world, int posX, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    return handle.getChunkProvider().c(posX >> 4, posZ >> 4);
  }
}