package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Direction;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class PointedDripstoneBlockPatch extends BlockShapePatch {
  private final BoundingBox TIP_MERGE_SHAPE = BoundingBox.originFromX16(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
  private final BoundingBox TIP_UP_SHAPE = BoundingBox.originFromX16(5.0D, 0.0D, 5.0D, 11.0D, 11.0D, 11.0D);
  private final BoundingBox TIP_DOWN_SHAPE = BoundingBox.originFromX16(5.0D, 5.0D, 5.0D, 11.0D, 16.0D, 11.0D);
  private final BoundingBox FRUSTUM_SHAPE = BoundingBox.originFromX16(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
  private final BoundingBox MIDDLE_SHAPE = BoundingBox.originFromX16(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
  private final BoundingBox BASE_SHAPE = BoundingBox.originFromX16(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
  private final BoundingBox DEF_SHAPE = BoundingBox.originFromX16(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

  @Override
  public BlockShape collisionPatch(World world, Player player, int x, int y, int z, Material type, int variantIndex, BlockShape shape) {
    return shapePatch(world, player, x, y, z, type, variantIndex, shape);
  }

  @Override
  public BlockShape outlinePatch(World world, Player player, int x, int y, int z, Material type, int variantIndex, BlockShape shape) {
    return shapePatch(world, player, x, y, z, type, variantIndex, shape);
  }

  private BlockShape shapePatch(World world, Player player, int x, int y, int z, Material type, int variantIndex, BlockShape shape) {
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    DripstoneThickness thickness = variant.enumProperty(DripstoneThickness.class, "thickness");
    Direction verticalDirection = variant.enumProperty(Direction.class, "vertical_direction");
    BoundingBox selected;
    switch (thickness) {
      case TIP_MERGE:
        selected = TIP_MERGE_SHAPE;
        break;
      case TIP:
        switch (verticalDirection) {
          case UP:
            selected = TIP_UP_SHAPE;
            break;
          case DOWN:
            selected = TIP_DOWN_SHAPE;
            break;
          default:
            throw new IllegalStateException("Unexpected vertical direction: " + verticalDirection);
        }
        break;
      case MIDDLE:
        selected = MIDDLE_SHAPE;
        break;
      case FRUSTUM:
        selected = FRUSTUM_SHAPE;
        break;
      case BASE:
        selected = BASE_SHAPE;
        break;
      default:
        selected = DEF_SHAPE;
    }
    float allowedOffset = 0.125f;
    long randomCoordinate = ClientMath.coordinateRandom(x, 0, z);
    double offsetX = MathHelper.minmax(-allowedOffset, ((double) ((float) (randomCoordinate & 15L) / 15.0F) - 0.5D) * 0.5D, allowedOffset);
    double offsetZ = MathHelper.minmax(-allowedOffset, ((double) ((float) (randomCoordinate >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D, allowedOffset);
    double offsetY = 0.0;
    BoundingBox boundingBox = selected.offset(offsetX, offsetY, offsetZ);
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().endsWith("_DRIPSTONE");
  }

  @KeepEnumInternalNames
  public enum DripstoneThickness {
    TIP_MERGE, TIP, FRUSTUM, MIDDLE, BASE
  }
}
