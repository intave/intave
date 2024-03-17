package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

@Deprecated
final class DoorBlockPatch extends BlockShapePatch {
  private static final ThreadLocal<Boolean> topAcquire = ThreadLocal.withInitial(() -> false);

  @Override
  public List<BoundingBox> collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    int upperData = blockState;
    int lowerData;

    User user = UserRepository.userOf(player);
    boolean isUpper = (upperData & 8) != 0;
    if (isUpper) {
      lowerData = VolatileBlockAccess.variantIndexAccess(user, world, posX, posY - 1, posZ);
    } else {
      lowerData = upperData;
      if (topAcquire.get()) {
        upperData = 0;
      } else {
        topAcquire.set(true);
        upperData = VolatileBlockAccess.variantIndexAccess(user, world, posX, posY + 1, posZ);
        topAcquire.set(false);
      }
    }

    Direction direction = Direction.getFront(lowerData & 3);
    boolean open = (lowerData & 4) != 0;
    boolean hinge = (upperData & 1) != 0;

    float f = 0.1875F;

    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (open) {
      switch (direction) {
        case EAST:
          if (hinge) {
            builder.shape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
          } else {
            builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
          }
          break;
        case SOUTH:
          if (hinge) {
            builder.shape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
          } else {
            builder.shape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
          }
          break;
        case WEST:
          if (hinge) {
            builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
          } else {
            builder.shape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
          }
          break;
        case NORTH:
          if (hinge) {
            builder.shape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
          } else {
            builder.shape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
          }
          break;
      }
    } else {
      switch (direction) {
        case EAST:
          builder.shape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
          break;
        case SOUTH:
          builder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
          break;
        case WEST:
          builder.shape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
          break;
        case NORTH:
          builder.shape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
          break;
      }
    }

    return builder.applyAndResolve();
  }

  private static final String NAME_PATTERN = "DOOR";

  @Override
  public boolean appliesTo(Material material) {
    return material.isBlock() && material.name().contains(NAME_PATTERN);
  }
}