package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class PowderSnowCollisionModifier extends CollisionModifier {
  private static final BlockShape FALLING_SHAPE = BoundingBox.originFromX16(0.0, 0.0, 0.0, 16.0, 0.9F * 16.0, 16.0);
  private static final BlockShape POWDER_SNOW_FROM_ABOVE = BlockShapes.originCube();

  @Override
  public BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape) {
    ItemStack boots = user.player().getInventory().getBoots();
    boolean leatherBootsEquipped = boots != null && boots.getType() == Material.LEATHER_BOOTS;

    MovementMetadata movement = user.meta().movement();
    if (movement.artificialFallDistance > 2.5) {
      return FALLING_SHAPE.contextualized(posX, posY, posZ);
    }

    if (leatherBootsEquipped && movement.verifiedPositionY > (double)posY + 1 - 9.999999747378752E-6 && !movement.isSneaking()) {
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
