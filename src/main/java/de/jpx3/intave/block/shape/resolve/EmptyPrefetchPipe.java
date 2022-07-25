package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.diagnostic.ShapeAccessFlowStudy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class EmptyPrefetchPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public EmptyPrefetchPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (isEmpty(type)) {
      ShapeAccessFlowStudy.incremDynamic();
      return BlockShapes.emptyShape();
    }
    return forward.collisionShapeOf(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    return forward.outlineShapeOf(world, player, type, blockState, posX, posY, posZ);
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
      case SIGN:
      case SIGN_POST:
      case WALL_SIGN:
      case LEVER:
        return true;
    }
    return false;
  }
}
