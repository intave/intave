package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.patch.BlockShapePatcher;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class PatcherReshaperPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public PatcherReshaperPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    BlockShape original = forward.collisionShapeOf(world, player, type, blockState, posX, posY, posZ);
    return player == null ? original : BlockShapePatcher.patchCollision(world, player, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    BlockShape original = forward.outlineShapeOf(world, player, type, blockState, posX, posY, posZ);
    return player == null ? original : BlockShapePatcher.patchOutline(world, player, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }
}
