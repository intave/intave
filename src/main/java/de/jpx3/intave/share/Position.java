package de.jpx3.intave.share;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.io.Serializable;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.share.ClientMath.floor;

public final class Position extends Vector implements Serializable {
  public Position() {
  }

  public Position(double xCoordinate, double yCoordinate, double zCoordinate) {
    super(xCoordinate, yCoordinate, zCoordinate);
  }

  public static Position of(int blockX, int blockY, int blockZ) {
    return new Position(blockX, blockY, blockZ);
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

  @Override
  public String toString() {
    return  formatDouble(x, 1) + ", " + formatDouble(y,1) + ", " + formatDouble(z,1);
  }

  @Override
  public Position clone() {
    return (Position) super.clone();
  }

  public static Position empty() {
    return new Position();
  }
}
