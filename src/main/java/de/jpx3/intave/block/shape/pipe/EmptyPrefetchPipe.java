package de.jpx3.intave.block.shape.pipe;

import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.diagnostic.BoundingBoxAccessFlowStudy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class EmptyPrefetchPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public EmptyPrefetchPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (isEmpty(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return BlockShapes.empty();
    }
    return forward.resolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }

  private boolean isEmpty(Material type) {
    if (MaterialMagic.isLiquid(type)) {
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
