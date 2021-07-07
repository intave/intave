package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public final class BambooCollisionModifier extends CollisionModifier {
  private final static WrappedAxisAlignedBB COLLISION_BOX = new WrappedAxisAlignedBB(6.5D / 16.0, 0.0D, 6.5D / 16.0, 9.5D / 16.0, 1.0, 9.5D / 16.0);

  @Override
  public List<WrappedAxisAlignedBB> modify(User user, WrappedAxisAlignedBB userBox, int posX, int posY, int posZ, List<WrappedAxisAlignedBB> boxes) {
    // Small Bamboos Leaves
    if (boxes.isEmpty()) {
      return boxes;
    }
    long randomCoordinate = WrappedMathHelper.getCoordinateRandom(posX, 0, posZ);
    double offsetX = ((double) ((float) (randomCoordinate & 15L) / 15.0F) - 0.5D) * 0.5D;
    double offsetZ = ((double) ((float) (randomCoordinate >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D;
    double offsetY = 0.0;
    WrappedAxisAlignedBB translate = COLLISION_BOX.offset(offsetX, offsetY, offsetZ);
    return Collections.singletonList(translate.offset(posX, posY, posZ));
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("BAMBOO");
  }
}
