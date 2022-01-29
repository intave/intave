package de.jpx3.intave.shade;

import java.io.Serializable;

public final class Position implements Serializable {
  public double xCoordinate, yCoordinate, zCoordinate;

  public Position() {
  }

  public Position(double xCoordinate, double yCoordinate, double zCoordinate) {
    this.xCoordinate = xCoordinate;
    this.yCoordinate = yCoordinate;
    this.zCoordinate = zCoordinate;
  }

  public double xCoordinate() {
    return xCoordinate;
  }

  public double yCoordinate() {
    return yCoordinate;
  }

  public double zCoordinate() {
    return zCoordinate;
  }

  public int blockX() {
    return floor(xCoordinate);
  }

  public int blockY() {
    return floor(yCoordinate);
  }

  public int blockZ() {
    return floor(zCoordinate);
  }

  private int floor(double num) {
    int floor = (int)num;
    return (double)floor == num ? floor : floor - (int)(Double.doubleToRawLongBits(num) >>> 63);
  }
  public double distanceTo(Position position) {
    double d0 = position.xCoordinate - this.xCoordinate;
    double d1 = position.yCoordinate - this.yCoordinate;
    double d2 = position.zCoordinate - this.zCoordinate;
    return (float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Position position = (Position) o;

    if (Double.compare(position.xCoordinate, xCoordinate) != 0) return false;
    if (Double.compare(position.yCoordinate, yCoordinate) != 0) return false;
    return Double.compare(position.zCoordinate, zCoordinate) == 0;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    temp = Double.doubleToLongBits(xCoordinate);
    result = (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(yCoordinate);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(zCoordinate);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  public Position with(Motion motion) {
    return new Position(xCoordinate + motion.motionX, yCoordinate + motion.motionY, zCoordinate + motion.motionZ);
  }

  @Override
  public String toString() {
    return "Position{" +
      "x=" + xCoordinate +
      ", y=" + yCoordinate +
      ", z=" + zCoordinate +
      '}';
  }

  public static Position empty() {
    return new Position();
  }
}
