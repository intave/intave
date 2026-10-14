package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class DrillRescuePipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline next;

  public DrillRescuePipe(ShapeResolverPipeline next) {
    this.next = next;
  }

  @Override
  public BlockShape collisionShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    try {
      return next.collisionShapeOf(world, user, type, variantIndex, posX, posY, posZ);
    } catch (Exception exception) {
      // we catch irregularities here elsewhere
      return BoundingBox
        // anything but a full or empty box, or it will be remembered
        .originFrom(0.25, 0.25, 0.25, 0.75, 0.75, 0.75)
        .contextualized(posX, posY, posZ);
    }
  }

  @Override
  public BlockShape outlineShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    try {
      return next.outlineShapeOf(world, user, type, variantIndex, posX, posY, posZ);
    } catch (Exception exception) {
      // we catch irregularities here elsewhere
      return BoundingBox
        // anything but a full or empty box, or it will be remembered
        .originFrom(0.25, 0.25, 0.25, 0.75, 0.75, 0.75)
        .contextualized(posX, posY, posZ);
    }
  }
}
