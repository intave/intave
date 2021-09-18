package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.clazz.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.MovingObjectPosition;
import de.jpx3.intave.shade.NativeVector;
import de.jpx3.intave.user.User;
import net.minecraft.server.v1_14_R1.*;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PatchyAutoTranslation
public final class v14Raytracer implements Raytracer {
  @Override
  @PatchyAutoTranslation
  public MovingObjectPosition raytrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    RayTrace raytraceConfiguration = new RayTrace(
      (Vec3D) eyeVector.convertToNativeVec3(),
      (Vec3D) targetVector.convertToNativeVec3(),
      RayTrace.BlockCollisionOption.OUTLINE, // can not be changed
      RayTrace.FluidCollisionOption.NONE, // can not be changed
      ((CraftPlayer) player).getHandle()
    );
    MovingObjectPositionBlock movingObjectPositionBlock =
//      traceProcess(UserRepository.userOf(player), raytraceConfiguration);
      ((CraftWorld) world).getHandle().rayTrace(raytraceConfiguration);
    return MovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPositionBlock);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private MovingObjectPositionBlock traceProcess(User user, RayTrace var0) {
    Vec3D eyeVector = var0.b();
    Vec3D targetVector = var0.a();
    if (eyeVector.equals(targetVector)) {
      return emptyFor(var0);
    } else {
      double var5 = shrink(-1.0E-7D, targetVector.x, eyeVector.x);
      double var7 = shrink(-1.0E-7D, targetVector.y, eyeVector.y);
      double var9 = shrink(-1.0E-7D, targetVector.z, eyeVector.z);
      double var11 = shrink(-1.0E-7D, eyeVector.x, targetVector.x);
      double var13 = shrink(-1.0E-7D, eyeVector.y, targetVector.y);
      double var15 = shrink(-1.0E-7D, eyeVector.z, targetVector.z);
      int var17 = intFloor(var11);
      int var18 = intFloor(var13);
      int var19 = intFloor(var15);
      BlockPosition.MutableBlockPosition var20 = new BlockPosition.MutableBlockPosition(var17, var18, var19);
      MovingObjectPositionBlock var21 = dualRaytrace(user, var0, var20);
      if (var21 != null) {
        return var21;
      } else {
        double var22 = var5 - var11;
        double var24 = var7 - var13;
        double var26 = var9 - var15;
        int var28 = signum(var22);
        int var29 = signum(var24);
        int var30 = signum(var26);
        double var31 = var28 == 0 ? 1.7976931348623157E308D : (double)var28 / var22;
        double var33 = var29 == 0 ? 1.7976931348623157E308D : (double)var29 / var24;
        double var35 = var30 == 0 ? 1.7976931348623157E308D : (double)var30 / var26;
        double var37 = var31 * (var28 > 0 ? 1.0D - subtractLongFloor(var11) : subtractLongFloor(var11));
        double var39 = var33 * (var29 > 0 ? 1.0D - subtractLongFloor(var13) : subtractLongFloor(var13));
        double var41 = var35 * (var30 > 0 ? 1.0D - subtractLongFloor(var15) : subtractLongFloor(var15));

        MovingObjectPositionBlock var43;
        do {
          if (!(var37 <= 1.0D) && !(var39 <= 1.0D) && !(var41 <= 1.0D)) {
            return emptyFor(var0);
          }
          if (var37 < var39) {
            if (var37 < var41) {
              var17 += var28;
              var37 += var31;
            } else {
              var19 += var30;
              var41 += var35;
            }
          } else if (var39 < var41) {
            var18 += var29;
            var39 += var33;
          } else {
            var19 += var30;
            var41 += var35;
          }
          var43 = dualRaytrace(user, var0, var20.d(var17, var18, var19));
        } while(var43 == null);
        return var43;
      }
    }
  }

  private double subtractLongFloor(double var0) {
    return var0 - longFloor(var0);
  }

  private long longFloor(double var0) {
    long var2 = (long)var0;
    return var0 < (double)var2 ? var2 - 1L : var2;
  }

  private int intFloor(double var0) {
    int var2 = (int)var0;
    return var0 < (double)var2 ? var2 - 1 : var2;
  }

  private int signum(double var0) {
    if (var0 == 0.0D) {
      return 0;
    } else {
      return var0 > 0.0D ? 1 : -1;
    }
  }

  private double shrink(double factor, double a, double b) {
    return a + factor * (b - a);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private MovingObjectPositionBlock emptyFor(RayTrace var0x) {
    Vec3D var1 = var0x.b().d(var0x.a());
    return MovingObjectPositionBlock.a(var0x.a(), EnumDirection.a(var1.x, var1.y, var1.z), new BlockPosition(var0x.a()));
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private MovingObjectPositionBlock dualRaytrace(User user, RayTrace var0x, BlockPosition var1) {
    BlockStateAccess blockStateAccess = user.blockShapeAccess();
    WorldServer worldServer = ((CraftWorld) user.player().getWorld()).getHandle();
    IBlockAccess blockAccess = worldServer.getChunkProvider().c(var1.getX() >> 4, var1.getZ() >> 4);
    if (blockAccess == null) {
      return null;
    }
    org.bukkit.Material type = blockStateAccess.resolveType(var1.getX() >> 4, var1.getZ() >> 4, var1.getX(), var1.getY(), var1.getZ());
    int variantIndex = blockStateAccess.resolveVariantIndex(var1.getX() >> 4, var1.getZ() >> 4, var1.getX(), var1.getY(), var1.getZ());
    IBlockData blockVariant = (IBlockData) BlockVariantRegister.rawBlockDataOf(type, variantIndex);
    Vec3D var4 = var0x.b();
    Vec3D var5 = var0x.a();
    VoxelShape var6 = voxelShapeAt(user, var1);//var0x.a(blockVariant, blockAccess, var1);
    //    VoxelShape var8 = VoxelShapes.a();//var0x.a(fluid, blockAccess, var1);
//    MovingObjectPositionBlock var9 = var8.rayTrace(var4, var5, var1);
//    double var10 = var7 == null ? 1.7976931348623157E308D : var0x.b().distanceSquared(var7.getPos());
//    double var12 = var9 == null ? 1.7976931348623157E308D : var0x.b().distanceSquared(var9.getPos());
    return this.blockTrace(var4, var5, blockAccess, var1, var6, blockVariant);//var10 <= var12 ? var7 : var9;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private VoxelShape voxelShapeAt(User user, BlockPosition position) {
    // resolve native boxes
    List<AxisAlignedBB> boxes = translateBoxes(user.blockShapeAccess().resolveShape(
      position.getX(), position.getY(), position.getZ()
    ).boundingBoxes());
    return voxelShapeOf(boxes);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private List<AxisAlignedBB> translateBoxes(List<BoundingBox> wrapped) {
    if (wrapped.isEmpty()) {
      return Collections.emptyList();
    }
    List<AxisAlignedBB> axisAlignedBBS = new ArrayList<>();
    for (BoundingBox wrap : wrapped) {
      axisAlignedBBS.add(new AxisAlignedBB(
        wrap.minX, wrap.minY, wrap.minZ,
        wrap.maxX, wrap.maxY, wrap.maxZ
      ));
    }
    return axisAlignedBBS;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private VoxelShape voxelShapeOf(List<AxisAlignedBB> bbs) {
    if (bbs.isEmpty()) {
      return VoxelShapes.a();
    }
    VoxelShape voxelShape = VoxelShapes.a(bbs.get(0));
    for (int i = 1; i < bbs.size(); i++) {
      voxelShape = VoxelShapes.a(voxelShape, VoxelShapes.a(bbs.get(0)));
    }
    return voxelShape;
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private MovingObjectPositionBlock blockTrace(Vec3D var0, Vec3D var1, IBlockAccess blockAccess, BlockPosition var2, VoxelShape var3, IBlockData var4) {
    MovingObjectPositionBlock var5 = var3.rayTrace(var0, var1, var2);
    if (var5 != null) {
      MovingObjectPositionBlock var6 = var4.k(blockAccess, var2).rayTrace(var0, var1, var2);
      if (var6 != null && var6.getPos().d(var0).g() < var5.getPos().d(var0).g()) {
        return var5.a(var6.getDirection());
      }
    }
    return var5;
  }
}
