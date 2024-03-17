package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class FarmlandBlockPatch extends BlockShapePatch {
  @Override
  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    User user = UserRepository.userOf(player);
    if (user.protocolVersion() > 210 /* 1.10.1*/) {
      return BoundingBox.originFrom(0, 0, 0, 1, 0.9375f, 1);
    } else {
      return BlockShapes.originCube();
    }
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.equals("SOIL") || name.equals("FARMLAND");
  }
}
