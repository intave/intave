package de.jpx3.intave.world.blockshape.boxresolver;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class EmptyPrefetchPipe implements ResolverPipeline {
  private final ResolverPipeline forward;

  public EmptyPrefetchPipe(ResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (isEmpty(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return Collections.emptyList();
    }
    return forward.resolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }

  private boolean isEmpty(Material type) {
    if (Materials.isLiquid(type)) {
      return true;
    }
    switch (type) {
      case AIR:
//      case GRASS:
//      case LONG_GRASS:
      case SIGN:
      case SIGN_POST:
      case WALL_SIGN:
      case LEVER:
        return true;
    }

    return false;
  }
}
