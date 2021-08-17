package de.jpx3.intave.world.blockshape.boxresolver;

import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockshape.boxresolver.patcher.BoundingBoxBuilder;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public final class CorruptedFilteringPipe implements ResolverPipeline {
  private final ResolverPipeline forward;

  public CorruptedFilteringPipe(ResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    List<WrappedAxisAlignedBB> corrupted = resolveCorrupted(type, blockState);
    return corrupted != null ? corrupted : forward.resolve(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }

  public List<WrappedAxisAlignedBB> resolveCorrupted(Material type, int data) {
    if (type == BlockTypeAccess.SKULL) {
      BoundingBoxBuilder builder = BoundingBoxBuilder.create();
      switch (data & 7) {
        case 1:
          builder.shape(0.25F, 0.0F, 0.25F, 0.75F, 0.5F, 0.75F); // up
          break;
        case 2:
          builder.shape(0.25F, 0.25F, 0.5F, 0.75F, 0.75F, 1.0F); // north
          break;
        case 3:
          builder.shape(0.25F, 0.25F, 0.0F, 0.75F, 0.75F, 0.5F); // south
          break;
        case 4:
          builder.shape(0.5F, 0.25F, 0.25F, 1.0F, 0.75F, 0.75F); // west
          break;
        case 5:
          builder.shape(0.0F, 0.25F, 0.25F, 0.5F, 0.75F, 0.75F); // east
          break;
      }
    }
    return null;
  }
}
