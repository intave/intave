package de.jpx3.intave.world.wrapper;

import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;

public class WrappedVector {
  public static final WrappedVector ZERO = new WrappedVector(0.0D, 0.0D, 0.0D);
  public final double xCoord, yCoord, zCoord;

  public WrappedVector(double x, double y, double z) {
    if (x == -0.0D) {
      x = 0.0D;
    }
    if (y == -0.0D) {
      y = 0.0D;
    }
    if (z == -0.0D) {
      z = 0.0D;
    }
    this.xCoord = x;
    this.yCoord = y;
    this.zCoord = z;
  }

  public Vector convertToBukkitVec() {
    return new Vector(xCoord, yCoord, zCoord);
  }

  public Object convertToNativeVec3() {
    try {
      return Lookup.serverClass("Vec3D")
        .getConstructor(Double.TYPE, Double.TYPE, Double.TYPE)
        .newInstance(xCoord, yCoord, zCoord);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public Location toLocation(World world) {
    return new Location(world, xCoord, yCoord, zCoord);
  }

  /**
   * Returns a new vector with the result of the specified vector minus this.
   */
  public WrappedVector subtractReverse(WrappedVector vec) {
    return new WrappedVector(vec.xCoord - this.xCoord, vec.yCoord - this.yCoord, vec.zCoord - this.zCoord);
  }

  /**
   * Normalizes the vector to a length of 1 (except if it is the zero vector)
   */
  public WrappedVector normalize() {
    double d0 = WrappedMathHelper.sqrt_double(this.xCoord * this.xCoord + this.yCoord * this.yCoord + this.zCoord * this.zCoord);
    return d0 < 1.0E-4D ? new WrappedVector(0.0D, 0.0D, 0.0D) : new WrappedVector(this.xCoord / d0, this.yCoord / d0, this.zCoord / d0);
  }

  public double dotProduct(WrappedVector vec) {
    return this.xCoord * vec.xCoord + this.yCoord * vec.yCoord + this.zCoord * vec.zCoord;
  }

  public double length() {
    return Math.sqrt(xCoord * xCoord + yCoord * yCoord + zCoord * zCoord);
  }

  public WrappedVector scale(double factor) {
    return new WrappedVector(xCoord * factor, yCoord * factor, zCoord * factor);
  }

  /**
   * Returns a new vector with the result of this vector x the specified vector.
   */
  public WrappedVector crossProduct(WrappedVector vec) {
    return new WrappedVector(this.yCoord * vec.zCoord - this.zCoord * vec.yCoord, this.zCoord * vec.xCoord - this.xCoord * vec.zCoord, this.xCoord * vec.yCoord - this.yCoord * vec.xCoord);
  }

  public WrappedVector subtract(WrappedVector vec) {
    return this.subtract(vec.xCoord, vec.yCoord, vec.zCoord);
  }

  public WrappedVector subtract(double x, double y, double z) {
    return this.addVector(-x, -y, -z);
  }

  public WrappedVector add(WrappedVector vec) {
    return this.addVector(vec.xCoord, vec.yCoord, vec.zCoord);
  }

  public WrappedVector add(double x, double y, double z) {
    return new WrappedVector(this.xCoord + x, this.yCoord + y, this.zCoord + z);
  }

  /**
   * Adds the specified x,y,z vector components to this vector and returns the resulting vector. Does not change this
   * vector.
   */
  public WrappedVector addVector(double x, double y, double z) {
    return new WrappedVector(this.xCoord + x, this.yCoord + y, this.zCoord + z);
  }

  /**
   * Euclidean distance between this and the specified vector, returned as double.
   */
  public double distanceTo(WrappedVector vec) {
    double d0 = vec.xCoord - this.xCoord;
    double d1 = vec.yCoord - this.yCoord;
    double d2 = vec.zCoord - this.zCoord;
    return WrappedMathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
  }

  public double distanceToBox(WrappedAxisAlignedBB boundingBox) {
    double xDist = MathHelper.minmax(0, boundingBox.minX - xCoord, xCoord - boundingBox.maxX);
    double yDist = MathHelper.minmax(0, boundingBox.minY - yCoord, yCoord - boundingBox.maxY);
    double zDist = MathHelper.minmax(0, boundingBox.minZ - zCoord, zCoord - boundingBox.maxZ);
    return distanceTo(new WrappedVector(xDist, yDist, zDist));
  }

  public double distanceTo(Vector vector) {
    double d0 = vector.getX() - this.xCoord;
    double d1 = vector.getY() - this.yCoord;
    double d2 = vector.getZ() - this.zCoord;
    return WrappedMathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
  }

  /**
   * The square of the Euclidean distance between this and the specified vector.
   */
  public double squareDistanceTo(WrappedVector vec) {
    double d0 = vec.xCoord - this.xCoord;
    double d1 = vec.yCoord - this.yCoord;
    double d2 = vec.zCoord - this.zCoord;
    return d0 * d0 + d1 * d1 + d2 * d2;
  }

  /**
   * Returns the length of the vector.
   */
  public double lengthVector() {
    return WrappedMathHelper.sqrt_double(this.xCoord * this.xCoord + this.yCoord * this.yCoord + this.zCoord * this.zCoord);
  }

  /**
   * Returns a new vector with x value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public WrappedVector getIntermediateWithXValue(WrappedVector vec, double x) {
    double d0 = vec.xCoord - this.xCoord;
    double d1 = vec.yCoord - this.yCoord;
    double d2 = vec.zCoord - this.zCoord;

    if (d0 * d0 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (x - this.xCoord) / d0;
      return d3 >= 0.0D && d3 <= 1.0D ? new WrappedVector(this.xCoord + d0 * d3, this.yCoord + d1 * d3, this.zCoord + d2 * d3) : null;
    }
  }

  /**
   * Returns a new vector with y value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public WrappedVector getIntermediateWithYValue(WrappedVector vec, double y) {
    double d0 = vec.xCoord - this.xCoord;
    double d1 = vec.yCoord - this.yCoord;
    double d2 = vec.zCoord - this.zCoord;

    if (d1 * d1 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (y - this.yCoord) / d1;
      return d3 >= 0.0D && d3 <= 1.0D ? new WrappedVector(this.xCoord + d0 * d3, this.yCoord + d1 * d3, this.zCoord + d2 * d3) : null;
    }
  }

  /**
   * Returns a new vector with z value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public WrappedVector getIntermediateWithZValue(WrappedVector vec, double z) {
    double d0 = vec.xCoord - this.xCoord;
    double d1 = vec.yCoord - this.yCoord;
    double d2 = vec.zCoord - this.zCoord;

    if (d2 * d2 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (z - this.zCoord) / d2;
      return d3 >= 0.0D && d3 <= 1.0D ? new WrappedVector(this.xCoord + d0 * d3, this.yCoord + d1 * d3, this.zCoord + d2 * d3) : null;
    }
  }

  public static WrappedVector fromNative(Object vec3d) {
    return WrapperLinkage.vectorOf(vec3d);
  }

  public String toString() {
    return "(" + this.xCoord + ", " + this.yCoord + ", " + this.zCoord + ")";
  }
}
