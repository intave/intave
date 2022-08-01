package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class PowderSnowCollisionModifier extends CollisionModifier {
  private static final BlockShape POWDER_SNOW_FROM_ABOVE = BlockShapes.originCube();

  @Override
  public BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape) {
    ItemStack boots = user.player().getInventory().getBoots();
    boolean hasLeatherBootsEquipped = boots != null && boots.getType() == Material.LEATHER_BOOTS;
    if (hasLeatherBootsEquipped && userBox.minY >= posY + 1) {
      return POWDER_SNOW_FROM_ABOVE.contextualized(posX, posY, posZ);
    } else {
      return BlockShapes.emptyShape();
    }
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("POWDER_SNOW");
  }
}
