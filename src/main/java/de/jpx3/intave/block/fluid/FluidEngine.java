package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.NativeVector;
import de.jpx3.intave.shade.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;

public abstract class FluidEngine {
  public final WrappedFluid fluidAt(User user, double x, double y, double z) {
    return fluidAt(user, WrappedMathHelper.floor(x), WrappedMathHelper.floor(y), WrappedMathHelper.floor(z));
  }

  protected abstract WrappedFluid fluidAt(User user, int x, int y, int z);

  protected abstract NativeVector flowVectorAt(User user, int x, int y, int z);

  protected boolean handleFluidAcceleration(User user, BoundingBox boundingBox) {
    World world = user.player().getWorld();
    MovementMetadata movementData = user.meta().movement();
    BoundingBox wrappedBoundingBox = boundingBox.shrink(0.001D);
    int minX = WrappedMathHelper.floor(wrappedBoundingBox.minX);
    int minY = WrappedMathHelper.floor(wrappedBoundingBox.minY);
    int minZ = WrappedMathHelper.floor(wrappedBoundingBox.minZ);
    int maxX = WrappedMathHelper.ceil(wrappedBoundingBox.maxX);
    int maxY = WrappedMathHelper.ceil(wrappedBoundingBox.maxY);
    int maxZ = WrappedMathHelper.ceil(wrappedBoundingBox.maxZ);
    boolean inWater = false;
    int countedWaterCollisions = 0;
    NativeVector waterFlowTotal = NativeVector.ZERO;
    double d0 = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material blockClientSide = VolatileBlockAccess.safeTypeAccess(user, world, x, y, z);
          WrappedFluid wrappedFluid = fluidAt(user, x, y, z);
          if (wrappedFluid.isIn(FluidTag.WATER)) {
            double d1 = (float) y + wrappedFluid.height();
            if (d1 >= wrappedBoundingBox.minY) {
              inWater = true;
              d0 = Math.max(d1 - wrappedBoundingBox.minY, d0);
              NativeVector flowVector = flowVectorAt(user, x, y, z);
              if (d0 < 0.4) {
                flowVector = flowVector.scale(d0);
              }
              waterFlowTotal = waterFlowTotal.add(flowVector);
              ++countedWaterCollisions;
            }
          } else if (MaterialMagic.isWater(blockClientSide)) {
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