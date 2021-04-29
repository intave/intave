package de.jpx3.intave.world.waterflow;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.detect.checks.movement.physics.LegacyWaterPhysics;
import de.jpx3.intave.tools.client.MaterialLogic;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class UnknownWaterflowEngine extends AbstractWaterflowEngine {
  @Override
  public boolean fluidStateEmpty(User user, double x, double y, double z) {
    World world = user.player().getWorld();
    Block block = BukkitBlockAccess.blockAccess(world, WrappedMathHelper.floor(x), WrappedMathHelper.floor(y), WrappedMathHelper.floor(z));
    return !MaterialLogic.isLiquid(block.getType());
  }

  @Override
  public boolean handleFluidAcceleration(User user, WrappedAxisAlignedBB boundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    WrappedAxisAlignedBB entityBoundingBox = boundingBox.shrink(0.001D);

    int minX = WrappedMathHelper.floor(entityBoundingBox.minX);
    int minY = WrappedMathHelper.floor(entityBoundingBox.minY);
    int minZ = WrappedMathHelper.floor(entityBoundingBox.minZ);
    int maxX = WrappedMathHelper.ceil(entityBoundingBox.maxX);
    int maxY = WrappedMathHelper.ceil(entityBoundingBox.maxY);
    int maxZ = WrappedMathHelper.ceil(entityBoundingBox.maxZ);

    double d0 = 0;
    boolean inWater = false;
    WrappedVector waterFlow = new WrappedVector(0, 0, 0);
    int countedWaterCollisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Block block = BukkitBlockAccess.blockAccess(world, x, y, z);
          Material clientSideBlock = BukkitBlockAccess.cacheAppliedTypeAccess(user, world, x, y, z);
          boolean waterServerSide = MaterialLogic.isWater(block.getType());
          boolean waterClientSide = MaterialLogic.isWater(clientSideBlock);
          if (waterServerSide) {
            double height = 1 - LegacyWaterPhysics.resolveLiquidHeightPercentage(block.getData());
            double d1 = (float) y + height;
            if (d1 >= entityBoundingBox.minY) {
              inWater = true;
              d0 = Math.max(d1 - entityBoundingBox.minY, d0);
              WrappedVector flowVector = LegacyWaterPhysics.modifyAcceleration(user, new WrappedBlockPosition(x, y, z), new WrappedVector(0, 0, 0));
              if (d0 < 0.4) {
                flowVector = flowVector.scale(d0);
              }
              waterFlow = waterFlow.add(flowVector);
              ++countedWaterCollisions;
            }
          } else if (waterClientSide) {
            inWater = true;
          }
        }
      }
    }

    if (waterFlow.length() > 0.0D) {
      if (countedWaterCollisions > 0) {
        waterFlow = waterFlow.scale(1.0D / countedWaterCollisions);
      }
      waterFlow = waterFlow.normalize();
      double d2 = 0.014D;
      movementData.physicsMotionX += waterFlow.xCoord * d2;
      movementData.physicsMotionY += waterFlow.yCoord * d2;
      movementData.physicsMotionZ += waterFlow.zCoord * d2;
      movementData.pastPushedByWaterFlow = 0;
    }

    return inWater;
  }

  @Override
  public boolean areEyesInFluid(User user, double positionX, double positionY, double positionZ) {
    Player player = user.player();
    World world = player.getWorld();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    float eyeHeight = movementData.eyeHeight();
    double posYEye = positionY + eyeHeight;
    double d0 = posYEye - (double) 0.11111111F;
    WrappedVector vector3d = new WrappedVector(positionX, d0, positionZ);
    Block block = BukkitBlockAccess.blockAccess(world, vector3d.xCoord, vector3d.yCoord, vector3d.zCoord);
    if (MaterialLogic.isWater(block.getType())) {
      double d1 = vector3d.yCoord + 1 - LegacyWaterPhysics.resolveLiquidHeightPercentage(block.getData());
      return d1 > d0;
    }
    return false;
  }

  @Override
  @Deprecated
  public Object blockPositionOf(int x, int y, int z) {
    return null;
  }

  @Override
  @Deprecated
  public boolean fluidTaggedWithWater(Object fluidState) {
    return false;
  }

  @Override
  @Deprecated
  public Object fluidState(User user, Object blockPosition) {
    return null;
  }

  @Override
  @Deprecated
  public float fluidHeight(Object fluidState) {
    return 0;
  }

  @Override
  @Deprecated
  public WrappedVector resolveFlowVector(Object fluidState, Object world, Object blockPosition) {
    return null;
  }

  @Override
  public boolean appliesToAtLeast(MinecraftVersion currentVersion) {
    return true;
  }
}