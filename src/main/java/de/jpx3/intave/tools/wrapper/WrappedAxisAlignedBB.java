package de.jpx3.intave.tools.wrapper;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class WrappedAxisAlignedBB {
  private static Field fromClassMinXField, fromClassMinYField, fromClassMinZField;
  private static Field fromClassMaxXField, fromClassMaxYField, fromClassMaxZField;
  public final double minX, minY, minZ;
  public final double maxX, maxY, maxZ;

  public WrappedAxisAlignedBB(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    this.minX = Math.min(x1, x2);
    this.minY = Math.min(y1, y2);
    this.minZ = Math.min(z1, z2);
    this.maxX = Math.max(x1, x2);
    this.maxY = Math.max(y1, y2);
    this.maxZ = Math.max(z1, z2);
  }

  public static WrappedAxisAlignedBB fromClass(Object obj) {
    try {
      if (fromClassMinXField == null) {
        cacheFields(obj.getClass());
      }
      double minX = (double) fromClassMinXField.get(obj);
      double minY = (double) fromClassMinYField.get(obj);
      double minZ = (double) fromClassMinZField.get(obj);
      double maxX = (double) fromClassMaxXField.get(obj);
      double maxY = (double) fromClassMaxYField.get(obj);
      double maxZ = (double) fromClassMaxZField.get(obj);
      return new WrappedAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    } catch (Throwable throwable) {
      throw new IntaveInternalException(throwable);
    }
  }

  private static void cacheFields(Class<?> fromClass) {
    fromClassMinXField = fromClass.getFields()[0];
    fromClassMinYField = fromClass.getFields()[1];
    fromClassMinZField = fromClass.getFields()[2];
    fromClassMaxXField = fromClass.getFields()[3];
    fromClassMaxYField = fromClass.getFields()[4];
    fromClassMaxZField = fromClass.getFields()[5];
  }

  private static final Class<?>[] AABB_CONSTRUCTOR = new Class[]{
    Double.TYPE, Double.TYPE, Double.TYPE,
    Double.TYPE, Double.TYPE, Double.TYPE
  };
  private static Constructor<?> axisAlignedBBConstructor;

  public Object unwrap() {
    try {
      if (axisAlignedBBConstructor == null) {
        axisAlignedBBConstructor = ReflectiveAccess.NMS_AABB_CLASS.getConstructor(AABB_CONSTRUCTOR);
      }
      return axisAlignedBBConstructor.newInstance(minX, minY, minZ, maxX, maxY, maxZ);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new ReflectionFailureException(e);
    }
  }

  public double getMin(WrappedEnumDirection.Axis axis) {
    return axis.getCoordinate(this.minX, this.minY, this.minZ);
  }

  public double getMax(WrappedEnumDirection.Axis axis) {
    return axis.getCoordinate(this.maxX, this.maxY, this.maxZ);
  }

  /**
   * Adds the coordinates to the bounding box extending it if the point lies outside the current ranges. Args: x, y, z
   */
  public WrappedAxisAlignedBB addCoord(double x, double y, double z) {
    double d0 = this.minX;
    double d1 = this.minY;
    double d2 = this.minZ;
    double d3 = this.maxX;
    double d4 = this.maxY;
    double d5 = this.maxZ;

    if (x < 0.0D) {
      d0 += x;
    } else if (x > 0.0D) {
      d3 += x;
    }

    if (y < 0.0D) {
      d1 += y;
    } else if (y > 0.0D) {
      d4 += y;
    }

    if (z < 0.0D) {
      d2 += z;
    } else if (z > 0.0D) {
      d5 += z;
    }

    return new WrappedAxisAlignedBB(d0, d1, d2, d3, d4, d5);
  }

  public boolean contains(double x, double y, double z) {
    return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
  }

  /**
   * Returns a bounding box expanded by the specified vector (if negative numbers are given it will shrink). Args: x, y,
   * z
   */
  public WrappedAxisAlignedBB expand(double x, double y, double z) {
    double d0 = this.minX - x;
    double d1 = this.minY - y;
    double d2 = this.minZ - z;
    double d3 = this.maxX + x;
    double d4 = this.maxY + y;
    double d5 = this.maxZ + z;
    return new WrappedAxisAlignedBB(d0, d1, d2, d3, d4, d5);
  }

  public WrappedAxisAlignedBB grow(double value) {
    return expand(value, value, value);
  }

  public WrappedAxisAlignedBB shrink(double value) {
    return grow(-value);
  }

  public WrappedAxisAlignedBB union(WrappedAxisAlignedBB other) {
    double d0 = Math.min(this.minX, other.minX);
    double d1 = Math.min(this.minY, other.minY);
    double d2 = Math.min(this.minZ, other.minZ);
    double d3 = Math.max(this.maxX, other.maxX);
    double d4 = Math.max(this.maxY, other.maxY);
    double d5 = Math.max(this.maxZ, other.maxZ);
    return new WrappedAxisAlignedBB(d0, d1, d2, d3, d4, d5);
  }

  /**
   * returns an AABB with corners x1, y1, z1 and x2, y2, z2
   */
  public static WrappedAxisAlignedBB fromBounds(double x1, double y1, double z1, double x2, double y2, double z2) {
    double d0 = Math.min(x1, x2);
    double d1 = Math.min(y1, y2);
    double d2 = Math.min(z1, z2);
    double d3 = Math.max(x1, x2);
    double d4 = Math.max(y1, y2);
    double d5 = Math.max(z1, z2);
    return new WrappedAxisAlignedBB(d0, d1, d2, d3, d4, d5);
  }

  /**
   * Offsets the current bounding box by the specified coordinates. Args: x, y, z
   */
  public WrappedAxisAlignedBB offset(double x, double y, double z) {
    return new WrappedAxisAlignedBB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
  }

  /**
   * if instance and the argument bounding boxes overlap in the Y and Z dimensions, calculate the offset between them in
   * the X dimension.  return var2 if the bounding boxes do not overlap or if var2 is closer to 0 then the calculated
   * offset.  Otherwise return the calculated offset.
   */
  public double calculateXOffset(WrappedAxisAlignedBB other, double offsetX) {
    if (other.maxY > this.minY && other.minY < this.maxY && other.maxZ > this.minZ && other.minZ < this.maxZ) {
      if (offsetX > 0.0D && other.maxX <= this.minX) {
        double d1 = this.minX - other.maxX;

        if (d1 < offsetX) {
          offsetX = d1;
        }
      } else if (offsetX < 0.0D && other.minX >= this.maxX) {
        double d0 = this.maxX - other.minX;

        if (d0 > offsetX) {
          offsetX = d0;
        }
      }

    }
    return offsetX;
  }

  /**
   * if instance and the argument bounding boxes overlap in the X and Z dimensions, calculate the offset between them in
   * the Y dimension.  return var2 if the bounding boxes do not overlap or if var2 is closer to 0 then the calculated
   * offset.  Otherwise return the calculated offset.
   */
  public double calculateYOffset(WrappedAxisAlignedBB other, double offsetY) {
    if (other.maxX > this.minX && other.minX < this.maxX && other.maxZ > this.minZ && other.minZ < this.maxZ) {
      if (offsetY > 0.0D && other.maxY <= this.minY) {
        double d1 = this.minY - other.maxY;

        if (d1 < offsetY) {
          offsetY = d1;
        }
      } else if (offsetY < 0.0D && other.minY >= this.maxY) {
        double d0 = this.maxY - other.minY;

        if (d0 > offsetY) {
          offsetY = d0;
        }
      }

    }
    return offsetY;
  }

  /**
   * if instance and the argument bounding boxes overlap in the Y and X dimensions, calculate the offset between them in
   * the Z dimension.  return var2 if the bounding boxes do not overlap or if var2 is closer to 0 then the calculated
   * offset.  Otherwise return the calculated offset.
   */
  public double calculateZOffset(WrappedAxisAlignedBB other, double offsetZ) {
    if (other.maxX > this.minX && other.minX < this.maxX && other.maxY > this.minY && other.minY < this.maxY) {
      if (offsetZ > 0.0D && other.maxZ <= this.minZ) {
        double d1 = this.minZ - other.maxZ;

        if (d1 < offsetZ) {
          offsetZ = d1;
        }
      } else if (offsetZ < 0.0D && other.minZ >= this.maxZ) {
        double d0 = this.maxZ - other.minZ;

        if (d0 > offsetZ) {
          offsetZ = d0;
        }
      }

    }
    return offsetZ;
  }

  /**
   * Returns whether the given bounding box intersects with this one. Args: axisAlignedBB
   */
  public boolean intersectsWith(WrappedAxisAlignedBB other) {
    return other.maxX > this.minX && other.minX < this.maxX && (other.maxY > this.minY && other.minY < this.maxY && other.maxZ > this.minZ && other.minZ < this.maxZ);
  }

  /**
   * Returns if the supplied Vec3D is completely inside the bounding box
   */
  public boolean isVecInside(WrappedVector vec) {
    return vec.xCoord > this.minX && vec.xCoord < this.maxX && (vec.yCoord > this.minY && vec.yCoord < this.maxY && vec.zCoord > this.minZ && vec.zCoord < this.maxZ);
  }

  /**
   * Returns the average length of the edges of the bounding box.
   */
  public double getAverageEdgeLength() {
    double d0 = this.maxX - this.minX;
    double d1 = this.maxY - this.minY;
    double d2 = this.maxZ - this.minZ;
    return (d0 + d1 + d2) / 3.0D;
  }

  /**
   * Returns a bounding box that is inset by the specified amounts
   */
  public WrappedAxisAlignedBB contract(double x, double y, double z) {
    double d0 = this.minX + x;
    double d1 = this.minY + y;
    double d2 = this.minZ + z;
    double d3 = this.maxX - x;
    double d4 = this.maxY - y;
    double d5 = this.maxZ - z;
    return new WrappedAxisAlignedBB(d0, d1, d2, d3, d4, d5);
  }

  public WrappedMovingObjectPosition calculateIntercept(WrappedVector vecA, WrappedVector vecB) {
    WrappedVector vec3 = vecA.getIntermediateWithXValue(vecB, this.minX);
    WrappedVector vec31 = vecA.getIntermediateWithXValue(vecB, this.maxX);
    WrappedVector vec32 = vecA.getIntermediateWithYValue(vecB, this.minY);
    WrappedVector vec33 = vecA.getIntermediateWithYValue(vecB, this.maxY);
    WrappedVector vec34 = vecA.getIntermediateWithZValue(vecB, this.minZ);
    WrappedVector vec35 = vecA.getIntermediateWithZValue(vecB, this.maxZ);

    if (!this.isVecInYZ(vec3)) {
      vec3 = null;
    }

    if (!this.isVecInYZ(vec31)) {
      vec31 = null;
    }

    if (!this.isVecInXZ(vec32)) {
      vec32 = null;
    }

    if (!this.isVecInXZ(vec33)) {
      vec33 = null;
    }

    if (!this.isVecInXY(vec34)) {
      vec34 = null;
    }

    if (!this.isVecInXY(vec35)) {
      vec35 = null;
    }

    WrappedVector vec36 = null;

    if (vec3 != null) {
      vec36 = vec3;
    }

    if (vec31 != null && (vec36 == null || vecA.squareDistanceTo(vec31) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec31;
    }

    if (vec32 != null && (vec36 == null || vecA.squareDistanceTo(vec32) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec32;
    }

    if (vec33 != null && (vec36 == null || vecA.squareDistanceTo(vec33) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec33;
    }

    if (vec34 != null && (vec36 == null || vecA.squareDistanceTo(vec34) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec34;
    }

    if (vec35 != null && (vec36 == null || vecA.squareDistanceTo(vec35) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec35;
    }

    if (vec36 == null) {
      return null;
    } else {
      WrappedEnumDirection enumfacing;

      if (vec36 == vec3) {
        enumfacing = WrappedEnumDirection.WEST;
      } else if (vec36 == vec31) {
        enumfacing = WrappedEnumDirection.EAST;
      } else if (vec36 == vec32) {
        enumfacing = WrappedEnumDirection.DOWN;
      } else if (vec36 == vec33) {
        enumfacing = WrappedEnumDirection.UP;
      } else if (vec36 == vec34) {
        enumfacing = WrappedEnumDirection.NORTH;
      } else {
        enumfacing = WrappedEnumDirection.SOUTH;
      }

      return new WrappedMovingObjectPosition(vec36, enumfacing);
    }
  }

  public double nearestDistanceTo(WrappedVector fieldPoint) {
    WrappedVector wrappedVector = nearestPointTo(fieldPoint);
    return wrappedVector.distanceTo(fieldPoint);
  }

  private WrappedVector nearestPointTo(WrappedVector fieldPoint) {
    double pointX, pointY, pointZ;
    double refX = fieldPoint.xCoord,
           refY = fieldPoint.yCoord,
           refZ = fieldPoint.zCoord;

    if (refX > maxX/*(targetX + (hitboxWidth / 2))*/) {
      pointX = maxX/*targetX + (hitboxWidth / 2)*/;
    } else {
      pointX = Math.max(refX, minX/*(targetX - (hitboxWidth / 2))*/);
    }
    double boxOffset = 0.1;
    if (refY > minY/*(targetY + (hitboxHeight - boxOffset))*/) {
      pointY = minY/*targetY + (hitboxHeight - boxOffset)*/;
    } else {
      pointY = Math.max(refY, minY/*(targetY - boxOffset)*/);
    }
    if (refZ > maxZ/*(targetZ + (hitboxWidth / 2))*/) {
      pointZ = maxZ/*targetZ + (hitboxWidth / 2)*/;
    } else {
      pointZ = Math.max(refZ, minZ/*(targetZ - (hitboxWidth / 2))*/);
    }
    return new WrappedVector(pointX, pointY, pointZ);
  }

  public WrappedAxisAlignedBB addJustMaxY(double expansionY) {
    return new WrappedAxisAlignedBB(minX, minY, minZ, maxX, this.maxY + expansionY, maxZ);
  }

  /**
   * Checks if the specified vector is within the YZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInYZ(WrappedVector vec) {
    return vec != null && vec.yCoord >= this.minY && vec.yCoord <= this.maxY && vec.zCoord >= this.minZ && vec.zCoord <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXZ(WrappedVector vec) {
    return vec != null && vec.xCoord >= this.minX && vec.xCoord <= this.maxX && vec.zCoord >= this.minZ && vec.zCoord <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XY dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXY(WrappedVector vec) {
    return vec != null && vec.xCoord >= this.minX && vec.xCoord <= this.maxX && vec.yCoord >= this.minY && vec.yCoord <= this.maxY;
  }

  public String toString() {
    return "box[" + this.minX + ", " + this.minY + ", " + this.minZ + " -> " + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
  }

  public boolean func_181656_b() {
    return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
  }

  public WrappedAxisAlignedBB copy() {
    return new WrappedAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
  }
}