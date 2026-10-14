package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;

final class AnvilBlockPatch extends BlockShapePatch {
  public AnvilBlockPatch() {
    super(Material.ANVIL);
  }

  @Override
  public BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockVariant, BlockShape shape) {
    boolean legacy = user.protocolVersion() < ProtocolMetadata.VER_1_13;
    Direction.Axis axis = axisOf(blockVariant);
    return legacy ? legacyPatch(axis) : modernPatch(axis);
  }

  private BlockShape legacyPatch(Direction.Axis axis) {
    if (axis == Direction.Axis.X_AXIS) {
      return BoundingBox.originFrom(0.0F, 0.0F, 0.125F, 1.0F, 1.0F, 0.875F);
    } else {
      return BoundingBox.originFrom(0.125F, 0.0F, 0.0F, 0.875F, 1.0F, 1.0F);
    }
  }

  private BlockShape modernPatch(Direction.Axis axis) {
    ApplyOnShapeBoundingBoxBuilder boundingBoxBuilder = ApplyOnShapeBoundingBoxBuilder.create();
    if (axis == Direction.Axis.X_AXIS) {
      boundingBoxBuilder.shapeX16AndApply(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
      boundingBoxBuilder.shapeX16AndApply(3.0D, 4.0D, 4.0D, 13.0D, 5.0D, 12.0D);
      boundingBoxBuilder.shapeX16AndApply(4.0D, 5.0D, 6.0D, 12.0D, 10.0D, 10.0D);
      boundingBoxBuilder.shapeX16AndApply(0.0D, 10.0D, 3.0D, 16.0D, 16.0D, 13.0D);
    } else {
      boundingBoxBuilder.shapeX16AndApply(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
      boundingBoxBuilder.shapeX16AndApply(4.0D, 4.0D, 3.0D, 12.0D, 5.0D, 13.0D);
      boundingBoxBuilder.shapeX16AndApply(6.0D, 5.0D, 4.0D, 10.0D, 10.0D, 12.0D);
      boundingBoxBuilder.shapeX16AndApply(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D);
    }
    return boundingBoxBuilder.resolveAsShape();
  }

  private Direction.Axis axisOf(int variantIndex) {
    BlockVariant variant = BlockVariantRegister.variantOf(Material.ANVIL, variantIndex);
    return variant.enumProperty(Direction.class, "facing").axis();
  }
}
