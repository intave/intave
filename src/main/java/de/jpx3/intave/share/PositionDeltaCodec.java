package de.jpx3.intave.share;

import de.jpx3.intave.codec.transform.TriFunction;
import org.bukkit.util.Vector;

public final class PositionDeltaCodec {
  private Position base = new Position(0.0D, 0.0D, 0.0D);
  private double resolution = 4096.0D;

  public PositionDeltaCodec() {
  }

  public PositionDeltaCodec(double resolution) {
    this.resolution = resolution;
  }

  private long encode(double value) {
    return Math.round(value * this.resolution);
  }

  private double decode(long value) {
    return value / this.resolution;
  }

  public <T> T decode(
    long x, long y, long z,
    TriFunction<Double, Double, Double, T> function
  ) {
    if (x == 0L && y == 0L && z == 0L) {
      return function.apply(this.base.getX(), this.base.getY(), this.base.getZ());
    } else {
      double dx = x == 0L ? this.base.getX() : decode(encode(this.base.getX()) + x);
      double dy = y == 0L ? this.base.getY() : decode(encode(this.base.getY()) + y);
      double dz = z == 0L ? this.base.getZ() : decode(encode(this.base.getZ()) + z);
      return function.apply(dx, dy, dz);
    }
  }

  public long encodeX(Position position) {
    return encode(position.getX()) - encode(this.base.getX());
  }

  public long encodeY(Position position) {
    return encode(position.getY()) - encode(this.base.getY());
  }

  public long encodeZ(Position position) {
    return encode(position.getZ()) - encode(this.base.getZ());
  }

  public Vector delta(Position position) {
    return position.subtract(this.base);
  }

  public void setBase(Position position) {
	  try {
		  this.base = position.clone();
	  } catch (CloneNotSupportedException e) {
		  throw new RuntimeException(e);
	  }
  }
}
