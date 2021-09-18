package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.shade.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class LegacyWaterflow {
  public static boolean handleMaterialAcceleration(User user, BoundingBox boundingBox) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX + 1.0D);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY + 1.0D);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ + 1.0D);
    boolean inWater = false;
    NativeVector flowVector = null;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Material type = VolatileBlockAccess.typeAccess(user, world, x, y, z);
          if (MaterialMagic.isWater(type)) {
            int level = VolatileBlockAccess.variantIndexAccess(user, world, x, y, z);
            double d0 = (float) (y + 1) - resolveLiquidHeightPercentage(level);
            if ((double) maxY >= d0) {
              inWater = true;
              if (flowVector == null) {
                flowVector = new NativeVector(0, 0, 0);
              }
              flowVector = modifyAcceleration(user, new BlockPosition(x, y, z), flowVector);
            }
          }
        }
      }
    }
    if (inWater && flowVector != null && flowVector.lengthVector() > 0.0D) {
      flowVector = flowVector.normalize();
      double factor = 0.014D;
      movementData.physicsMotionX += flowVector.xCoord * factor;
      movementData.physicsMotionY += flowVector.yCoord * factor;
      movementData.physicsMotionZ += flowVector.zCoord * factor;
      movementData.pastPushedByWaterFlow = 0;
    }
    return inWater;
  }

  public static NativeVector modifyAcceleration(User user, BlockPosition pos, NativeVector motion) {
    return motion.add(flowVector(user, pos));
  }

  private static NativeVector flowVector(User user, BlockPosition pos) {
    NativeVector vec3 = new NativeVector(0.0D, 0.0D, 0.0D);
    int i = resolveEffectiveFlowDecay(user, pos);
    for (EnumDirection enumDirection : EnumDirection.Plane.HORIZONTAL) {
      BlockPosition position = pos.offset(enumDirection);
      int j = resolveEffectiveFlowDecay(user, position);
      if (j < 0) {
        if (!blocksMovement(user, pos)) {
          j = resolveEffectiveFlowDecay(user, position.down());
          if (j >= 0) {
            int k = j - (i - 8);
            vec3 = vec3.addVector((position.xCoord - pos.xCoord) * k, (position.yCoord - pos.yCoord) * k, (position.zCoord - pos.zCoord) * k);
          }
        }
      } else {
        int l = j - i;
        vec3 = vec3.addVector((position.xCoord - pos.xCoord) * l, (position.yCoord - pos.yCoord) * l, (position.zCoord - pos.zCoord) * l);
      }
    }
    if (resolveLevel(user, pos) >= 8) {
      for (EnumDirection enumfacing1 : EnumDirection.Plane.HORIZONTAL) {
        BlockPosition blockpos1 = pos.offset(enumfacing1);
        if (isBlockSolid(user, blockpos1, enumfacing1) || isBlockSolid(user, blockpos1.up(), enumfacing1)) {
          vec3 = vec3.normalize().addVector(0.0D, -6.0D, 0.0D);
          break;
        }
      }
    }
    return vec3.normalize();
  }

  private static int resolveEffectiveFlowDecay(User user, BlockPosition pos) {
    int i = resolveLevel(user, pos);
    return i >= 8 ? 0 : i;
  }

  private static int resolveLevel(User user, BlockPosition pos) {
    World world = user.player().getWorld();
    Material clientSideBlock = VolatileBlockAccess.typeAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    return MaterialMagic.isWater(clientSideBlock) ? VolatileBlockAccess.variantIndexAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord) : -1;
  }

  private static boolean blocksMovement(User user, BlockPosition position) {
    Material type = VolatileBlockAccess.typeAccess(user, user.player().getWorld(), position.xCoord, position.yCoord, position.zCoord);//blockAt(world, position).getType();
    return MaterialMagic.blocksMovement(type);
  }

  private static boolean isBlockSolid(User user, BlockPosition pos, EnumDirection side) {
    World world = user.player().getWorld();
    Material type = VolatileBlockAccess.typeAccess(user, world, pos.xCoord, pos.yCoord, pos.zCoord);
    return !MaterialMagic.isLiquid(type) && (side == EnumDirection.UP || (type != Material.ICE && MaterialMagic.blockSolid(type)));
  }

  public static float resolveLiquidHeightPercentage(int level) {
    if (level >= 8) {
      level = 0;
    }
    return (float) (level + 1) / 9.0F;
  }
}