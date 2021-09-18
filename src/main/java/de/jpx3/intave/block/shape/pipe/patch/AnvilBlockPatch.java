package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.EnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

final class AnvilBlockPatch extends BoundingBoxPatch {
  public AnvilBlockPatch() {
    super(Material.ANVIL);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), BlockTypeAccess.typeAccess(block, player), BlockVariantAccess.variantAccess(block), bbs);
  }

  @Override
  public List<BoundingBox> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    User user = UserRepository.userOf(player);
    boolean legacy = user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_13;
    EnumDirection.Axis axis = axisOf(blockState);
    return legacy ? legacyPatch(axis) : modernPatch(axis);
  }

  private List<BoundingBox> legacyPatch(EnumDirection.Axis axis) {
    BoundingBoxBuilder boundingBoxBuilder = BoundingBoxBuilder.create();
    if (axis == EnumDirection.Axis.X) {
      boundingBoxBuilder.shape(0.0F, 0.0F, 0.125F, 1.0F, 1.0F, 0.875F);
    } else {
      boundingBoxBuilder.shape(0.125F, 0.0F, 0.0F, 0.875F, 1.0F, 1.0F);
    }
    return boundingBoxBuilder.applyAndResolve();
  }

  private List<BoundingBox> modernPatch(EnumDirection.Axis axis) {
    ApplyOnShapeBoundingBoxBuilder boundingBoxBuilder = ApplyOnShapeBoundingBoxBuilder.create();
    if (axis == EnumDirection.Axis.X) {
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
    return boundingBoxBuilder.resolve();
  }

  private final static boolean CORRUPTED = MinecraftVersions.VER1_14_0.atOrAbove();

  private EnumDirection.Axis axisOf(int state) {
    if (CORRUPTED) {
      switch (state) {
        case 1:
          return EnumDirection.Axis.Z;
        case 2:
          return EnumDirection.Axis.X;
      }
    }
    return EnumDirection.getHorizontal(state & 3).getAxis();
  }
}