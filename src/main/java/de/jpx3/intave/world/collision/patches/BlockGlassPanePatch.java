package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockGlassPanePatch extends BoundingBoxPatch {

  public BlockGlassPanePatch() {
    super(Material.STAINED_GLASS_PANE);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getType(), block.getData(), bbs);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {


    return super.patch(world, player, type, blockState, bbs);
  }
}
