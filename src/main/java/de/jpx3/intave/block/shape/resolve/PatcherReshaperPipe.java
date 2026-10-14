package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.patch.BlockShapePatcher;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class PatcherReshaperPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public PatcherReshaperPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape collisionShapeOf(World world, User user, Material type, int blockState, int posX, int posY, int posZ) {
    BlockShape original = forward.collisionShapeOf(world, user, type, blockState, posX, posY, posZ);
    return BlockShapePatcher.patchCollision(world, user, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public BlockShape outlineShapeOf(World world, User user, Material type, int blockState, int posX, int posY, int posZ) {
    BlockShape original = forward.outlineShapeOf(world, user, type, blockState, posX, posY, posZ);
    return BlockShapePatcher.patchOutline(world, user, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }
}
