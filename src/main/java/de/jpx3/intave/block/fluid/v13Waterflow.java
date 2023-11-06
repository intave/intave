package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;

import static de.jpx3.intave.share.ClientMath.ceil;
import static de.jpx3.intave.share.ClientMath.floor;

final class v13Waterflow implements FluidFlow {
  @Override
  public boolean applyFlowTo(User user, BoundingBox boundingBox) {
    World world = user.player().getWorld();
    MovementMetadata movementData = user.meta().movement();
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);
    boolean inWater = false;
    int countedWaterCollisions = 0;
    Motion waterFlowTotal = null;
    double d0 = 0;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material type = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, x, y, z);
          Fluid fluid = Fluids.fluidStateOf(type, variantIndex);
          if (fluid.isOfWater()) {
            double d1 = (float) y + fluid.height();
            if (d1 >= wrappedBoundingBox.minY) {
              inWater = true;
              d0 = Math.max(d1 - wrappedBoundingBox.minY, d0);
              Motion pushMotion = pushMotionAt(user, x, y, z);
              if (d0 < 0.4) {
                pushMotion.multiply(d0);
              }
              if (waterFlowTotal == null) {
                waterFlowTotal = new Motion();
              }
              waterFlowTotal = waterFlowTotal.add(pushMotion);
              ++countedWaterCollisions;
            }
          }
        }
      }
    }

    if (waterFlowTotal != null && waterFlowTotal.length() > 0.0D) {
      if (countedWaterCollisions > 0) {
        waterFlowTotal.multiply(1.0D / (double) countedWaterCollisions);
      }
//      waterFlowTotal.normalize();
      double d2 = 0.014D;
//      movementData.baseMotionX += waterFlowTotal.motionX * d2;
//      movementData.baseMotionY += waterFlowTotal.motionY * d2;
//      movementData.baseMotionZ += waterFlowTotal.motionZ * d2;
      waterFlowTotal.multiply(d2);

      if (Math.abs(waterFlowTotal.motionX) < 0.003D && Math.abs(waterFlowTotal.motionZ) < 0.003D && waterFlowTotal.length() < 0.0045000000000000005D) {
        waterFlowTotal.normalize().multiply(0.0045000000000000005D);
      }

      movementData.baseMotionX += waterFlowTotal.motionX;
      movementData.baseMotionY += waterFlowTotal.motionY;
      movementData.baseMotionZ += waterFlowTotal.motionZ;

      movementData.pastPushedByWaterFlow = 0;
    }
    return inWater;
  }

  @Override
  public Motion pushMotionAt(User user, int blockX, int blockY, int blockZ) {
    Motion flowVector = new Motion(0.0D, 0.0D, 0.0D);
    BlockPosition pos = new BlockPosition(blockX, blockY, blockZ);
    int i = resolveEffectiveFlowDecay(user, pos);
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPosition position = pos.offset(direction);
      int j = resolveEffectiveFlowDecay(user, position);
      if (j < 0) {
        if (!blocksMovement(user, pos)) {
          j = resolveEffectiveFlowDecay(user, position.down());
          if (j >= 0) {
            int k = j - (i - 8);
            flowVector.add((position.xCoord - pos.xCoord) * k, (position.yCoord - pos.yCoord) * k, (position.zCoord - pos.zCoord) * k);
          }
        }
      } else {
        int l = j - i;
        flowVector.add((position.xCoord - pos.xCoord) * l, (position.yCoord - pos.yCoord) * l, (position.zCoord - pos.zCoord) * l);
      }
    }
    if (VolatileBlockAccess.fluidAccess(user, pos).falling()) {
      for (Direction facing : Direction.Plane.HORIZONTAL) {
        BlockPosition blockpos = pos.offset(facing);
        if (isBlockSolid(user, blockpos, facing) || isBlockSolid(user, blockpos.up(), facing)) {
          flowVector.normalize().add(0.0D, -6.0D, 0.0D);
          break;
        }
      }
    }
    return flowVector.normalize();
  }

  private static int resolveEffectiveFlowDecay(User user, BlockPosition pos) {
    int i = resolveLevel(user, pos);
    return i >= 8 ? 0 : i;
  }

  private static int resolveLevel(User user, BlockPosition pos) {
    World world = user.player().getWorld();
    Material clientSideBlock = VolatileBlockAccess.typeAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    Fluid fluid = Fluids.fluidStateOf(clientSideBlock, variantIndex);
    return fluid.isOfWater() ? fluid.level() : -1;
  }

  private static boolean blocksMovement(User user, BlockPosition position) {
    Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position.xCoord, position.yCoord, position.zCoord);
    return MaterialMagic.blocksMovement(type);
  }

  private static boolean isBlockSolid(User user, BlockPosition pos, Direction side) {
    World world = user.player().getWorld();
    Material type = VolatileBlockAccess.typeAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    return !MaterialMagic.couldContainLiquid(type, variantIndex) && (side == Direction.UP || (type != Material.ICE && MaterialMagic.blockSolid(type)));
  }

  public static float resolveLiquidHeightPercentage(int level) {
    if (level >= 8) {
      level = 0;
    }
    return (float) (level + 1) / 9.0F;
  }
}
