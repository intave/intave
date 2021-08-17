package de.jpx3.intave.world.blockshape.boxresolver;

import de.jpx3.intave.world.blockshape.boxresolver.patcher.BoundingBoxPatcher;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public final class PatcherReshaperPipe implements ResolverPipeline {
  private final ResolverPipeline forward;

  public PatcherReshaperPipe(ResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    List<WrappedAxisAlignedBB> original = forward.resolve(world, player, type, blockState, posX, posY, posZ);
    return player == null ? original : BoundingBoxPatcher.patch(world, player, posX, posY, posZ, type, blockState, original);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }
}
