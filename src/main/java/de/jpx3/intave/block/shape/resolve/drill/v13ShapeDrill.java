package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_13_R2.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

import java.util.List;

@PatchyAutoTranslation
public final class v13ShapeDrill extends AbstractShapeDrill {
  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, Material type, int variant, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variant);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    VoxelShape collisionShape = blockData.getCollisionShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = collisionShape.d();
    return translateWithOffset(nativeBoxes, posX, posY, posZ);
  }

  @Override
  @PatchyAutoTranslation
  public BlockShape outlineShapeOf(World world, Player player, Material type, int variant, int posX, int posY, int posZ) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    BlockPosition blockPosition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variant);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    VoxelShape shape = blockData.getShape(handle, blockPosition);
    List<AxisAlignedBB> nativeBoxes = shape.d();
    return translateWithOffset(nativeBoxes, posX, posY, posZ);
  }
}