package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

final class EnderPortalFramePatch extends BlockShapePatch {
  private final BoundingBox baseShape = BoundingBox.originFromX16(0, 0, 0, 16, 13, 16);
  private final BoundingBox eye8 = BoundingBox.originFromX16(5, 13, 5, 11, 16, 11);
  private final BoundingBox eye13 = BoundingBox.originFromX16(4, 13, 4, 12, 16, 12);

  @Override
  protected BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int variantIndex, BlockShape shape) {
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    boolean eye = variant.propertyOf("eye");
    List<BoundingBox> boundingBoxes = new ArrayList<>();
    boundingBoxes.add(baseShape);
    if (eye) {
      User user = UserRepository.userOf(player);
      if (user.meta().protocol().waterUpdate()) {
        boundingBoxes.add(eye13);
      } else {
        boundingBoxes.add(eye8);
      }
    }
    return BlockShapes.merge(boundingBoxes);
  }

  @Override
  public boolean appliesTo(Material material) {
    return material.name().endsWith("PORTAL_FRAME");
  }
}
