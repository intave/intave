package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class v8Waterflow implements FluidFlow {
  @Override
  public boolean applyFlowTo(User user, BoundingBox boundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    int minX = ClientMath.floor(boundingBox.minX);
    int minY = ClientMath.floor(boundingBox.minY);
    int minZ = ClientMath.floor(boundingBox.minZ);
    int maxX = ClientMath.floor(boundingBox.maxX + 1.0D);
    int maxY = ClientMath.floor(boundingBox.maxY + 1.0D);
    int maxZ = ClientMath.floor(boundingBox.maxZ + 1.0D);

    boolean inWater = false;
    Motion flowVector = null;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material type = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, x, y, z);
          Fluid fluid = Fluids.fluidStateOf(type, variantIndex);
          if (fluid.isOfWater()) {
            double d0 = (float) (y + 1) - resolveLiquidHeightPercentage(fluid.level());
            if ((double) maxY >= d0) {
              inWater = true;
              if (flowVector == null) {
                flowVector = new Motion();
              }
              flowVector = modifyAcceleration(user, new BlockPosition(x, y, z), flowVector);
            }
          }
        }
      }
    }
    if (inWater && flowVector != null && flowVector.length() > 0.0D) {
      flowVector.normalize();
      double factor = 0.014D;
      movementData.baseMotionX += flowVector.motionX * factor;
      movementData.baseMotionY += flowVector.motionY * factor;
      movementData.baseMotionZ += flowVector.motionZ * factor;
      movementData.pastPushedByWaterFlow = 0;
    }
    return inWater;
  }

  public Motion modifyAcceleration(User user, BlockPosition pos, Motion motion) {
    return motion.add(pushMotionAt(user, pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
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
