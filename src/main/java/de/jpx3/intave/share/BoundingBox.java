package de.jpx3.intave.share;

import de.jpx3.intave.block.shape.BlockRaytrace;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.SimulationEnvironment;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.share.Direction.Axis.*;

public final class BoundingBox extends MemoryTraced implements BlockShape {
  private static final double EPSILON = 0.00001;
  private static final double TOLERANCE = 0.0000001;
  // just assuming defaults - please remove
  private static final float PLAYER_HEIGHT = 1.8f;
  private static final double HALF_WIDTH = 0.3;
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

  public static BoundingBox originFrom(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double d0 = Math.min(x1, x2);
    double d1 = Math.min(y1, y2);
    double d2 = Math.min(z1, z2);
    double d3 = Math.max(x1, x2);
    double d4 = Math.max(y1, y2);
    double d5 = Math.max(z1, z2);
    BoundingBox boundingBox = new BoundingBox(d0, d1, d2, d3, d4, d5);
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  public static BoundingBox originFromX16(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double fromX = Math.min(x1, x2);
    double fromY = Math.min(y1, y2);
    double fromZ = Math.min(z1, z2);
    double toX = Math.max(x1, x2);
    double toY = Math.max(y1, y2);
    double toZ = Math.max(z1, z2);
    BoundingBox boundingBox = new BoundingBox(
      fromX / 16D, fromY / 16D, fromZ / 16D,
      toX / 16D, toY / 16D, toZ / 16D
    );
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, Location location) {
    return fromPosition(user, environment, location.getX(), location.getY(), location.getZ());
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, Position position) {
    return fromPosition(user, environment, position.getX(), position.getY(), position.getZ());
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, BlockPosition position) {
    return fromPosition(user, environment, position.xCoord, position.yCoord, position.zCoord);
  }

  public static BoundingBox fromPosition(
    User user,
    SimulationEnvironment environment,
    double positionX, double positionY, double positionZ
  ) {
    double width = environment.isInVehicle() ? environment.width() / 2.0f : environment.widthRounded();
    float height = environment.height();

    double newYMax;
    if (user.meta().protocol().roundEnvironmentNumbers()) {
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
    return WrapperConverter.boundingBoxFromAABB(nativeBB);
  }

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

  public BoundingBox expand(Vector vec) {
    return expand(vec.getX(), vec.getY(), vec.getZ());
  }

  public BoundingBox expand(Motion motion) {
    return expand(motion.motionX(), motion.motionY(), motion.motionZ());
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
    BoundingBox resulting = new BoundingBox(d0, d1, d2, d3, d4, d5);
    if (isOriginBox()) {
      resulting.makeOriginBox();
    }
    return resulting;
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
    BoundingBox resulting = new BoundingBox(d0, d1, d2, d3, d4, d5);
    if (this.isOriginBox()) {
      resulting.makeOriginBox();
    }
    return resulting;
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

  public BoundingBox originOffset(double x, double y, double z) {
    BoundingBox boundingBox = new BoundingBox(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    boundingBox.makeOriginBox();
    return boundingBox;
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

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    if (!isOriginBox()) {
      return this;
    }
    return offset(posX, posY, posZ);
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    if (isOriginBox()) {
      return this;
    }
    BoundingBox normalized = offset(-posX, -posY, -posZ);
    normalized.makeOriginBox();
    return normalized;
  }

  private List<BoundingBox> selfInListCache;

  @Override
  @Deprecated
  public List<BoundingBox> boundingBoxes() {
    if (selfInListCache == null) {
      selfInListCache = Collections.singletonList(this);
    }
    return selfInListCache;
  }

  @Override
  public boolean isEmpty() {
    return minX == maxX || minY == maxY || minZ == maxZ;
  }

  @Override
  public boolean isCubic() {
    if (isOriginBox()) {
      return minX == 0 && minY == 0 && minZ == 0 &&
        maxX == 1 && maxY == 1 && maxZ == 1;
    } else {
      return Math.abs(maxX - minX - 1) < EPSILON &&
        Math.abs(maxY - minY - 1) < EPSILON &&
        Math.abs(maxZ - minZ - 1) < EPSILON;
    }
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

  public double centerX() {
    return (minX + maxX) / 2.0;
  }

  public double centerY() {
    return (minY + maxY) / 2.0;
  }

  public double centerZ() {
    return (minZ + maxZ) / 2.0;
  }

  /**
   * Returns the average length of the edges of the bounding box.
   */
  public double averageEdgeLength() {
    double d0 = this.maxX - this.minX;
    double d1 = this.maxY - this.minY;
    double d2 = this.maxZ - this.minZ;
    return (d0 + d1 + d2) / 3.0D;
  }

  // position
//  public String toString() {
//    return "" + (minX + (maxX - minX) / 2d) + "," + (minY + (maxY - minY) / 2d) + "," + (minZ + (maxZ - minZ) / 2d);
//  }

  // width and height
  public String toString() {
    return String.format("size{%s,%s,%s}@mid{%s,%s,%s}", maxX - minX, maxY - minY, maxZ - minZ, minX + (maxX - minX) / 2d, minY + (maxY - minY) / 2d, minZ + (maxZ - minZ) / 2d);
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

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    int blockX = floor(origin.getX());
    int blockY = floor(origin.getY());
    int blockZ = floor(origin.getZ());
    origin = new Position(origin.getX() - blockX, origin.getY() - blockY, origin.getZ() - blockZ);
    target = new Position(target.getX() - blockX, target.getY() - blockY, target.getZ() - blockZ);

    Position xMin = raytraceX(origin, target, minX - blockX);
    Position xMax = raytraceX(origin, target, maxX - blockX);
    Position yMin = raytraceY(origin, target, minY - blockY);
    Position yMax = raytraceY(origin, target, maxY - blockY);
    Position zMin = raytraceZ(origin, target, minZ - blockZ);
    Position zMax = raytraceZ(origin, target, maxZ - blockZ);

    if (!xIntersectsWith(xMax, blockY, blockZ)) {
      xMax = null;
    }
    if (!xIntersectsWith(xMin, blockY, blockZ)) {
      xMin = null;
    }

    if (!yIntersectsWith(yMax, blockX, blockZ)) {
      yMax = null;
    }
    if (!yIntersectsWith(yMin, blockX, blockZ)) {
      yMin = null;
    }

    if (!zIntersectsWith(zMax, blockX, blockY)) {
      zMax = null;
    }
    if (!zIntersectsWith(zMin, blockX, blockY)) {
      zMin = null;
    }

    Position closest = null;
    if (xMin != null/* && (closest == null || origin.distanceSquared(xMin) < origin.distanceSquared(closest))*/) {
      closest = xMin;
    }
    if (xMax != null && (closest == null || origin.distanceSquared(xMax) < origin.distanceSquared(closest))) {
      closest = xMax;
    }

    if (yMin != null && (closest == null || origin.distanceSquared(yMin) < origin.distanceSquared(closest))) {
      closest = yMin;
    }
    if (yMax != null && (closest == null || origin.distanceSquared(yMax) < origin.distanceSquared(closest))) {
      closest = yMax;
    }

    if (zMin != null && (closest == null || origin.distanceSquared(zMin) < origin.distanceSquared(closest))) {
      closest = zMin;
    }
    if (zMax != null && (closest == null || origin.distanceSquared(zMax) < origin.distanceSquared(closest))) {
      closest = zMax;
    }

    if (closest == null) {
      return null;
    }

    Direction direction = null;

    if (closest == xMin) {
      direction = Direction.WEST;
    } else if (closest == xMax) {
      direction = Direction.EAST;
    } else if (closest == yMin) {
      direction = Direction.DOWN;
    } else if (closest == yMax) {
      direction = Direction.UP;
    } else if (closest == zMin) {
      direction = Direction.NORTH;
    } else if (closest == zMax) {
      direction = Direction.SOUTH;
    }

    return new BlockRaytrace(direction, closest.distance(origin));
  }

  private boolean xIntersectsWith(Position position, int blockY, int blockZ) {
    if (position == null) {
      return false;
    }
    return position.getY() >= minY - blockY && position.getY() <= maxY - blockY && position.getZ() >= minZ - blockZ && position.getZ() <= maxZ - blockZ;
  }

  private boolean yIntersectsWith(Position position, int blockX, int blockZ) {
    if (position == null) {
      return false;
    }
    return position.getX() >= minX - blockX && position.getX() <= maxX - blockX && position.getZ() >= minZ - blockZ && position.getZ() <= maxZ - blockZ;
  }

  private boolean zIntersectsWith(Position position, int blockX, int blockY) {
    if (position == null) {
      return false;
    }
    return position.getX() >= minX - blockX && position.getX() <= maxX - blockX && position.getY() >= minY - blockY && position.getY() <= maxY - blockY;
  }

  private Position raytraceX(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceX * distanceX < 1.0E-7D) {
      return null;
    } else {
      double k1 = (k - on.getX()) / distanceX;
      if (k1 < 0.0D || k1 > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * k1,
          on.getY() + distanceY * k1,
          on.getZ() + distanceZ * k1
        );
      }
    }
  }

  private Position raytraceY(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceY * distanceY < 1.0E-7D) {
      return null;
    } else {
      double k1 = (k - on.getY()) / distanceY;
      if (k1 < 0.0D || k1 > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * k1,
          on.getY() + distanceY * k1,
          on.getZ() + distanceZ * k1
        );
      }
    }
  }

  private Position raytraceZ(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceZ * distanceZ < 1.0E-7D) {
      return null;
    } else {
      double scale = (k - on.getZ()) / distanceZ;
      if (scale < 0.0D || scale > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * scale,
          on.getY() + distanceY * scale,
          on.getZ() + distanceZ * scale
        );
      }
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

  public BoundingBox move(Motion motion) {
    return move(motion.motionX, motion.motionY, motion.motionZ);
  }

  public BoundingBox move(double x, double y, double z) {
    return new BoundingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
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

  public String toCompactString() {
    return "" + MathHelper.formatDouble(this.minX, 3) + ", " + MathHelper.formatDouble(this.minY, 3) + ", " + MathHelper.formatDouble(this.minZ, 3) + " -> " + MathHelper.formatDouble(this.maxX, 3) + ", " + MathHelper.formatDouble(this.maxY, 3) + ", " + MathHelper.formatDouble(this.maxZ, 3);
  }

  public boolean func_181656_b() {
    return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
  }

  public float width() {
    return (float) (maxX - minX);
  }

  public float height() {
    return (float) (maxY - minY);
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
}