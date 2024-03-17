package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class BambooPotPatch extends BlockShapePatch {
  private static final BlockShape FLOWER_POT_SHAPE = BoundingBox.originFromX16(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);

  @Override
  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    return FLOWER_POT_SHAPE;
  }

  @Override
  protected boolean appliesTo(Material material) {
    return material.name().contains("POTTED_BAMBOO");
  }
}
