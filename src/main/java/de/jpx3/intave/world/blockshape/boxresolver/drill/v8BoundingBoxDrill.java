package de.jpx3.intave.world.blockshape.boxresolver.drill;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.blockshape.boxresolver.ResolverPipeline;
import de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs.v8AlwaysCollidingBoundingBox;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PatchyAutoTranslation
public final class v8BoundingBoxDrill implements ResolverPipeline {
  private final static v8AlwaysCollidingBoundingBox ALWAYS_COLLIDING_BOX = new v8AlwaysCollidingBoundingBox();

  @Override
  @PatchyAutoTranslation
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, org.bukkit.Material type, int blockState, int posX, int posY, int posZ) {
    BlockPosition blockposition = new BlockPosition(posX, posY, posZ);
    IBlockData blockData = Block.getByCombinedId(type.getId() | (blockState & 0xF) << 12);
    if (blockData == null) {
      return Collections.emptyList();
    }
    List<AxisAlignedBB> bbs = new ArrayList<>();
    WorldServer worldServer = ((CraftWorld) world).getHandle();
    blockData.getBlock().a(worldServer, blockposition, blockData, ALWAYS_COLLIDING_BOX, bbs, null);
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
