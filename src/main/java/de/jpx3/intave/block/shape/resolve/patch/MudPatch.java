package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class MudPatch extends BlockShapePatch {
  private static final BoundingBox MUD_BOUNDING_BOX = BoundingBox.originFromX16(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);

  @Override
  protected BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    return MUD_BOUNDING_BOX;
  }

  @Override
  protected BlockShape outlinePatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    return BlockShapes.originCube();
  }

  @Override
  protected boolean appliesTo(Material material) {
    return "MUD".equalsIgnoreCase(material.name());
  }
}
