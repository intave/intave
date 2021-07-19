package de.jpx3.intave.world.blockshape.resolver.pipeline;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolvePipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class DynamicEmptyBlockPreFilter implements BoundingBoxResolvePipeline {
  private final BoundingBoxResolvePipeline forward;

  public DynamicEmptyBlockPreFilter(BoundingBoxResolvePipeline forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> nativeResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (isEmpty(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return Collections.emptyList();
    }
    return forward.nativeResolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public List<WrappedAxisAlignedBB> customResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (isEmpty(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return Collections.emptyList();
    }
    return forward.customResolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void flushTypeCache(Material type) {
    forward.flushTypeCache(type);
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
