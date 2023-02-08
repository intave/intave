package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.ClientMathHelper.ceil;
import static de.jpx3.intave.share.ClientMathHelper.floor;

final class v12FluidResolver extends FluidResolver {
  @Override
  protected Fluid fluidAt(User user, int x, int y, int z) {
    Player player = user.player();
    Block block = VolatileBlockAccess.blockAccess(user.player().getWorld(), x, y, z);
    if (block.getY() < WorldHeight.LOWER_WORLD_LIMIT) {
      return Fluid.empty();
    }
    Material type = BlockTypeAccess.typeAccess(block, player);
    int variantIndex = BlockVariantNativeAccess.variantAccess(block);
    int level = levelOfLiquidAt(type, variantIndex);
    float height = LegacyWaterflow.resolveLiquidHeightPercentage(level);
    FluidTag fluidTag = FluidTag.EMPTY;
    if (MaterialMagic.isWater(type)) {
      fluidTag = FluidTag.WATER;
    } else if (MaterialMagic.isLava(type)) {
      fluidTag = FluidTag.LAVA;
    }
    return Fluid.of(fluidTag, true, height);
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

    int minX = floor(entityBoundingBox.minX);
    int minY = floor(entityBoundingBox.minY);
    int minZ = floor(entityBoundingBox.minZ);
    int maxX = ceil(entityBoundingBox.maxX);
    int maxY = ceil(entityBoundingBox.maxY);
    int maxZ = ceil(entityBoundingBox.maxZ);

    double d0 = 0;
    boolean inWater = false;
    NativeVector waterFlow = new NativeVector(0, 0, 0);
    int countedWaterCollisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
          Material clientSideBlock = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          boolean waterServerSide = MaterialMagic.isWater(BlockTypeAccess.typeAccess(block, player));
          boolean waterClientSide = MaterialMagic.isWater(clientSideBlock);
          if (waterServerSide) {
            int liquidLevel = levelOfLiquidAt(BlockTypeAccess.typeAccess(block), BlockVariantNativeAccess.variantAccess(block));
            double height = 1 - LegacyWaterflow.resolveLiquidHeightPercentage(liquidLevel);
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
      movementData.baseMotionX += waterFlow.xCoord * d2;
      movementData.baseMotionY += waterFlow.yCoord * d2;
      movementData.baseMotionZ += waterFlow.zCoord * d2;
      movementData.pastPushedByWaterFlow = 0;
    }

    return inWater;
  }

  private static int levelOfLiquidAt(Material material, int variantIndex) {
    if (MaterialMagic.isLiquid(material)) {
      return BlockVariantRegister.variantOf(material, variantIndex).propertyOf("level");
    } else {
      return -1;
    }
  }
}