package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

abstract class BlockShapePatch {
  private final Material[] materials;

  protected BlockShapePatch(Material... materials) {
    this.materials = materials;
  }

  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    // this method should be overridden
    // calls bb collision patch function if not

    List<BoundingBox> input = shape.boundingBoxes();
    List<BoundingBox> output = collisionPatch(world, player, posX, posY, posZ, type, blockState, input);

    if (input.equals(output)) {
      return shape;
    } else {
      return BlockShapes.merge(output);
    }
  }

  @Deprecated
  protected List<BoundingBox> collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    return bbs;
  }

  protected BlockShape outlinePatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    // this method should be overridden
    // calls bb outline patch function if not

    List<BoundingBox> input = shape.boundingBoxes();
    List<BoundingBox> output = outlinePatch(world, player, posX, posY, posZ, type, blockState, input);

    if (input.equals(output)) {
      return shape;
    } else {
      return BlockShapes.merge(output);
    }
  }

  @Deprecated
  protected List<BoundingBox> outlinePatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    return bbs;
  }

  protected boolean requireNormalization() {
    return false;
  }

  protected boolean appliesTo(Material material) {
    if (this.materials == null) {
      return false;
    }
    for (Material matcher : this.materials) {
      if (matcher == material) {
        return true;
      }
    }
    return false;
  }
}