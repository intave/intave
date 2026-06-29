package de.jpx3.intave.share;

import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.link.WrapperConverter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;

public class RawVector3d {
  public static final RawVector3d ZERO = new RawVector3d(0.0D, 0.0D, 0.0D);
  public final double x, y, z;

  public RawVector3d(double x, double y, double z) {
    if (x == -0.0D) {
      x = 0.0D;
    }
    if (y == -0.0D) {
      y = 0.0D;
    }
    if (z == -0.0D) {
      z = 0.0D;
    }
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Position toPosition() {
    return new Position(x, y, z);
  }

  public Vector convertToBukkitVec() {
    return new Vector(x, y, z);
  }

  public Object convertToNativeVec3() {
    try {
      return Lookup.serverClass("Vec3D")
        .getConstructor(Double.TYPE, Double.TYPE, Double.TYPE)
        .newInstance(x, y, z);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public Motion toMotion() {
    return new Motion(x, y, z);
  }

  public Location toLocation(World world) {
    return new Location(world, x, y, z);
  }

  /**
   * Returns a new vector with the result of the specified vector minus this.
   */
  public RawVector3d subtractReverse(RawVector3d vec) {
    return new RawVector3d(vec.x - this.x, vec.y - this.y, vec.z - this.z);
  }

  /**
   * Normalizes the vector to a length of 1 (except if it is the zero vector)
   */
  public RawVector3d normalize() {
    double d0 = ClientMath.sqrt_double(this.x * this.x + this.y * this.y + this.z * this.z);
    return d0 < 1.0E-4D ? new RawVector3d(0.0D, 0.0D, 0.0D) : new RawVector3d(this.x / d0, this.y / d0, this.z / d0);
  }

  public double dotProduct(RawVector3d vec) {
    return this.x * vec.x + this.y * vec.y + this.z * vec.z;
  }

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public RawVector3d scale(double factor) {
    return new RawVector3d(x * factor, y * factor, z * factor);
  }

  /**
   * Returns a new vector with the result of this vector x the specified vector.
   */
  public RawVector3d crossProduct(RawVector3d vec) {
    return new RawVector3d(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
  }

  public RawVector3d subtract(RawVector3d vec) {
    return this.subtract(vec.x, vec.y, vec.z);
  }

  public RawVector3d subtract(double x, double y, double z) {
    return this.addVector(-x, -y, -z);
  }

  public RawVector3d add(RawVector3d vec) {
    return this.addVector(vec.x, vec.y, vec.z);
  }

  public RawVector3d add(double x, double y, double z) {
    return new RawVector3d(this.x + x, this.y + y, this.z + z);
  }

  /**
   * Adds the specified x,y,z vector components to this vector and returns the resulting vector. Does not change this
   * vector.
   */
  public RawVector3d addVector(double x, double y, double z) {
    return new RawVector3d(this.x + x, this.y + y, this.z + z);
  }

  /**
   * Euclidean distance between this and the specified vector, returned as double.
   */
  public double distanceTo(RawVector3d vec) {
    double d0 = vec.x - this.x;
    double d1 = vec.y - this.y;
    double d2 = vec.z - this.z;
    return ClientMath.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
  }

  public double distanceToBox(BoundingBox boundingBox) {
    double xDist = MathHelper.minmax(0, boundingBox.minX - x, x - boundingBox.maxX);
    double yDist = MathHelper.minmax(0, boundingBox.minY - y, y - boundingBox.maxY);
    double zDist = MathHelper.minmax(0, boundingBox.minZ - z, z - boundingBox.maxZ);
    return distanceTo(new RawVector3d(xDist, yDist, zDist));
  }

  public double distanceTo(Vector vector) {
    double d0 = vector.getX() - this.x;
    double d1 = vector.getY() - this.y;
    double d2 = vector.getZ() - this.z;
    return ClientMath.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
  }

  /**
   * The square of the Euclidean distance between this and the specified vector.
   */
  public double squareDistanceTo(RawVector3d vec) {
    double d0 = vec.x - this.x;
    double d1 = vec.y - this.y;
    double d2 = vec.z - this.z;
    return d0 * d0 + d1 * d1 + d2 * d2;
  }

  /**
   * Returns the length of the vector.
   */
  public double lengthVector() {
    return ClientMath.sqrt_double(this.x * this.x + this.y * this.y + this.z * this.z);
  }

  /**
   * Returns a new vector with x value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public RawVector3d getIntermediateWithXValue(RawVector3d vec, double x) {
    double d0 = vec.x - this.x;
    double d1 = vec.y - this.y;
    double d2 = vec.z - this.z;

    if (d0 * d0 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (x - this.x) / d0;
      return d3 >= 0.0D && d3 <= 1.0D ? new RawVector3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }
  }

  /**
   * Returns a new vector with y value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public RawVector3d getIntermediateWithYValue(RawVector3d vec, double y) {
    double d0 = vec.x - this.x;
    double d1 = vec.y - this.y;
    double d2 = vec.z - this.z;

    if (d1 * d1 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (y - this.y) / d1;
      return d3 >= 0.0D && d3 <= 1.0D ? new RawVector3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }
  }

  /**
   * Returns a new vector with z value equal to the second parameter, along the line between this vector and the passed
   * in vector, or null if not possible.
   */
  public RawVector3d getIntermediateWithZValue(RawVector3d vec, double z) {
    double d0 = vec.x - this.x;
    double d1 = vec.y - this.y;
    double d2 = vec.z - this.z;

    if (d2 * d2 < 1.0000000116860974E-7D) {
      return null;
    } else {
      double d3 = (z - this.z) / d2;
      return d3 >= 0.0D && d3 <= 1.0D ? new RawVector3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }
  }

  public static RawVector3d fromNative(Object vec3d) {
    return WrapperConverter.vectorFromVec3D(vec3d);
  }

  public String toString() {
    return "(" + this.x + ", " + this.y + ", " + this.z + ")";
  }
}
