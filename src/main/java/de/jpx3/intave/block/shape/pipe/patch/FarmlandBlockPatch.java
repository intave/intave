package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class FarmlandBlockPatch extends BoundingBoxPatch {
  @Override
  protected List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  protected List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    User user = UserRepository.userOf(player);
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (user.meta().protocol().protocolVersion() > 210 /* 1.10.1*/) {
      builder.shape(0, 0, 0, 1, 0.9375f, 1);
    } else {
      builder.shape(0,0,0,1,1, 1);
    }
    return builder.applyAndResolve();
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.equals("SOIL") || name.equals("FARMLAND");
  }
}
