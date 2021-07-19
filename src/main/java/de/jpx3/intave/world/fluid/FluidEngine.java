package de.jpx3.intave.world.fluid;

import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import org.bukkit.Material;
import org.bukkit.World;

public abstract class FluidEngine {
  public final WrappedFluid fluidAt(User user, double x, double y, double z) {
    return fluidAt(user, WrappedMathHelper.floor(x), WrappedMathHelper.floor(y), WrappedMathHelper.floor(z));
  }

  protected abstract WrappedFluid fluidAt(User user, int x, int y, int z);

  protected abstract WrappedVector flowVectorAt(User user, int x, int y, int z);

  protected boolean handleFluidAcceleration(User user, WrappedAxisAlignedBB boundingBox) {
    World world = user.player().getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    WrappedAxisAlignedBB wrappedAxisAlignedBB = boundingBox.shrink(0.001D);
    int minX = WrappedMathHelper.floor(wrappedAxisAlignedBB.minX);
    int minY = WrappedMathHelper.floor(wrappedAxisAlignedBB.minY);
    int minZ = WrappedMathHelper.floor(wrappedAxisAlignedBB.minZ);
    int maxX = WrappedMathHelper.ceil(wrappedAxisAlignedBB.maxX);
    int maxY = WrappedMathHelper.ceil(wrappedAxisAlignedBB.maxY);
    int maxZ = WrappedMathHelper.ceil(wrappedAxisAlignedBB.maxZ);
    boolean inWater = false;
    int countedWaterCollisions = 0;
    WrappedVector waterFlowTotal = WrappedVector.ZERO;
    double d0 = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material blockClientSide = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z);
          WrappedFluid wrappedFluid = fluidAt(user, x, y, z);
          if (wrappedFluid.isIn(FluidTag.WATER)) {
            double d1 = (float) y + wrappedFluid.height();
            if (d1 >= wrappedAxisAlignedBB.minY) {
              inWater = true;
              d0 = Math.max(d1 - wrappedAxisAlignedBB.minY, d0);
              WrappedVector flowVector = flowVectorAt(user, x, y, z);
              if (d0 < 0.4) {
                flowVector = flowVector.scale(d0);
              }
              waterFlowTotal = waterFlowTotal.add(flowVector);
              ++countedWaterCollisions;
            }
          } else if (Materials.isWater(blockClientSide)) {
            inWater = true;
          }
        }
      }
    }

    if (waterFlowTotal.length() > 0.0D) {
      if (countedWaterCollisions > 0) {
        waterFlowTotal = waterFlowTotal.scale(1.0D / (double) countedWaterCollisions);
      }
      waterFlowTotal = waterFlowTotal.normalize();
      double d2 = 0.014D;
      movementData.physicsMotionX += waterFlowTotal.xCoord * d2;
      movementData.physicsMotionY += waterFlowTotal.yCoord * d2;
      movementData.physicsMotionZ += waterFlowTotal.zCoord * d2;
      movementData.pastPushedByWaterFlow = 0;
    }

    return inWater;
  }
}