package de.jpx3.intave.block.shape.pipe;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.pipe.patch.BoundingBoxPatcher;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PatcherReshaperPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public PatcherReshaperPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    BlockShape original = forward.resolve(world, player, type, blockState, posX, posY, posZ);
    return player == null ? original : BoundingBoxPatcher.patch(world, player, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }
}
