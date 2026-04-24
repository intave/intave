package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Relative;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.io.Serializable;
import java.util.Set;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.share.ClientMath.floor;

public final class Position extends Vector implements Serializable {
  public static final StreamCodec<ByteBuf, ByteBuf, Position> STREAM_CODEC = StreamCodec.of(
    (byteBuf, position) -> {
      byteBuf.writeDouble(position.x);
      byteBuf.writeDouble(position.y);
      byteBuf.writeDouble(position.z);
    },
    byteBuf -> new Position(byteBuf.readDouble(), byteBuf.readDouble(), byteBuf.readDouble())
  );

  public Position() {
  }

  public Position(double xCoordinate, double yCoordinate, double zCoordinate) {
    super(xCoordinate, yCoordinate, zCoordinate);
  }

  public Position filtered(Set<Relative> flags) {
    return new Position(
      flags.contains(Relative.X) ? x : 0,
      flags.contains(Relative.Y) ? y : 0,
      flags.contains(Relative.Z) ? z : 0
    );
  }

  public static Position of(int blockX, int blockY, int blockZ) {
    return new Position(blockX, blockY, blockZ);
  }

  public static Position of(double x, double y, double z) {
    return new Position(x, y, z);
  }

  public static Position of(Location location) {
    return new Position(location.getX(), location.getY(), location.getZ());
  }

  public static Position of(Vector vector) {
    return new Position(vector.getX(), vector.getY(), vector.getZ());
  }

  public boolean hasNaNCoordinate() {
    return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);
  }

  public BlockPosition toBlockPosition() {
    return new BlockPosition(floor(x), floor(y), floor(z));
  }

  public double distance(Position position) {
    return distance(position.x, position.y, position.z);
  }

  public double distance(Location location) {
    return distance(location.getX(), location.getY(), location.getZ());
  }

  public double distance(double x, double y, double z) {
    double deltaX = this.x - x;
    double deltaY = this.y - y;
    double deltaZ = this.z - z;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
  }

  public Position add(double x, double y, double z) {
    return new Position(this.x + x, this.y + y, this.z + z);
  }

  public Position add(Vector vector) {
    return add(vector.getX(), vector.getY(), vector.getZ());
  }

  @Override
  public String toString() {
    return formatDouble(x, 2) + ", " + formatDouble(y,2) + ", " + formatDouble(z,2);
  }

  public String format(int decimalPlaces) {
    return formatDouble(x, decimalPlaces) + ", " + formatDouble(y, decimalPlaces) + ", " + formatDouble(z, decimalPlaces);
  }

  @Override
  public Position clone() {
    return (Position) super.clone();
  }

  public static Position empty() {
    return new Position();
  }

  public NativeVector toNativeVec() {
    return new NativeVector(x, y, z);
  }

  public Rotation rotationTo(Position otherPoint) {
    float yaw = (float) Math.toDegrees(Math.atan2(otherPoint.z - z, otherPoint.x - x) - 90f);
    float pitch = -(float) Math.toDegrees(Math.atan2(otherPoint.y - y, Math.sqrt(Math.pow(otherPoint.x - x, 2) + Math.pow(otherPoint.z - z, 2))));
    return new Rotation(yaw, pitch);
  }

  public int chunkX() {
    return floor(x) >> 4;
  }

  public int chunkZ() {
    return floor(z) >> 4;
  }
}
