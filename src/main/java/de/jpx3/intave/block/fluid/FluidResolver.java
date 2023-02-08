package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;

import static de.jpx3.intave.share.ClientMathHelper.ceil;
import static de.jpx3.intave.share.ClientMathHelper.floor;

public abstract class FluidResolver {
  public final Fluid fluidAt(User user, double x, double y, double z) {
    return fluidAt(user, floor(x), floor(y), floor(z));
  }

  protected abstract Fluid fluidAt(User user, int x, int y, int z);

  protected abstract NativeVector flowVectorAt(User user, int x, int y, int z);

  protected boolean handleFluidAcceleration(User user, BoundingBox boundingBox) {
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
    NativeVector waterFlowTotal = NativeVector.ZERO;
    double d0 = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material blockClientSide = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          Fluid fluid = fluidAt(user, x, y, z);
          if (fluid.isOfWater()) {
            double d1 = (float) y + fluid.height();
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
      movementData.baseMotionX += waterFlowTotal.xCoord * d2;
      movementData.baseMotionY += waterFlowTotal.yCoord * d2;
      movementData.baseMotionZ += waterFlowTotal.zCoord * d2;
      movementData.pastPushedByWaterFlow = 0;
    }

    return inWater;
  }
}