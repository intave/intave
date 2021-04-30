package de.jpx3.intave.world.collision.dynamic;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.client.MaterialLogic;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.collision.BoundingBoxResolver;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;

public final class LiquidDynamicResolver implements BoundingBoxResolver {
  private final BoundingBoxResolver forward;

  public LiquidDynamicResolver(BoundingBoxResolver forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, Material advanceType, int posX, int posY, int posZ) {
    if(MaterialLogic.isLiquid(advanceType)) {
      BoundingBoxAccessFlowStudy.increaseDynamic();
      return Collections.emptyList();
    }
    return forward.resolve(world, advanceType, posX, posY, posZ);
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ, Material type, int blockState) {
    return forward.resolve(world, posX, posY, posZ, type, blockState);
  }
}
