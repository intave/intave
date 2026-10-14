package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class FenceGateBlockPatch extends BlockShapePatch {
  @Override
  public BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    return shape;
  }

  private static final String NAME_PATTERN = "FENCE_GATE";

  @Override
  public boolean appliesTo(Material material) {
    return material.name().contains(NAME_PATTERN);
  }
}
