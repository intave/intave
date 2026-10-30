/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;

import static de.jpx3.intave.codec.JsonStreamCodecs.doubleField;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;

public class RawVector3d {
  /** The packed, scaled vector introduced in 1.21.9 (network/LpVec3). */
  public static final StreamCodec<ByteBuf, ByteBuf, RawVector3d> LOW_PRECISION_STREAM_CODEC = StreamCodec.of(
    (output, value) -> {
      double x = sanitizeVector(value.x),
          y = sanitizeVector(value.y),
          z = sanitizeVector(value.z);
      double maximum = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
      if (maximum < 3.051944088384301E-5) {
        output.writeByte(0);
        return;
      }
      long scale = (long) Math.ceil(maximum);
      boolean extended = (scale & 3) != scale;
      long packed =
          (extended ? scale & 3 | 4 : scale)
              | packVector(x / scale) << 3
              | packVector(y / scale) << 18
              | packVector(z / scale) << 33;
      output.writeByte((int) packed);
      output.writeByte((int) (packed >> 8));
      output.writeInt((int) (packed >> 16));
      if (extended) ByteBufStreamCodecs.VARINT.encode(output, (int) (scale >> 2));
    },
    input -> {
      int first = input.readUnsignedByte();
      if (first == 0) return new RawVector3d(0, 0, 0);
      int second = input.readUnsignedByte();
      long packed = input.readUnsignedInt() << 16 | (long) second << 8 | first;
      long scale = first & 3;
      if ((first & 4) != 0) scale |= (ByteBufStreamCodecs.VARINT.decode(input) & 0xffffffffL) << 2;
      return new RawVector3d(
          unpackVector(packed >> 3) * scale,
          unpackVector(packed >> 18) * scale,
          unpackVector(packed >> 33) * scale);
    }
  );

  private static final StreamCodec<ByteBuf, ByteBuf, Double> BYTE_DELTA =
    ByteBufStreamCodecs.BYTE.beforeAndAfter(
      value -> value / 32.0,
      value -> (byte) quantizedDelta(value, 32.0, Byte.MIN_VALUE, Byte.MAX_VALUE)
    );
  private static final StreamCodec<ByteBuf, ByteBuf, Double> SHORT_DELTA =
    ByteBufStreamCodecs.SHORT.beforeAndAfter(
      value -> value / 4096.0,
      value -> (short) quantizedDelta(value, 4096.0, Short.MIN_VALUE, Short.MAX_VALUE)
    );
  private static final StreamCodec<ByteBuf, ByteBuf, Double> BYTE_HIT_COORDINATE =
    ByteBufStreamCodecs.UNSIGNED_BYTE.beforeAndAfter(value -> value / 16.0, value -> (int) (value * 16));
  public static final StreamCodec<ByteBuf, ByteBuf, RawVector3d> BYTE_DELTA_STREAM_CODEC = StreamCodec.compound(
    BYTE_DELTA, RawVector3d::x,
    BYTE_DELTA, RawVector3d::y,
    BYTE_DELTA, RawVector3d::z,
    RawVector3d::new
  );
  public static final StreamCodec<ByteBuf, ByteBuf, RawVector3d> SHORT_DELTA_STREAM_CODEC = StreamCodec.compound(
    SHORT_DELTA, RawVector3d::x,
    SHORT_DELTA, RawVector3d::y,
    SHORT_DELTA, RawVector3d::z,
    RawVector3d::new
  );
  public static final StreamCodec<ByteBuf, ByteBuf, RawVector3d> FLOAT_STREAM_CODEC = StreamCodec.compound(
    ByteBufStreamCodecs.FLOAT_AS_DOUBLE, RawVector3d::x,
    ByteBufStreamCodecs.FLOAT_AS_DOUBLE, RawVector3d::y,
    ByteBufStreamCodecs.FLOAT_AS_DOUBLE, RawVector3d::z,
    RawVector3d::new
  );
  public static final StreamCodec<ByteBuf, ByteBuf, RawVector3d> BYTE_HIT_POSITION_STREAM_CODEC = StreamCodec.compound(
    BYTE_HIT_COORDINATE, RawVector3d::x,
    BYTE_HIT_COORDINATE, RawVector3d::y,
    BYTE_HIT_COORDINATE, RawVector3d::z,
    RawVector3d::new
  );

  private static double sanitizeVector(double value) {
    return Double.isNaN(value)
        ? 0
        : Math.max(-1.7179869183E10, Math.min(1.7179869183E10, value));
  }

  private static long packVector(double value) {
    return Math.round((value * 0.5 + 0.5) * 32766);
  }

  private static double unpackVector(long value) {
    return Math.min(value & 32767, 32766) * 2.0 / 32766.0 - 1.0;
  }

  private static int quantizedDelta(double value, double divisor, int minimum, int maximum) {
    double scaled = Math.rint(value * divisor);
    if (!Double.isFinite(scaled) || scaled < minimum || scaled > maximum) {
      throw new IllegalArgumentException("Movement delta is out of range");
    }
    return (int) scaled;
  }

  public static final StreamCodec<JsonReader, JsonWriter, RawVector3d> JSON_CODEC = object(
    doubleField("x", RawVector3d::x),
    doubleField("y", RawVector3d::y),
    doubleField("z", RawVector3d::z),
    RawVector3d::new
  );
  public static final RawVector3d ZERO = new RawVector3d(0.0D, 0.0D, 0.0D);
	public static final RawVector3d X_AXIS = new RawVector3d(1.0D, 0.0D, 0.0D);
  public static final RawVector3d Y_AXIS = new RawVector3d(0.0D, 1.0D, 0.0D);
  public static final RawVector3d Z_AXIS = new RawVector3d(0.0D, 0.0D, 1.0D);
	final double x, y, z;

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

  public double dot(RawVector3d other) {
    return this.x * other.x + this.y * other.y + this.z * other.z;
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

  public BlockPosition toBlockPosition() {
    if (this instanceof BlockPosition) {
      return (BlockPosition) this;
    }
    return new BlockPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public double z() {
    return z;
  }

  public double lengthSqr() {
    return this.x * this.x + this.y * this.y + this.z * this.z;
  }

  public static RawVector3d fromBukkit(Vector subtract) {
    return new RawVector3d(subtract.getX(), subtract.getY(), subtract.getZ());
  }

  public static RawVector3d zero() {
    return new RawVector3d(0.0D, 0.0D, 0.0D);
  }

  public String toString() {
    return "(" + this.x + ", " + this.y + ", " + this.z + ")";
  }
}
