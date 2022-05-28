package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;
import de.jpx3.intave.shade.Position;

import javax.annotation.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.shade.Direction.Axis.*;

final class CubeShape extends MemoryTraced implements BlockShape {
  private final int x, y, z;

  CubeShape(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public double allowedOffset(Direction.Axis axis, BoundingBox other, double offset) {
    // always collide if axis is selected
    boolean collidesInXAxis = axis == X_AXIS || other.max(X_AXIS) > this.min(X_AXIS) && other.min(X_AXIS) < this.max(X_AXIS);
    boolean collidesInYAxis = axis == Y_AXIS || (collidesInXAxis && other.max(Y_AXIS) > this.min(Y_AXIS) && other.min(Y_AXIS) < this.max(Y_AXIS));
    boolean collidesInZAxis = axis == Z_AXIS || (collidesInYAxis && other.max(Z_AXIS) > this.min(Z_AXIS) && other.min(Z_AXIS) < this.max(Z_AXIS));

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
  public BlockRaytrace raytrace(Position origin, Position target) {
    double[] distanceStorage = {1.0};
    double differenceX = origin.getX() - target.getX();
    double differenceY = origin.getY() - target.getY();
    double differenceZ = origin.getZ() - target.getZ();
    Direction direction = xyzDirectionRaytrace(target, distanceStorage, differenceX, differenceY, differenceZ);
    return direction == null ? BlockRaytrace.none() : new BlockRaytrace(direction, distanceStorage[0]);
  }

  private static final double TOLERANCE = 0.0000001;

  @SuppressWarnings({"SuspiciousNameCombination", "ConstantConditions"})
  @Nullable
  private Direction xyzDirectionRaytrace(Position targetPosition, double[] distanceStorage, double differenceX, double differenceY, double differenceZ) {
    double minX = min(X_AXIS);
    double maxX = max(X_AXIS);
    double minY = min(Y_AXIS);
    double maxY = max(Y_AXIS);
    double minZ = min(Z_AXIS);
    double maxZ = max(Z_AXIS);
    double targetPositionX = targetPosition.getX();
    double targetPositionY = targetPosition.getY();
    double targetPositionZ = targetPosition.getZ();
    Direction direction = null;
    if (differenceX > TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceX, differenceY, differenceZ, minX, minY, maxY, minZ, maxZ, Direction.WEST, targetPositionX, targetPositionY, targetPositionZ);
    } else if (differenceX < -TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceX, differenceY, differenceZ, maxX, minY, maxY, minZ, maxZ, Direction.EAST, targetPositionX, targetPositionY, targetPositionZ);
    }
    if (differenceY > TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceY, differenceZ, differenceX, minY, minZ, maxZ, minX, maxX, Direction.DOWN, targetPositionY, targetPositionZ, targetPositionX);
    } else if (differenceY < -TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceY, differenceZ, differenceX, maxY, minZ, maxZ, minX, maxX, Direction.UP, targetPositionY, targetPositionZ, targetPositionX);
    }
    if (differenceZ > TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceZ, differenceX, differenceY, minZ, minX, maxX, minY, maxY, Direction.NORTH, targetPositionZ, targetPositionX, targetPositionY);
    } else if (differenceZ < -TOLERANCE) {
      direction = directionRaytrace(distanceStorage, direction, differenceZ, differenceX, differenceY, maxZ, minX, maxX, minY, maxY, Direction.SOUTH, targetPositionZ, targetPositionX, targetPositionY);
    }
    return direction;
  }

  @Nullable
  private static Direction directionRaytrace(
    double[] distanceStorage,
    @Nullable Direction inheritDirection,
    double differenceMain, double differenceUp, double differenceRight,
    double minMain,
    double minUp, double maxUp,
    double minRight, double maxRight,
    Direction selectedDirection,
    double targetMain, double targetUp, double targetRight
  ) {
    double normalizedStepMain = (minMain - targetMain) / differenceMain;
    if (normalizedStepMain > 0.0 && normalizedStepMain < distanceStorage[0]) {
      double normalizedStepUp = targetUp + normalizedStepMain * differenceUp;
      double normalizedStepRight = targetRight + normalizedStepMain * differenceRight;
      if (
        minUp - TOLERANCE < normalizedStepUp && normalizedStepUp < maxUp + TOLERANCE &&
          minRight - TOLERANCE < normalizedStepRight && normalizedStepRight < maxRight + TOLERANCE
      ) {
        distanceStorage[0] = normalizedStepMain;
        return selectedDirection;
      }
    }
    return inheritDirection;
  }

  private static final Reference<List<BoundingBox>> EMPTY_REFERENCE = new WeakReference<>(null);
  private Reference<List<BoundingBox>> boundingBoxCache = EMPTY_REFERENCE;

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

  public boolean intersectsWith(BoundingBox boundingBox) {
    return boundingBox.maxX > min(X_AXIS) && boundingBox.minX < max(X_AXIS) &&
      boundingBox.maxY > min(Y_AXIS) && boundingBox.minY < max(Y_AXIS) &&
      boundingBox.maxZ > min(Z_AXIS) && boundingBox.minZ < max(Z_AXIS);
  }

  @Override
  public String toString() {
    return "CubeShape{" +
      "x=" + x +
      ", y=" + y +
      ", z=" + z +
      '}';
  }
}
