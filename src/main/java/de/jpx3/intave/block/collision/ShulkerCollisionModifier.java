package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import static de.jpx3.intave.block.collision.CollisionOrigin.INTERSECTION_CHECK;

final class ShulkerCollisionModifier extends CollisionModifier {
  @Override
  public BlockShape modify(
    User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape,
    CollisionOrigin collisionType
  ) {
    if (collisionType == INTERSECTION_CHECK) {
      return BlockShapes.emptyShape();
    }
    ShulkerBox shulker = user.meta().movement().shulkerBoxAt(posX, posY, posZ);
    return shulker != null ? shulker.originShape().contextualized(posX, posY, posZ) : shape;
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("SHULKER_BOX");
  }
}
