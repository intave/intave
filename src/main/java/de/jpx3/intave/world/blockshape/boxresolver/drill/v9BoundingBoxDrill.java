package de.jpx3.intave.world.blockshape.boxresolver.drill;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.blockshape.boxresolver.ResolverPipeline;
import de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v9AlwaysCollidingBoundingBox;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import net.minecraft.server.v1_9_R2.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PatchyAutoTranslation
public final class v9BoundingBoxDrill implements ResolverPipeline {
  private final static v9AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v9AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, org.bukkit.Material type, int blockState, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = Block.getByCombinedId(type.getId() | (blockState & 0xF) << 12);
    if (blockData == null) {
      return Collections.emptyList();
    }
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    List<AxisAlignedBB> bbs = new ArrayList<>();
    blockData.getBlock().a(blockData, worldServer, blockposition, ALWAYS_COLLIDING_BOX, bbs, null);
    return translate(bbs);
  }

  private List<WrappedAxisAlignedBB> translate(List<?> bbs) {
    if (bbs.isEmpty()) {
      return Collections.emptyList();
    }
    List<WrappedAxisAlignedBB> list = new ArrayList<>();
    for (Object bb : bbs) {
      list.add(WrapperLinkage.boundingBoxOf(bb));
    }
    return list;
  }
}
