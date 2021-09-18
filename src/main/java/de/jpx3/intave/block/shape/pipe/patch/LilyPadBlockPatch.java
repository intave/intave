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

final class LilyPadBlockPatch extends BoundingBoxPatch {
  @Override
  public List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    User user = UserRepository.userOf(player);
    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (user.meta().protocol().combatUpdate()) {
      builder.shape(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.09375f, 0.9375f);
    } else {
      float radius = 0.5F;
      float height = 0.015625F;
      builder.shape(0.5F - radius, 0.0F, 0.5F - radius, 0.5F + radius, height, 0.5F + radius);
    }
    return builder.applyAndResolve();
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.contains("WATER_LILY");
  }
}