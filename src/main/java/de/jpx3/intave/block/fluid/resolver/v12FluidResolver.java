package de.jpx3.intave.block.fluid.resolver;

import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.FluidEngine;
import de.jpx3.intave.block.fluid.FluidTag;
import de.jpx3.intave.block.fluid.LegacyWaterflow;
import de.jpx3.intave.block.fluid.WrappedFluid;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BlockPosition;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.NativeVector;
import de.jpx3.intave.shade.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class v12FluidResolver extends FluidEngine {
  @Override
  protected WrappedFluid fluidAt(User user, int x, int y, int z) {
    Player player = user.player();
    Block block = VolatileBlockAccess.unsafe__BlockAccess(user.player().getWorld(), x, y, z);
    if (block.getY() < 0) {
      return WrappedFluid.empty();
    }
    float height = LegacyWaterflow.resolveLiquidHeightPercentage(BlockVariantAccess.variantAccess(block));
    Material type = BlockTypeAccess.typeAccess(block, player);
    FluidTag fluidTag = FluidTag.EMPTY;
    if (MaterialMagic.isWater(type)) {
      fluidTag = FluidTag.WATER;
    } else if (MaterialMagic.isLava(type)) {
      fluidTag = FluidTag.LAVA;
    }
    return WrappedFluid.construct(fluidTag, height);
  }

  @Override
  protected NativeVector flowVectorAt(User user, int x, int y, int z) {
    return null;
  }

  @Override
  protected boolean handleFluidAcceleration(User user, BoundingBox boundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    BoundingBox entityBoundingBox = boundingBox.shrink(0.001D);

    int minX = WrappedMathHelper.floor(entityBoundingBox.minX);
    int minY = WrappedMathHelper.floor(entityBoundingBox.minY);
    int minZ = WrappedMathHelper.floor(entityBoundingBox.minZ);
    int maxX = WrappedMathHelper.ceil(entityBoundingBox.maxX);
    int maxY = WrappedMathHelper.ceil(entityBoundingBox.maxY);
    int maxZ = WrappedMathHelper.ceil(entityBoundingBox.maxZ);

    double d0 = 0;
    boolean inWater = false;
    NativeVector waterFlow = new NativeVector(0, 0, 0);
    int countedWaterCollisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Block block = VolatileBlockAccess.unsafe__BlockAccess(world, x, y, z);
          Material clientSideBlock = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          boolean waterServerSide = MaterialMagic.isWater(BlockTypeAccess.typeAccess(block, player));
          boolean waterClientSide = MaterialMagic.isWater(clientSideBlock);
          if (waterServerSide) {
            double height = 1 - LegacyWaterflow.resolveLiquidHeightPercentage(BlockVariantAccess.variantAccess(block));
            double d1 = (float) y + height;
            if (d1 >= entityBoundingBox.minY) {
              inWater = true;
              d0 = Math.max(d1 - entityBoundingBox.minY, d0);
              NativeVector flowVector = LegacyWaterflow.modifyAcceleration(user, new BlockPosition(x, y, z), new NativeVector(0, 0, 0));
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