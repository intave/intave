package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.Direction.Axis.*;

final class CubeShape extends MemoryTraced implements BlockShape {
  private final int x, y, z;

  CubeShape(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox other, double offset) {
    // always collide if axis is selected
    boolean collidesInXAxis = axis == X_AXIS || other.max(X_AXIS) > this.min(X_AXIS) && other.min(X_AXIS) < this.max(X_AXIS);
    boolean collidesInYAxis = axis == Y_AXIS || other.max(Y_AXIS) > this.min(Y_AXIS) && other.min(Y_AXIS) < this.max(Y_AXIS);
    boolean collidesInZAxis = axis == Z_AXIS || other.max(Z_AXIS) > this.min(Z_AXIS) && other.min(Z_AXIS) < this.max(Z_AXIS);

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
  public double min(Direction.Axis axis) {
    return axis.select(x, y, z);
  }

  @Override
  public double max(Direction.Axis axis) {
    return axis.select(x, y, z) + 1;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new CubeShape(x + posX, y + posY, z + posZ);
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new CubeShape(x - posX, y - posY, z - posZ);
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return boundingBox.maxX > min(X_AXIS) && boundingBox.minX < max(X_AXIS) &&
      boundingBox.maxY > min(Y_AXIS) && boundingBox.minY < max(Y_AXIS) &&
      boundingBox.maxZ > min(Z_AXIS) && boundingBox.minZ < max(Z_AXIS);
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    origin = new Position(origin.getX() - x, origin.getY() - y, origin.getZ() - z);
    target = new Position(target.getX() - x, target.getY() - y, target.getZ() - z);

    Position xMin = raytraceX(origin, target, 0);
    Position xMax = raytraceX(origin, target, 1);
    Position yMin = raytraceY(origin, target, 0);
    Position yMax = raytraceY(origin, target, 1);
    Position zMin = raytraceZ(origin, target, 0);
    Position zMax = raytraceZ(origin, target, 1);

    if (!xIntersectsWith(xMax)) {
      xMax = null;
    }
    if (!xIntersectsWith(xMin)) {
      xMin = null;
    }

    if (!yIntersectsWith(yMax)) {
      yMax = null;
    }
    if (!yIntersectsWith(yMin)) {
      yMin = null;
    }

    if (!zIntersectsWith(zMax)) {
      zMax = null;
    }

    if (!zIntersectsWith(zMin)) {
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

  private boolean xIntersectsWith(Position position) {
    if (position == null) {
      return false;
    }
    return position.getY() >= 0 && position.getY() <= 1 && position.getZ() >= 0 && position.getZ() <= 1;
  }

  private boolean yIntersectsWith(Position position) {
    if (position == null) {
      return false;
    }
    return position.getX() >= 0 && position.getX() <= 1 && position.getZ() >= 0 && position.getZ() <= 1;
  }

  private boolean zIntersectsWith(Position position) {
    if (position == null) {
      return false;
    }
    return position.getX() >= 0 && position.getX() <= 1 && position.getY() >= 0 && position.getY() <= 1;
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

  private static final Reference<List<BoundingBox>> NULL_REFERENCE = new WeakReference<>(null);
  private Reference<List<BoundingBox>> boundingBoxCache = NULL_REFERENCE;

  @Override
  public List<BoundingBox> boundingBoxes() {
    List<BoundingBox> boundingBoxes = boundingBoxCache.get();
    if (boundingBoxes == null) {
      boundingBoxes = Collections.singletonList(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
      boundingBoxCache = new WeakReference<>(boundingBoxes);
    }
    return boundingBoxes;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isCubic() {
    return true;
  }

  @Override
  public String toString() {
    return String.format("{%d, %d, %d}", x, y, z);
  }
}
