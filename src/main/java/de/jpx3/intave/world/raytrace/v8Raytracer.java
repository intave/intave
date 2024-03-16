package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.UserRepository;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class v8Raytracer implements Raytracer {
  @Override
  @PatchyAutoTranslation
  public MovingObjectPosition raytrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    Vec3D nativeEyeVector = (Vec3D) eyeVector.convertToNativeVec3();
    Vec3D nativeTargetVector = (Vec3D) targetVector.convertToNativeVec3();
    net.minecraft.server.v1_8_R3.MovingObjectPosition movingObjectPosition = performRaytrace(player, handle, nativeEyeVector, nativeTargetVector);
    return MovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private net.minecraft.server.v1_8_R3.MovingObjectPosition performRaytrace(
    Player player, WorldServer world,
    Vec3D nativeLookVector, Vec3D nativePosition
  ) {
//    System.out.println("Raytrace " + nativeLookVector + " " + nativePosition);

    NativeVector lookVector = NativeVector.fromNative(nativeLookVector);
    NativeVector targetVector = NativeVector.fromNative(nativePosition);

    if (includesInvalidCoordinate(lookVector) || includesInvalidCoordinate(targetVector)) {
      return null;
    }

    net.minecraft.server.v1_8_R3.MovingObjectPosition movingobjectposition;
    int targetX = MathHelper.floor(targetVector.xCoord);
    int targetY = MathHelper.floor(targetVector.yCoord);
    int targetZ = MathHelper.floor(targetVector.zCoord);
    int lookX = MathHelper.floor(lookVector.xCoord);
    int lookY = MathHelper.floor(lookVector.yCoord);
    int lookZ = MathHelper.floor(lookVector.zCoord);

    BlockPosition blockposition = new BlockPosition(lookX, lookY, lookZ);
    IBlockData iblockdata = typeOf(player, world, blockposition);//world.getType(blockposition);
    Block block = iblockdata.getBlock();

    if (block.a(iblockdata, false)) {
      movingobjectposition = (net.minecraft.server.v1_8_R3.MovingObjectPosition)
        movingObjectPosition(world, block, blockposition, (Vec3D) lookVector.convertToNativeVec3(), (Vec3D) targetVector.convertToNativeVec3());
      if (movingobjectposition != null) {
        return movingobjectposition;
      }
    }

    int jumps = 50;
    while (jumps-- >= 0) {
      EnumDirection enumdirection;
      if (includesInvalidCoordinate(lookVector)) {
        return null;
      }
      if (lookX == targetX && lookY == targetY && lookZ == targetZ) {
        return null;
      }
      boolean arrivedAtX = true;
      boolean arrivedAtY = true;
      boolean arrivedAtZ = true;
      double lookXStep = 999.0;
      double lookYStep = 999.0;
      double lookZStep = 999.0;
      if (targetX > lookX) {
        lookXStep = (double) lookX + 1.0;
      } else if (targetX < lookX) {
        lookXStep = (double) lookX + 0.0;
      } else {
        arrivedAtX = false;
      }
      if (targetY > lookY) {
        lookYStep = (double) lookY + 1.0;
      } else if (targetY < lookY) {
        lookYStep = (double) lookY + 0.0;
      } else {
        arrivedAtY = false;
      }
      if (targetZ > lookZ) {
        lookZStep = (double) lookZ + 1.0;
      } else if (targetZ < lookZ) {
        lookZStep = (double) lookZ + 0.0;
      } else {
        arrivedAtZ = false;
      }
      double stepScaleX = 999.0;
      double stepScaleY = 999.0;
      double stepScaleZ = 999.0;
      double finalDistanceX = targetVector.xCoord - lookVector.xCoord;
      double finalDistanceY = targetVector.yCoord - lookVector.yCoord;
      double finalDistanceZ = targetVector.zCoord - lookVector.zCoord;
      if (arrivedAtX) {
        stepScaleX = (lookXStep - lookVector.xCoord) / finalDistanceX;
      }
      if (arrivedAtY) {
        stepScaleY = (lookYStep - lookVector.yCoord) / finalDistanceY;
      }
      if (arrivedAtZ) {
        stepScaleZ = (lookZStep - lookVector.zCoord) / finalDistanceZ;
      }
      if (stepScaleX == -0.0) {
        stepScaleX = -0.0001;
      }
      if (stepScaleY == -0.0) {
        stepScaleY = -0.0001;
      }
      if (stepScaleZ == -0.0) {
        stepScaleZ = -0.0001;
      }
      if (stepScaleX < stepScaleY && stepScaleX < stepScaleZ) {
        enumdirection = targetX > lookX ? EnumDirection.WEST : EnumDirection.EAST;
        lookVector = new NativeVector(lookXStep, lookVector.yCoord + finalDistanceY * stepScaleX, lookVector.zCoord + finalDistanceZ * stepScaleX);
      } else if (stepScaleY < stepScaleZ) {
        enumdirection = targetY > lookY ? EnumDirection.DOWN : EnumDirection.UP;
        lookVector = new NativeVector(lookVector.xCoord + finalDistanceX * stepScaleY, lookYStep, lookVector.zCoord + finalDistanceZ * stepScaleY);
      } else {
        enumdirection = targetZ > lookZ ? EnumDirection.NORTH : EnumDirection.SOUTH;
        lookVector = new NativeVector(lookVector.xCoord + finalDistanceX * stepScaleZ, lookVector.yCoord + finalDistanceY * stepScaleZ, lookZStep);
      }
      lookX = MathHelper.floor(lookVector.xCoord) - (enumdirection == EnumDirection.EAST ? 1 : 0);
      lookY = MathHelper.floor(lookVector.yCoord) - (enumdirection == EnumDirection.UP ? 1 : 0);
      lookZ = MathHelper.floor(lookVector.zCoord) - (enumdirection == EnumDirection.SOUTH ? 1 : 0);
      blockposition = new BlockPosition(lookX, lookY, lookZ);
      IBlockData iblockdata1 = typeOf(player, world, blockposition);
      Block block1 = iblockdata1.getBlock();

//      System.out.println("Raytrace " + lookX + " " + lookY + " " + lookZ + " " + block1 + " " + iblockdata1);

      // block1.a refers to isSolid
      boolean solid = block1.a(iblockdata1, false);
      if (solid) {
        net.minecraft.server.v1_8_R3.MovingObjectPosition finalObjectMovingPosition = (net.minecraft.server.v1_8_R3.MovingObjectPosition)
          movingObjectPosition(
            world,
            block1,
            blockposition,
            (Vec3D) lookVector.convertToNativeVec3(),
            (Vec3D) targetVector.convertToNativeVec3()
          );
        if (finalObjectMovingPosition != null) {
          return finalObjectMovingPosition;
        }
      }
    }
    return null;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private Object movingObjectPosition(WorldServer world, Block block, BlockPosition blockPosition, Vec3D lookVector, Vec3D targetVector) {
    try {
      // inner block raytrace
      return block.a(world, blockPosition, lookVector, targetVector);
    } catch (Exception | Error exception) {
      return Blocks.STONE.a(world, blockPosition, lookVector, targetVector);
    }
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private IBlockData typeOf(Player player, WorldServer world, BlockPosition blockPosition) {
    BlockCache blockStates = UserRepository.userOf(player).blockCache();
    int positionX = blockPosition.getX();
    int positionY = blockPosition.getY();
    int positionZ = blockPosition.getZ();
//    boolean inOverride = blockStates.currentlyInOverride(positionX, positionY, positionZ);
//    if (inOverride) {
      Material material = blockStates.typeAt(positionX, positionY, positionZ);
      int variant = blockStates.variantIndexAt(positionX, positionY, positionZ);
      return (IBlockData) BlockVariantRegister.rawVariantOf(material, variant);
//    } else {
//      return world.getType(blockPosition);
//    }
  }

  private boolean includesInvalidCoordinate(NativeVector nativeVector) {
    return Double.isNaN(nativeVector.xCoord) || Double.isNaN(nativeVector.yCoord) || Double.isNaN(nativeVector.zCoord);
  }
}
