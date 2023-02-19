package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;

import java.util.Set;

import static de.jpx3.intave.block.collision.CollisionOrigin.INTERSECTION_CHECK;

final class PistonCollisionModifier extends CollisionModifier {
  @Override
  public BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape, CollisionOrigin type) {
    MovementMetadata movement = user.meta().movement();
    Set<Motion> toleratedPistonMotions = movement.toleratedPistonMotions;
    if (!toleratedPistonMotions.isEmpty() && type == INTERSECTION_CHECK) {
      return BlockShapes.emptyShape();
    }
    return shape;
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("PISTON");
  }
}
