package de.jpx3.intave.shade;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.shade.link.WrapperLinkage;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.shade.Direction.Axis.*;

public final class BoundingBox extends MemoryTraced implements BlockShape {
  public final double minX, minY, minZ;
  public final double maxX, maxY, maxZ;

  private boolean originBox;

  public BoundingBox(
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

  public double min(Direction.Axis axis) {
    switch (axis) {
      case X_AXIS:
        return minX;
      case Y_AXIS:
        return minY;
      case Z_AXIS:
        return minZ;
    }
    return axis.select(this.minX, this.minY, this.minZ);
  }

  public double max(Direction.Axis axis) {
    switch (axis) {
      case X_AXIS:
        return maxX;
      case Y_AXIS:
        return maxY;
      case Z_AXIS:
        return maxZ;
    }
    return axis.select(this.maxX, this.maxY, this.maxZ);
  }

  /**
   * Adds the coordinates to the bounding box extending it if the point lies outside the current ranges. Args: x, y, z
   */
  public BoundingBox expand(double x, double y, double z) {
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

    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  public boolean contains(double x, double y, double z) {
    return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
  }

  /**
   * Returns a bounding box expanded by the specified vector (if negative numbers are given it will shrink). Args: x, y,
   * z
   */
  public BoundingBox grow(double x, double y, double z) {
    double d0 = this.minX - x;
    double d1 = this.minY - y;
    double d2 = this.minZ - z;
    double d3 = this.maxX + x;
    double d4 = this.maxY + y;
    double d5 = this.maxZ + z;
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  public BoundingBox grow(double value) {
    return grow(value, value, value);
  }

  public BoundingBox growHorizontally(double value) {
    return grow(value, 0, value);
  }

  public BoundingBox shrink(double value) {
    return grow(-value);
  }

  public BoundingBox union(BoundingBox other) {
    double d0 = Math.min(this.minX, other.minX);
    double d1 = Math.min(this.minY, other.minY);
    double d2 = Math.min(this.minZ, other.minZ);
    double d3 = Math.max(this.maxX, other.maxX);
    double d4 = Math.max(this.maxY, other.maxY);
    double d5 = Math.max(this.maxZ, other.maxZ);
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  /**
   * Offsets the current bounding box by the specified coordinates. Args: x, y, z
   */
  public BoundingBox offset(double x, double y, double z) {
    return new BoundingBox(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox other, double offset) {
    // always collide if axis is selected
    boolean collidesInXAxis = axis == X_AXIS || other.maxX > this.minX && other.minX < this.maxX;
    boolean collidesInYAxis = axis == Y_AXIS || (collidesInXAxis && other.maxY > this.minY && other.minY < this.maxY);
    boolean collidesInZAxis = axis == Z_AXIS || (collidesInYAxis && other.maxZ > this.minZ && other.minZ < this.maxZ);

    if (collidesInXAxis && collidesInYAxis && collidesInZAxis) {
      if (offset > 0.0D && other.max(axis) <= this.min(axis)) {
        double distance = this.min(axis) - other.max(axis);
        if (distance < offset) {
          offset = distance;
        }
      } else if (offset < 0.0D && other.min(axis) >= this.max(axis)) {
        double distance = this.max(axis) - other.min(axis);
        if (distance > offset) {
          offset = distance;
        }
      }
    }
    return offset;
  }

  /**
   * if instance and the argument bounding boxes overlap in the Y and Z dimensions, calculate the offset between them in
   * the X dimension.  return var2 if the bounding boxes do not overlap or if var2 is closer to 0 then the calculated
   * offset.  Otherwise return the calculated offset.
   */
  public double allowedXOffset(BoundingBox other, double offsetX) {
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
  public double allowedYOffset(BoundingBox other, double offsetY) {
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
  public double allowedZOffset(BoundingBox other, double offsetZ) {
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

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    if (isOriginBox()) {
      return offset(posX, posY, posZ);
    }
    return this;
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    BoundingBox normalized = offset(-posX, -posY, -posZ);
    normalized.makeOriginBox();
    return normalized;
  }

  @Override
  public List<BoundingBox> boundingBoxes() {
    return Collections.singletonList(this);
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  /**
   * Returns whether the given bounding box intersects with this one. Args: axisAlignedBB
   */
  public boolean intersectsWith(BoundingBox boundingBox) {
    return boundingBox.maxX > this.minX && boundingBox.minX < this.maxX && (boundingBox.maxY > this.minY && boundingBox.minY < this.maxY && boundingBox.maxZ > this.minZ && boundingBox.minZ < this.maxZ);
  }

  /**
   * Returns if the supplied Vec3D is completely inside the bounding box
   */
  public boolean isVecInside(NativeVector vec) {
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
  public BoundingBox contract(double x, double y, double z) {
    double d0 = this.minX + x;
    double d1 = this.minY + y;
    double d2 = this.minZ + z;
    double d3 = this.maxX - x;
    double d4 = this.maxY - y;
    double d5 = this.maxZ - z;
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  public MovingObjectPosition calculateIntercept(NativeVector vecA, NativeVector vecB) {
    NativeVector vec3 = vecA.getIntermediateWithXValue(vecB, this.minX);
    NativeVector vec31 = vecA.getIntermediateWithXValue(vecB, this.maxX);
    NativeVector vec32 = vecA.getIntermediateWithYValue(vecB, this.minY);
    NativeVector vec33 = vecA.getIntermediateWithYValue(vecB, this.maxY);
    NativeVector vec34 = vecA.getIntermediateWithZValue(vecB, this.minZ);
    NativeVector vec35 = vecA.getIntermediateWithZValue(vecB, this.maxZ);

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

    NativeVector vec36 = null;

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
      Direction enumfacing;

      if (vec36 == vec3) {
        enumfacing = Direction.WEST;
      } else if (vec36 == vec31) {
        enumfacing = Direction.EAST;
      } else if (vec36 == vec32) {
        enumfacing = Direction.DOWN;
      } else if (vec36 == vec33) {
        enumfacing = Direction.UP;
      } else if (vec36 == vec34) {
        enumfacing = Direction.NORTH;
      } else {
        enumfacing = Direction.SOUTH;
      }

      return new MovingObjectPosition(vec36, enumfacing);
    }
  }

  public double nearestDistanceTo(NativeVector fieldPoint) {
    NativeVector nativeVector = nearestPointTo(fieldPoint);
    return nativeVector.distanceTo(fieldPoint);
  }

  private NativeVector nearestPointTo(NativeVector fieldPoint) {
    double refX = fieldPoint.xCoord;
    double refY = fieldPoint.yCoord;
    double refZ = fieldPoint.zCoord;
    double pointX = refX > maxX ? maxX : Math.max(refX, minX);
    double pointY = refY > minY ? minY : Math.max(refY, minY);
    double pointZ = refZ > maxZ ? maxZ : Math.max(refZ, minZ);
    return new NativeVector(pointX, pointY, pointZ);
  }

  public BoundingBox addJustMaxY(double expansionY) {
    return new BoundingBox(minX, minY, minZ, maxX, this.maxY + expansionY, maxZ);
  }

  /**
   * Checks if the specified vector is within the YZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInYZ(NativeVector vec) {
    return vec != null && vec.yCoord >= this.minY && vec.yCoord <= this.maxY && vec.zCoord >= this.minZ && vec.zCoord <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXZ(NativeVector vec) {
    return vec != null && vec.xCoord >= this.minX && vec.xCoord <= this.maxX && vec.zCoord >= this.minZ && vec.zCoord <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XY dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXY(NativeVector vec) {
    return vec != null && vec.xCoord >= this.minX && vec.xCoord <= this.maxX && vec.yCoord >= this.minY && vec.yCoord <= this.maxY;
  }

  // box
  public String toString() {
    return "Box{" + this.minX + ", " + this.minY + ", " + this.minZ + " -> " + this.maxX + ", " + this.maxY + ", " + this.maxZ + "}";
  }

  // position
//  public String toString() {
//    return "" + (minX + (maxX - minX) / 2d) + "," + (minY + (maxY - minY) / 2d) + "," + (minZ + (maxZ - minZ) / 2d);
//  }

  // width and height
//  public String toString() {
//    return "" + (maxX - minX) + "," + (maxY - minY) + "," + (maxZ - minZ);
//  }

  public String toCompactString() {
    return "" + MathHelper.formatDouble(this.minX, 3) + ", " + MathHelper.formatDouble(this.minY, 3) + ", " + MathHelper.formatDouble(this.minZ, 3) + " -> " + MathHelper.formatDouble(this.maxX, 3) + ", " + MathHelper.formatDouble(this.maxY, 3) + ", " + MathHelper.formatDouble(this.maxZ, 3);
  }

  public boolean func_181656_b() {
    return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
  }

  public boolean isOriginBox() {
    return originBox;
  }

  public void makeOriginBox() {
    this.originBox = true;
  }

  public BoundingBox copy() {
    return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BoundingBox that = (BoundingBox) o;

    if (Double.compare(that.minX, minX) != 0) return false;
    if (Double.compare(that.minY, minY) != 0) return false;
    if (Double.compare(that.minZ, minZ) != 0) return false;
    if (Double.compare(that.maxX, maxX) != 0) return false;
    if (Double.compare(that.maxY, maxY) != 0) return false;
    return Double.compare(that.maxZ, maxZ) == 0;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    temp = Double.doubleToLongBits(minX);
    result = (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(minY);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(minZ);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(maxX);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(maxY);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(maxZ);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  public static BoundingBox fromBounds(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double d0 = Math.min(x1, x2);
    double d1 = Math.min(y1, y2);
    double d2 = Math.min(z1, z2);
    double d3 = Math.max(x1, x2);
    double d4 = Math.max(y1, y2);
    double d5 = Math.max(z1, z2);
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  public static BoundingBox fromX16Bounds(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double fromX = Math.min(x1, x2);
    double fromY = Math.min(y1, y2);
    double fromZ = Math.min(z1, z2);
    double toX = Math.max(x1, x2);
    double toY = Math.max(y1, y2);
    double toZ = Math.max(z1, z2);
    return new BoundingBox(
      fromX / 16D, fromY / 16D, fromZ / 16D,
      toX / 16D, toY / 16D, toZ / 16D
    );
  }

  public static BoundingBox fromPosition(User user, Location location) {
    return fromPosition(user, location.getX(), location.getY(), location.getZ());
  }

  public static BoundingBox fromPosition(User user, Position position) {
    return fromPosition(user, position.xCoordinate(), position.yCoordinate(), position.zCoordinate());
  }

  public static BoundingBox fromPosition(User user, BlockPosition position) {
    return fromPosition(user, position.xCoord, position.yCoord, position.zCoord);
  }

  public static BoundingBox fromPosition(
    User user,
    double positionX, double positionY, double positionZ
  ) {
    MovementMetadata movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    double width = movementData.hasRidingEntity() ? movementData.width / 2.0f : movementData.widthRounded;
    float height = movementData.height;

    double newYMax;
    if (clientData.roundEnvironmentNumbers()) {
      newYMax = Math.round((positionY + height) * 10000000d) / 10000000d;
    } else {
      newYMax = Math.round((positionY + height) * 10000000000d) / 10000000000d;
    }
    return new BoundingBox(
      positionX - width, positionY, positionZ - width,
      positionX + width, newYMax, positionZ + width
    );
  }

  public static BoundingBox fromNative(Object nativeBB) {
    return WrapperLinkage.boundingBoxOf(nativeBB);
  }

  // just assuming defaults - please remove
  private final static float PLAYER_HEIGHT = 1.8f;
  private final static double HALF_WIDTH = 0.3;

  @Deprecated
  // doomed to be inaccurate, just guesses default BB size - please remove ~richy
  public static BoundingBox fromPosition(
    double positionX, double positionY, double positionZ
  ) {
    return new BoundingBox(
      positionX - HALF_WIDTH, positionY, positionZ - HALF_WIDTH,
      positionX + HALF_WIDTH, positionY + PLAYER_HEIGHT, positionZ + HALF_WIDTH
    );
  }
}