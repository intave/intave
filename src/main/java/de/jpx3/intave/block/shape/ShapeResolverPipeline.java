package de.jpx3.intave.block.shape;

import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

public interface ShapeResolverPipeline {
  BlockShape collisionShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ);

  BlockShape outlineShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ);

  default void downstreamTypeReset(Material type) {
  }
}
