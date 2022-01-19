package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.shade.Direction.Axis.*;

public final class CubeShape extends MemoryTraced implements BlockShape {
  private final int x, y, z;

  public CubeShape(int x, int y, int z) {
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

  public double min(Direction.Axis axis) {
    return axis.select(x, y, z);
  }

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

  private final static Reference<List<BoundingBox>> EMPTY_REFERENCE = new WeakReference<>(null);
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
