package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.resolve.drill.acbbs.v11AlwaysCollidingBoundingBox;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.share.link.WrapperConverter;
import net.minecraft.server.v1_11_R1.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_11_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@PatchyAutoTranslation
public final class v11ShapeDrill extends AbstractShapeDrill {
  private static final v11AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v11AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, org.bukkit.Material type, int variant, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variant);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    blockData.getBlock().a(blockData, worldServer, blockposition, ALWAYS_COLLIDING_BOX, bbs, null, false);
    return translate(bbs);
  }

  @Override
  @PatchyAutoTranslation
  public BlockShape outlineShapeOf(World world, Player player, Material type, int variant, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variant);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Block block = blockData.getBlock();
    AxisAlignedBB bb = block.b(blockData, worldServer, blockposition);
    return bb == null ? BlockShapes.emptyShape() : WrapperConverter.boundingBoxFromAABB(bb);
  }
}
