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
    MovementMetadata movementData = user.meta().movement();
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);

    double largestFluidDepth = 0;
    boolean inWater = false;
    Motion waterFlowTotal = null;
    int countedWaterCollisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = VolatileBlockAccess.fluidAccess(user, x, y, z);
          if (fluid.isOfWater()) {
            double fluidSurfaceY = (float) y + fluid.height();
            if (fluidSurfaceY >= wrappedBoundingBox.minY) {
              inWater = true;
              largestFluidDepth = Math.max(fluidSurfaceY - wrappedBoundingBox.minY, largestFluidDepth);
              Motion pushMotion = pushMotionAt(user, x, y, z);
              if (largestFluidDepth < 0.4) {
                pushMotion.multiply(largestFluidDepth);
              }
              if (waterFlowTotal == null) {
                waterFlowTotal = new Motion();
              }
              waterFlowTotal.add(pushMotion);
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

      double d2 = 0.014D;
      waterFlowTotal.multiply(d2);

      if (Math.abs(movementData.baseMotionX) < 0.003D &&
        Math.abs(movementData.baseMotionZ) < 0.003D &&
        waterFlowTotal.length() < 0.0045000000000000005D
      ) {
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
  public double fluidDepthAt(User user, BoundingBox boundingBox) {
    double largestFluidDepth = 0;
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = floor(wrappedBoundingBox.minX);
    int minY = floor(wrappedBoundingBox.minY);
    int minZ = floor(wrappedBoundingBox.minZ);
    int maxX = ceil(wrappedBoundingBox.maxX);
    int maxY = ceil(wrappedBoundingBox.maxY);
    int maxZ = ceil(wrappedBoundingBox.maxZ);
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = VolatileBlockAccess.fluidAccess(user, x, y, z);
          if (fluid.isOfWater()) {
            double fluidSurfaceY = (float) y + fluid.height();
            if (fluidSurfaceY >= wrappedBoundingBox.minY) {
              largestFluidDepth = Math.max(fluidSurfaceY - wrappedBoundingBox.minY, largestFluidDepth);
            }
          }
        }
      }
    }
    return largestFluidDepth;
  }

  @Override
  public Motion pushMotionAt(User user, int blockX, int blockY, int blockZ) {
    double flowX = 0.0D;
    double flowY = 0.0D;

    Fluid fluid = VolatileBlockAccess.fluidAccess(user, blockX, blockY, blockZ);
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPosition position = new BlockPosition(blockX, blockY, blockZ).offset(direction);
      Fluid adjacentFluid = VolatileBlockAccess.fluidAccess(user, position.xCoord, position.yCoord, position.zCoord);

      if (fluid.affectsFlow(adjacentFluid)) {
        float adjacentHeight = adjacentFluid.height();
        float heightDifference = 0.0F;

        if (adjacentHeight == 0) {
          if (!blocksMovement(user, position)) {
            BlockPosition below = position.down();
            Fluid belowFluid = VolatileBlockAccess.fluidAccess(user, below.xCoord, below.yCoord, below.zCoord);
            if (fluid.affectsFlow(belowFluid)) {
              adjacentHeight = belowFluid.height();
              if (adjacentHeight > 0) {
                heightDifference = fluid.height() - (adjacentHeight - 0.8888889F);
              }
            }
          }
        } else if (adjacentHeight > 0) {
          heightDifference = fluid.height() - adjacentHeight;
        }

        if (heightDifference != 0) {
          flowX += direction.offsetX() * heightDifference;
          flowY += direction.offsetZ() * heightDifference;
        }
      }
    }

    Motion flow = new Motion(flowX, 0.0D, flowY);
    if (fluid.falling()) {
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        BlockPosition position = new BlockPosition(blockX, blockY, blockZ).offset(direction);
        if (isBlockSolid(user, position, direction) || isBlockSolid(user, position.up(), direction)) {
          flow.normalize().add(0.0D, -6.0D, 0.0D);
          break;
        }
      }
    }

    return flow.normalize();
  }

  private static boolean blocksMovement(User user, BlockPosition position) {
    Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position.xCoord, position.yCoord, position.zCoord);
    return MaterialMagic.blocksMovement(type);
  }

  private static boolean isBlockSolid(User user, BlockPosition pos, Direction side) {
    World world = user.player().getWorld();
    Material type = VolatileBlockAccess.typeAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    if (MaterialMagic.couldContainLiquid(type, variantIndex)) {
      return false;
    }
    return side == Direction.UP || (type != Material.ICE && MaterialMagic.blockSolid(type));
  }
}
