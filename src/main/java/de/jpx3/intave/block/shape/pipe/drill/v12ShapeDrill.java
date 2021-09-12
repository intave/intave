package de.jpx3.intave.block.shape.pipe.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.pipe.drill.acbbs.v12AlwaysCollidingBoundingBox;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@PatchyAutoTranslation
public final class v12ShapeDrill extends AbstractShapeDrill {
  private final static v12AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v12AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public BlockShape resolve(World world, Player player, org.bukkit.Material type, int blockState, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = Block.getByCombinedId(type.getId() | (blockState & 0xF) << 12);
    if (blockData == null) {
      return BlockShapes.empty();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    blockData.getBlock().a(blockData, worldServer, blockposition, ALWAYS_COLLIDING_BOX, bbs, null, false);
    return translate(bbs);
  }
}