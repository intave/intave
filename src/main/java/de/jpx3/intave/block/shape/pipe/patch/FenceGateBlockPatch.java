package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.shade.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class FenceGateBlockPatch extends BoundingBoxPatch {
  @Override
  public List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return bbs;
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    return bbs;
  }

  private final static String NAME_PATTERN = "FENCE_GATE";

  @Override
  public boolean appliesTo(Material material) {
    return material.name().contains(NAME_PATTERN);
  }
}