package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class LegacyVersionRaytracer implements VersionRaytracer {
  @Override
  @PatchyAutoTranslation
  public WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    Vec3D nativeEyeVector = (Vec3D) eyeVector.convertToNativeVec3();
    Vec3D nativeTargetVector = (Vec3D) targetVector.convertToNativeVec3();
    MovingObjectPosition movingObjectPosition = performRaytrace(player, handle, nativeEyeVector, nativeTargetVector);
    return WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private MovingObjectPosition performRaytrace(
    Player player, WorldServer world,
    Vec3D nativeLookVector, Vec3D nativePosition
  ) {
    WrappedVector lookVector = WrappedVector.fromNative(nativeLookVector);
    WrappedVector position = WrappedVector.fromNative(nativePosition);
    if (includesInvalidCoordinate(lookVector) || includesInvalidCoordinate(position)) {
      return null;
    }

    MovingObjectPosition movingobjectposition;
    int positionX = MathHelper.floor(position.xCoord);
    int positionY = MathHelper.floor(position.yCoord);
    int positionZ = MathHelper.floor(position.zCoord);
    int lookX = MathHelper.floor(lookVector.xCoord);
    int lookY = MathHelper.floor(lookVector.yCoord);
    int lookZ = MathHelper.floor(lookVector.zCoord);

    BlockPosition blockposition = new BlockPosition(lookX, lookY, lookZ);
    IBlockData iblockdata = typeOf(player, world, blockposition);//world.getType(blockposition);
    Block block = iblockdata.getBlock();
    if (block.a(iblockdata, false) &&
      (movingobjectposition = block.a(world, blockposition, (Vec3D) lookVector.convertToNativeVec3(), (Vec3D) position.convertToNativeVec3())) != null
    ) {
      return movingobjectposition;
    }

    int k1 = 50;
    while (k1-- >= 0) {
      EnumDirection enumdirection;
      if (includesInvalidCoordinate(lookVector)) {
        return null;
      }
      if (lookX == positionX && lookY == positionY && lookZ == positionZ) {
        return null;
      }
      boolean flag3 = true;
      boolean flag4 = true;
      boolean flag5 = true;
      double d0 = 999.0;
      double d1 = 999.0;
      double d2 = 999.0;
      if (positionX > lookX) {
        d0 = (double) lookX + 1.0;
      } else if (positionX < lookX) {
        d0 = (double) lookX + 0.0;
      } else {
        flag3 = false;
      }
      if (positionY > lookY) {
        d1 = (double) lookY + 1.0;
      } else if (positionY < lookY) {
        d1 = (double) lookY + 0.0;
      } else {
        flag4 = false;
      }
      if (positionZ > lookZ) {
        d2 = (double) lookZ + 1.0;
      } else if (positionZ < lookZ) {
        d2 = (double) lookZ + 0.0;
      } else {
        flag5 = false;
      }
      double d3 = 999.0;
      double d4 = 999.0;
      double d5 = 999.0;
      double d6 = position.xCoord - lookVector.xCoord;
      double d7 = position.yCoord - lookVector.yCoord;
      double d8 = position.zCoord - lookVector.zCoord;
      if (flag3) {
        d3 = (d0 - lookVector.xCoord) / d6;
      }
      if (flag4) {
        d4 = (d1 - lookVector.yCoord) / d7;
      }
      if (flag5) {
        d5 = (d2 - lookVector.zCoord) / d8;
      }
      if (d3 == -0.0) {
        d3 = -0.0001;
      }
      if (d4 == -0.0) {
        d4 = -0.0001;
      }
      if (d5 == -0.0) {
        d5 = -0.0001;
      }
      if (d3 < d4 && d3 < d5) {
        enumdirection = positionX > lookX ? EnumDirection.WEST : EnumDirection.EAST;
        lookVector = new WrappedVector(d0, lookVector.yCoord + d7 * d3, lookVector.zCoord + d8 * d3);
      } else if (d4 < d5) {
        enumdirection = positionY > lookY ? EnumDirection.DOWN : EnumDirection.UP;
        lookVector = new WrappedVector(lookVector.xCoord + d6 * d4, d1, lookVector.zCoord + d8 * d4);
      } else {
        enumdirection = positionZ > lookZ ? EnumDirection.NORTH : EnumDirection.SOUTH;
        lookVector = new WrappedVector(lookVector.xCoord + d6 * d5, lookVector.yCoord + d7 * d5, d2);
      }
      lookX = MathHelper.floor(lookVector.xCoord) - (enumdirection == EnumDirection.EAST ? 1 : 0);
      lookY = MathHelper.floor(lookVector.yCoord) - (enumdirection == EnumDirection.UP ? 1 : 0);
      lookZ = MathHelper.floor(lookVector.zCoord) - (enumdirection == EnumDirection.SOUTH ? 1 : 0);
      blockposition = new BlockPosition(lookX, lookY, lookZ);
      IBlockData iblockdata1 = typeOf(player, world, blockposition);//world.getType(blockposition);
      Block block1 = iblockdata1.getBlock();

      // block1.a refers to getCollisionBoundingBox
      if (block1.a(iblockdata1, false)) {
        MovingObjectPosition movingobjectposition2 = block1.a(world, blockposition, (Vec3D) lookVector.convertToNativeVec3(), (Vec3D) position.convertToNativeVec3());
        if (movingobjectposition2 == null) {
          continue;
        }
        return movingobjectposition2;
      }
    }
    return null;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private IBlockData typeOf(Player player, WorldServer world, BlockPosition blockPosition) {
    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    BoundingBoxAccess.CacheEntry cacheEntry = boundingBoxAccess.overrideOf(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    if(cacheEntry != null) {
      return Block.getById(cacheEntry.type().getId()).fromLegacyData(cacheEntry.data());
    } else {
      return world.getType(blockPosition);
    }
  }

  private boolean includesInvalidCoordinate(WrappedVector wrappedVector) {
    return Double.isNaN(wrappedVector.xCoord) || Double.isNaN(wrappedVector.yCoord) || Double.isNaN(wrappedVector.zCoord);
  }
}
