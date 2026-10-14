package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class TorchPatch extends BlockShapePatch {
  private static final BlockShape[] torchShapeCache = new BlockShape[6];

  @Override
  protected BlockShape outlinePatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    BlockVariant variant = BlockVariantRegister.variantOf(type, blockState);
    if (variant == null) {
      return shape;
    }
    Direction facing = variant.enumProperty(Direction.class, "facing");
    if (facing == null || facing == Direction.DOWN) {
      return shape;
    }
    int facingIndex = facing.getIndex();
    if (torchShapeCache[facingIndex] == null) {
      double var6 = 0.15;
      BlockShape newShape;
      switch (facing) {
        case EAST:
          newShape = BoundingBox.originFrom(0.0, 0.2, 0.5 - var6, var6 * 2.0, 0.8, 0.5 + var6);
          break;
        case WEST:
          newShape = BoundingBox.originFrom(1.0 - var6 * 2.0, 0.2, 0.5 - var6, 1.0, 0.8, 0.5 + var6);
          break;
        case SOUTH:
          newShape = BoundingBox.originFrom(0.5 - var6, 0.2, 0.0, 0.5 + var6, 0.8, var6 * 2.0);
          break;
        case NORTH:
          newShape = BoundingBox.originFrom(0.5 - var6, 0.2, 1.0 - var6 * 2.0, 0.5 + var6, 0.8, 1.0);
          break;
        default:
          var6 = 0.1;
          newShape = BoundingBox.originFrom(0.5 - var6, 0.0, 0.5 - var6, 0.5 + var6, 0.6, 0.5 + var6);
          break;
      }
      torchShapeCache[facingIndex] = newShape;
    }
    return torchShapeCache[facingIndex];
  }

  @Override
  protected boolean appliesTo(Material material) {
    return material.name().contains("TORCH") && !MinecraftVersions.VER1_13_0.atOrAbove();
  }
}
