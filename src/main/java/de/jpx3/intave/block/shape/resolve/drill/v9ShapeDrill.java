package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.resolve.drill.acbbs.v9AlwaysCollidingBoundingBox;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.share.link.WrapperConverter;
import net.minecraft.server.v1_9_R2.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@PatchyAutoTranslation
public final class v9ShapeDrill extends AbstractShapeDrill {
  private static final v9AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v9AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, Material type, int variant, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variant);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    blockData.getBlock().a(blockData, worldServer, blockposition, ALWAYS_COLLIDING_BOX, bbs, null);
    return translate(bbs);
  }

  @Override
  @PatchyAutoTranslation
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Block block = blockData.getBlock();
    AxisAlignedBB bb = block.a(blockData, (IBlockAccess) worldServer, blockposition);
    return bb == null ? BlockShapes.emptyShape() : WrapperConverter.boundingBoxFromAABB(bb);
  }
}
