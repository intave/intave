package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.List;

public final class ScaffoldingCollisionModifier extends CollisionModifier {
  @Override
  public List<BoundingBox> modify(User user, BoundingBox userBox, int posX, int posY, int posZ, List<BoundingBox> boxes) {
    if (useCustomCollision(user, posY)) {
      double yStart = 14.0 / 16.0;
      double yEnd = 1.0;
      return Collections.singletonList(BoundingBox.fromBounds(
        posX, posY + yStart, posZ,
        posX + 1, posY + yEnd, posZ + 1
      ));
    } else {
      if (bottomProperty(user, user.player().getWorld(), posX, posY, posZ) && useCustomCollision(user, posY - 1)) {
        BoundingBox collisionShapeTwo = BoundingBox.fromBounds(posX, posY, posZ, posX + 1.0, posY + 2.0 / 16.0, posZ + 1.0);
        return Collections.singletonList(collisionShapeTwo);
      } else {
        return Collections.emptyList();
      }
    }
  }

  private boolean bottomProperty(User user, World world, int posX, int posY, int posZ) {
    Block block = VolatileBlockAccess.unsafe__BlockAccess(world, posX, posY, posZ);
    if (block.getY() < 0) {
      return false;
    }
    BlockVariant blockVariant = VolatileBlockAccess.variantAccess(user, world, posX, posY, posZ);
    int distance = (Integer) blockVariant.propertyOf("distance");
    boolean bottom = (Boolean) blockVariant.propertyOf("bottom");

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
