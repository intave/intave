package de.jpx3.intave.world.blockshape.resolver.pipeline.patcher;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class BlockDoorPatch extends BoundingBoxPatch {
  private static final ThreadLocal<Boolean> topAcquire = ThreadLocal.withInitial(() -> false);

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), block.getType(), BlockDataAccess.dataIndexOf(block), bbs);
  }

  @Override
  public List<WrappedAxisAlignedBB> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    int upperData = blockState;
    int lowerData;

    User user = UserRepository.userOf(player);
    boolean isUpper = (upperData & 8) != 0;
    if(isUpper) {
      lowerData = BukkitBlockAccess.cacheAppliedDataAccess(user, world, posX, posY - 1, posZ);
    } else {
      lowerData = upperData;
      if (topAcquire.get()) {
        upperData = 0;
      } else {
        topAcquire.set(true);
        upperData = BukkitBlockAccess.cacheAppliedDataAccess(user, world, posX, posY + 1, posZ);
        topAcquire.set(false);
      }
    }

    WrappedEnumDirection enumDirection = WrappedEnumDirection.getFront(lowerData & 3);
    boolean open = (lowerData & 4) != 0;
    boolean hinge = (upperData & 1) != 0;

    float f = 0.1875F;

    BoundingBoxBuilder builder = BoundingBoxBuilder.create();
    if (open) {
      switch (enumDirection) {
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
      switch (enumDirection) {
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

  private final static String NAME_PATTERN = "DOOR";

  @Override
  public boolean appliesTo(Material material) {
    return material.isBlock() &&  material.name().contains(NAME_PATTERN);
  }
}
