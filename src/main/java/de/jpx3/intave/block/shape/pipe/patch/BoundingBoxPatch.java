package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.shade.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

abstract class BoundingBoxPatch {
  private final Material[] material;

  protected BoundingBoxPatch(Material... materials) {
    this.material = materials;
  }

  protected List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return bbs;
  }

  protected List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    return bbs;
  }

  protected boolean requireNormalization() {
    return false;
  }

  public boolean appliesTo(Material material) {
    return Arrays.stream(this.material).anyMatch(matcher -> matcher == material);
  }
}