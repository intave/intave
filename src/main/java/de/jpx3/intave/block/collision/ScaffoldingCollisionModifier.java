package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class ScaffoldingCollisionModifier extends CollisionModifier {
  @Override
  public BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape, CollisionOrigin type) {
    if (useCustomCollision(user, posY)) {
      double yStart = 14.0 / 16.0;
      double yEnd = 1.0;
      return BoundingBox.fromBounds(
        posX, posY + yStart, posZ,
        posX + 1, posY + yEnd, posZ + 1
      );
    } else {
      if (bottomProperty(user, user.player().getWorld(), posX, posY, posZ) && useCustomCollision(user, posY - 1)) {
        return BoundingBox.fromBounds(posX, posY, posZ, posX + 1.0, posY + 2.0 / 16.0, posZ + 1.0);
      } else {
        return BlockShapes.emptyShape();
      }
    }
  }

  @Override
  public BlockShape imaginaryBlockShape(Material type, User user, int posX, int posY, int posZ, int data) {
    return BlockShapes.emptyShape();
  }

  private boolean bottomProperty(User user, World world, int posX, int posY, int posZ) {
    Block block = VolatileBlockAccess.blockAccess(world, posX, posY, posZ);
    if (block.getY() < WorldHeight.LOWER_WORLD_LIMIT) {
      return false;
    }
    BlockVariant blockVariant = VolatileBlockAccess.variantAccess(user, world, posX, posY, posZ);
    int distance = blockVariant.propertyOf("distance");
    boolean bottom = blockVariant.propertyOf("bottom");
    return bottom && distance != 0;
  }

  private boolean useCustomCollision(User user, double blockY) {
    MovementMetadata movementData = user.meta().movement();
    return movementData.positionY >= blockY + 1 - (double) 0.00001f;
  }

  @Override
  public boolean matches(Material material) {
    String name = material.name();
    return name.contains("SCAFFOLDING");
  }
}
