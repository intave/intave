package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.resolve.drill.acbbs.v8AlwaysCollidingBoundingBox;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.share.BoundingBox;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@PatchyAutoTranslation
public final class v8ShapeDrill extends AbstractShapeDrill {
  private static final v8AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v8AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public BlockShape collisionShapeOf(World world, Player player, org.bukkit.Material type, int blockState, int posX, int posY, int posZ) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, blockState);
    if (blockData == null) {
      return BlockShapes.emptyShape();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    Block block = blockData.getBlock();
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    block.updateShape(worldServer, blockposition);
    block.a(worldServer, blockposition, blockData, ALWAYS_COLLIDING_BOX, bbs, null);
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
    if (!block.a(blockData, false)) {
      return BlockShapes.emptyShape();
    }
    block.updateShape(worldServer, blockposition);
    return BoundingBox.originFrom(
      block.B(), block.D(), block.F(),
      block.C(), block.E(), block.G()
    ).contextualized(posX, posY, posZ);
  }
}
