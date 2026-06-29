package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.WATERFLOW_PUSH;

final class v8Waterflow implements FluidFlow {
  @Override
  public boolean applyFlowTo(
    User user, SimulationEnvironment environment,
    Motion baseMotion, BoundingBox boundingBox
  ) {
    Player player = user.player();
    World world = player.getWorld();
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
      baseMotion.motionX += flowVector.motionX * factor;
      baseMotion.motionY += flowVector.motionY * factor;
      baseMotion.motionZ += flowVector.motionZ * factor;
      environment.activeTick(WATERFLOW_PUSH);
    }
    return inWater;
  }

  @Override
  public double fluidDepthAt(User user, BoundingBox boundingBox) {
    return 0.0D; // not needed here
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
            flowVector.add((position.x - pos.x) * k, (position.y - pos.y) * k, (position.z - pos.z) * k);
          }
        }
      } else {
        int l = j - i;
        flowVector.add((position.x - pos.x) * l, (position.y - pos.y) * l, (position.z - pos.z) * l);
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
    Material clientSideBlock = VolatileBlockAccess.typeAccess(user, world, pos.x, pos.y, pos.z);
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.x, pos.y, pos.z);
    Fluid fluid = Fluids.fluidStateOf(clientSideBlock, variantIndex);
    return fluid.isOfWater() ? fluid.level() : -1;
  }

  private static boolean blocksMovement(User user, BlockPosition position) {
    Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position.x, position.y, position.z);
    return MaterialMagic.blocksMovement(type);
  }

  private static boolean isBlockSolid(User user, BlockPosition pos, Direction side) {
    World world = user.player().getWorld();
    Material type = VolatileBlockAccess.typeAccess(user, world, pos.x, pos.y, pos.z);
    int variantIndex = VolatileBlockAccess.variantIndexAccess(user, world, pos.x, pos.y, pos.z);
    return !MaterialMagic.couldContainLiquid(type, variantIndex) && (side == Direction.UP || (type != Material.ICE && MaterialMagic.blockSolid(type)));
  }

  public static float resolveLiquidHeightPercentage(int level) {
    if (level >= 8) {
      level = 0;
    }
    return (float) (level + 1) / 9.0F;
  }
}
