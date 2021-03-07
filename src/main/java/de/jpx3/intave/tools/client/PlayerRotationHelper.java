package de.jpx3.intave.tools.client;

import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import org.bukkit.util.Vector;

public final class PlayerRotationHelper {
  public static Vector vectorForRotation(float pitch, float yaw) {
    float f = pitch * ((float)Math.PI / 180F);
    float f1 = -yaw * ((float)Math.PI / 180F);
    float f2 = WrappedMathHelper.cos(f1);
    float f3 = WrappedMathHelper.sin(f1);
    float f4 = WrappedMathHelper.cos(f);
    float f5 = WrappedMathHelper.sin(f);
    return new Vector(f3 * f4, -f5, (double)(f2 * f4));
  }

//  public static WrappedVector wrappedVectorForRotation(float pitch, float yaw) {
//    float f = pitch * ((float)Math.PI / 180F);
//    float f1 = -yaw * ((float)Math.PI / 180F);
//    float f2 = WrappedMathHelper.cos(f1);
//    float f3 = WrappedMathHelper.sin(f1);
//    float f4 = WrappedMathHelper.cos(f);
//    float f5 = WrappedMathHelper.sin(f);
//    return new WrappedVector(f3 * f4, -f5, f2 * f4);
//  }

  public static WrappedVector wrappedVectorForRotation(float pitch, float prevYaw, boolean fastMath) {
    float var3 = SinusCache.cos(-prevYaw * 0.017453292f - (float) Math.PI, fastMath);
    float var4 = SinusCache.sin(-prevYaw * 0.017453292F - (float) Math.PI, fastMath);
    float var5 = -SinusCache.cos(-pitch * 0.017453292f, fastMath);
    float var6 = SinusCache.sin(-pitch * 0.017453292f, fastMath);
    return new WrappedVector(var4 * var5, var6, var3 * var5);
  }

  public static float resolveYawRotation(
    WrappedEntity.EntityPositionContext entityPositions,
    double posX, double posZ
  ) {
    final double diffX = entityPositions.posX - posX;
    final double diffZ = entityPositions.posZ - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  public static float resolvePitchRotation(
    WrappedEntity.EntityPositionContext entityPositions,
    double posX, double posY, double posZ
  ) {
    double diffY = entityPositions.posY + 1.62f - (posY + 1.62f);
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    double d3 = Math.sqrt(diffX * diffX + diffZ * diffZ);
    return (float) (-Math.atan2(diffY, d3) * 180.0 / Math.PI);
  }
}