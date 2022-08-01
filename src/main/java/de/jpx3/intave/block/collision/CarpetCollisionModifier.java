package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.EnumSet;

final class CarpetCollisionModifier extends CollisionModifier {
  private static final BlockShape CARPET_FROM_ABOVE = BoundingBox.originFromX16(0, -1, 0, 16, 0, 16);
  private static final BlockShape CARPET_FROM_BELOW = BoundingBox.originFromX16(0, 0, 0, 16, 1, 16);
  private final EnumSet<Material> CARPETS = EnumSet.noneOf(Material.class);

  @Override
  public BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape) {
    if (user.protocolVersion() <= 5) {
      Material material = VolatileBlockAccess.typeAccess(user, posX, posY - 1, posZ);
      boolean isCarpetBelow = CARPETS.contains(material);

      if (isCarpetBelow) {
        return CARPET_FROM_ABOVE.contextualized(posX, posY, posZ);
      } else {
        if (userBox.maxY <= posY + 0.1) {
          return CARPET_FROM_BELOW.contextualized(posX, posY, posZ);
        } else {
          return BlockShapes.emptyShape();
        }
      }
    } else {
      return shape;
    }
  }

  @Override
  public boolean matches(Material material) {
    boolean isCarpet = material.name().contains("CARPET");
    if (isCarpet) {
      CARPETS.add(material);
    }
    return isCarpet;
  }
}
