package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.BoundingBox;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public final class CubeShape implements BlockShape {
  private final int x, y, z;

  public CubeShape(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public double allowedXOffset(BoundingBox other, double offsetX) {
    if (other.maxY > y && other.minY < y + 1 && other.maxZ > z && other.minZ < z + 1) {
      if (offsetX > 0.0D && other.maxX <= x) {
        double d1 = x - other.maxX;
        if (d1 < offsetX) {
          offsetX = d1;
        }
      } else if (offsetX < 0.0D && other.minX >= x + 1) {
        double d0 = x + 1 - other.minX;
        if (d0 > offsetX) {
          offsetX = d0;
        }
      }
    }
    return offsetX;
  }

  public double allowedYOffset(BoundingBox other, double offsetY) {
    if (other.maxX > x && other.minX < x + 1 && other.maxZ > z && other.minZ < z + 1) {
      if (offsetY > 0.0D && other.maxY <= y) {
        double d1 = y - other.maxY;
        if (d1 < offsetY) {
          offsetY = d1;
        }
      } else if (offsetY < 0.0D && other.minY >= y + 1) {
        double d0 = y + 1 - other.minY;
        if (d0 > offsetY) {
          offsetY = d0;
        }
      }
    }
    return offsetY;
  }

  public double allowedZOffset(BoundingBox other, double offsetZ) {
    if (other.maxX > x && other.minX < x + 1 && other.maxY > y && other.minY < y + 1) {
      if (offsetZ > 0.0D && other.maxZ <= z) {
        double d1 = z - other.maxZ;
        if (d1 < offsetZ) {
          offsetZ = d1;
        }
      } else if (offsetZ < 0.0D && other.minZ >= z + 1) {
        double d0 = z + 1 - other.minZ;
        if (d0 > offsetZ) {
          offsetZ = d0;
        }
      }
    }
    return offsetZ;
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
    return boundingBox.maxX > this.x && boundingBox.minX < this.x + 1 && (boundingBox.maxY > this.y && boundingBox.minY < this.y + 1 && boundingBox.maxZ > this.z && boundingBox.minZ < this.z + 1);
  }
}
