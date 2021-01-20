package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

public final class LegacyVersionRaytracer implements VersionRaytracer {
  @Override
  @PatchyAutoTranslation
  public WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    Vec3D nativeEyeVector = (Vec3D) eyeVector.convertToNativeVec3();
    Vec3D nativeTargetVector = (Vec3D) targetVector.convertToNativeVec3();
//    MovingObjectPosition movingObjectPosition = rayTrace(player, handle, nativeEyeVector, nativeTargetVector, false, false, false);
    MovingObjectPosition movingObjectPosition = handle.rayTrace(nativeEyeVector, nativeTargetVector, false, false, false);
    return WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public MovingObjectPosition rayTrace(Player player, net.minecraft.server.v1_8_R3.WorldServer world, Vec3D vec3d, Vec3D vec3d1, boolean flag, boolean flag1, boolean flag2) {
    if (!Double.isNaN(vec3d.a) && !Double.isNaN(vec3d.b) && !Double.isNaN(vec3d.c)) {
      if (!Double.isNaN(vec3d1.a) && !Double.isNaN(vec3d1.b) && !Double.isNaN(vec3d1.c)) {
        int i = MathHelper.floor(vec3d1.a);
        int j = MathHelper.floor(vec3d1.b);
        int k = MathHelper.floor(vec3d1.c);
        int l = MathHelper.floor(vec3d.a);
        int i1 = MathHelper.floor(vec3d.b);
        int j1 = MathHelper.floor(vec3d.c);
        BlockPosition blockposition = new BlockPosition(l, i1, j1);
        IBlockData iblockdata = world.getType(blockposition);
        Block block = iblockdata.getBlock();
        MovingObjectPosition movingobjectposition1;
        if ((!flag1 || block.a(world, blockposition, iblockdata) != null) && block.a(iblockdata, flag)) {
          movingobjectposition1 = /*Raytracer.isIgnored(player, new com.comphenix.protocol.wrappers.BlockPosition(blockposition.getX(), blockposition.getY(), blockposition.getZ())) ? null :*/ block.a(world, blockposition, vec3d, vec3d1);
          if (movingobjectposition1 != null) {
            return movingobjectposition1;
          }
        }

        movingobjectposition1 = null;
        int var16 = 200;

        while(var16-- >= 0) {
          if (Double.isNaN(vec3d.a) || Double.isNaN(vec3d.b) || Double.isNaN(vec3d.c)) {
            return null;
          }

          if (l == i && i1 == j && j1 == k) {
            return flag2 ? movingobjectposition1 : null;
          }

          boolean flag3 = true;
          boolean flag4 = true;
          boolean flag5 = true;
          double d0 = 999.0D;
          double d1 = 999.0D;
          double d2 = 999.0D;
          if (i > l) {
            d0 = (double)l + 1.0D;
          } else if (i < l) {
            d0 = (double)l + 0.0D;
          } else {
            flag3 = false;
          }

          if (j > i1) {
            d1 = (double)i1 + 1.0D;
          } else if (j < i1) {
            d1 = (double)i1 + 0.0D;
          } else {
            flag4 = false;
          }

          if (k > j1) {
            d2 = (double)j1 + 1.0D;
          } else if (k < j1) {
            d2 = (double)j1 + 0.0D;
          } else {
            flag5 = false;
          }

          double d3 = 999.0D;
          double d4 = 999.0D;
          double d5 = 999.0D;
          double d6 = vec3d1.a - vec3d.a;
          double d7 = vec3d1.b - vec3d.b;
          double d8 = vec3d1.c - vec3d.c;
          if (flag3) {
            d3 = (d0 - vec3d.a) / d6;
          }

          if (flag4) {
            d4 = (d1 - vec3d.b) / d7;
          }

          if (flag5) {
            d5 = (d2 - vec3d.c) / d8;
          }

          if (d3 == -0.0D) {
            d3 = -1.0E-4D;
          }

          if (d4 == -0.0D) {
            d4 = -1.0E-4D;
          }

          if (d5 == -0.0D) {
            d5 = -1.0E-4D;
          }

          EnumDirection enumdirection;
          if (d3 < d4 && d3 < d5) {
            enumdirection = i > l ? EnumDirection.WEST : EnumDirection.EAST;
            vec3d = new Vec3D(d0, vec3d.b + d7 * d3, vec3d.c + d8 * d3);
          } else if (d4 < d5) {
            enumdirection = j > i1 ? EnumDirection.DOWN : EnumDirection.UP;
            vec3d = new Vec3D(vec3d.a + d6 * d4, d1, vec3d.c + d8 * d4);
          } else {
            enumdirection = k > j1 ? EnumDirection.NORTH : EnumDirection.SOUTH;
            vec3d = new Vec3D(vec3d.a + d6 * d5, vec3d.b + d7 * d5, d2);
          }

          /*Raytracer.isIgnored(player, new com.comphenix.protocol.wrappers.BlockPosition(blockposition.getX(), blockposition.getY(), blockposition.getZ())) ? null :*/

          l = MathHelper.floor(vec3d.a) - (enumdirection == EnumDirection.EAST ? 1 : 0);
          i1 = MathHelper.floor(vec3d.b) - (enumdirection == EnumDirection.UP ? 1 : 0);
          j1 = MathHelper.floor(vec3d.c) - (enumdirection == EnumDirection.SOUTH ? 1 : 0);
          blockposition = new BlockPosition(l, i1, j1);
          IBlockData iblockdata1 = world.getType(blockposition);
          Block block1 = iblockdata1.getBlock();
          if (!flag1 || block1.a(world, blockposition, iblockdata1) != null) { // liquid check
            if (block1.a(iblockdata1, flag)) { // idk check
              MovingObjectPosition movingobjectposition2 = block.a(world, blockposition, vec3d, vec3d1);

              if (movingobjectposition2 != null) {
                Bukkit.broadcastMessage(String.valueOf(block1));
                return movingobjectposition2;
              }
            } else {
              movingobjectposition1 = new MovingObjectPosition(MovingObjectPosition.EnumMovingObjectType.MISS, vec3d, enumdirection, blockposition);
            }
          }
        }

        return flag2 ? movingobjectposition1 : null;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }
}
