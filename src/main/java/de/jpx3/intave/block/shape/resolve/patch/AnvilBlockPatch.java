package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class AnvilBlockPatch extends BlockShapePatch {
  public AnvilBlockPatch() {
    super(Material.ANVIL);
  }

  @Override
  public BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockVariant, BlockShape shape) {
    User user = UserRepository.userOf(player);
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

  private static final boolean CORRUPTED = MinecraftVersions.VER1_14_0.atOrAbove();

  private Direction.Axis axisOf(int variantIndex) {
    BlockVariant variant = BlockVariantRegister.variantOf(Material.ANVIL, variantIndex);
//    System.out.println("Anvil Index: " + variantIndex);
//    variant.dumpStates();
    return variant.enumProperty(Direction.class, "facing").axis();
//    if (CORRUPTED) {
//      switch (variantIndex) {
//        case 1:
//          return Direction.Axis.Z_AXIS;
//        case 2:
//          return Direction.Axis.X_AXIS;
//      }
//    }
//    return Direction.getHorizontal(variantIndex & 3).axis();
//    return Direction.Axis.X_AXIS;
  }
}