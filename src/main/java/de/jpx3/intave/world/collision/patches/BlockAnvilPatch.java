package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.collision.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockAnvilPatch extends BoundingBoxPatch {
  protected BlockAnvilPatch() {
    super(Material.ANVIL);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getType(), block.getData(), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    BoundingBoxBuilder boundingBoxBuilder = BoundingBoxBuilder.create();

    if((blockState & 3) % 2 == 0) {
      boundingBoxBuilder.shape(0.125F, 0.0F, 0.0F, 0.875F, 1.0F, 1.0F);
    } else {
      boundingBoxBuilder.shape(0.0F, 0.0F, 0.125F, 1.0F, 1.0F, 0.875F);
    }

    boundingBoxBuilder.apply();
    return boundingBoxBuilder.resolve();
  }
}
