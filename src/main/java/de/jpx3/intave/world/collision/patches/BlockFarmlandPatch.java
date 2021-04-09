package de.jpx3.intave.world.collision.patches;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockFarmlandPatch extends BoundingBoxPatch {

  public BlockFarmlandPatch() {
    super(Material.SOIL);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getType(), block.getData(), bbs);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if(user.meta().clientData().protocolVersion() > 210 /* 1.10.1*/) {
      builder.shape(0, 0, 0, 1, 0.9375f, 1);
    } else {
      builder.shape(0,0,0,1,1, 1);
    }
    return builder.applyAndResolve();
  }
}
