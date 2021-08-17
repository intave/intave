package de.jpx3.intave.world.fluid.resolver;

import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.fluid.FluidEngine;
import de.jpx3.intave.world.fluid.FluidTag;
import de.jpx3.intave.world.fluid.LegacyWaterflow;
import de.jpx3.intave.world.fluid.WrappedFluid;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.WrappedBlockPosition;
import de.jpx3.intave.world.wrapper.WrappedMathHelper;
import de.jpx3.intave.world.wrapper.WrappedVector;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class v12FluidResolver extends FluidEngine {
  @Override
  protected WrappedFluid fluidAt(User user, int x, int y, int z) {
    Player player = user.player();
    Block block = BukkitBlockAccess.blockAccess(user.player().getWorld(), x, y, z);
    if (block.getY() < 0) {
      return WrappedFluid.empty();
    }
    float height = LegacyWaterflow.resolveLiquidHeightPercentage(BlockDataAccess.dataAccess(block));
    Material type = BlockTypeAccess.typeAccess(block, player);
    FluidTag fluidTag = FluidTag.EMPTY;
    if (Materials.isWater(type)) {
      fluidTag = FluidTag.WATER;
    } else if (Materials.isLava(type)) {
      fluidTag = FluidTag.LAVA;
    }
    return WrappedFluid.construct(fluidTag, height);
  }

  @Override
  protected WrappedVector flowVectorAt(User user, int x, int y, int z) {
    return null;
  }

  @Override
  protected boolean handleFluidAcceleration(User user, WrappedAxisAlignedBB boundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
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
          boolean waterServerSide = Materials.isWater(BlockTypeAccess.typeAccess(block, player));
          boolean waterClientSide = Materials.isWater(clientSideBlock);
          if (waterServerSide) {
            double height = 1 - LegacyWaterflow.resolveLiquidHeightPercentage(block.getData());
            double d1 = (float) y + height;
            if (d1 >= entityBoundingBox.minY) {
              inWater = true;
              d0 = Math.max(d1 - entityBoundingBox.minY, d0);
              WrappedVector flowVector = LegacyWaterflow.modifyAcceleration(user, new WrappedBlockPosition(x, y, z), new WrappedVector(0, 0, 0));
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
}