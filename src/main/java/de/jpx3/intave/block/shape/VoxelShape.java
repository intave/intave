package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.AxisRotation;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;

import java.util.BitSet;
import java.util.List;

import static de.jpx3.intave.shade.ClientMathHelper.binarySearch;
import static de.jpx3.intave.shade.Direction.Axis.*;

public final class VoxelShape implements BlockShape {
  private final BitSet storage;
  private final int sizeX, sizeY, sizeZ;
  private final int startX, startY, startZ;
  private final int endX, endY, endZ;

  public VoxelShape(int xSizeIn, int ySizeIn, int zSizeIn) {
    this(xSizeIn, ySizeIn, zSizeIn, xSizeIn, ySizeIn, zSizeIn, 0, 0, 0);
  }

  public VoxelShape(int xSizeIn, int ySizeIn, int zSizeIn, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    this.storage = new BitSet(xSizeIn * ySizeIn * zSizeIn);
    this.sizeX = xSizeIn;
    this.sizeY = ySizeIn;
    this.sizeZ = zSizeIn;
    this.startX = minX;
    this.startY = minY;
    this.startZ = minZ;
    this.endX = maxX;
    this.endY = maxY;
    this.endZ = maxZ;
  }

  public boolean filled(int x, int y, int z) {
    return this.storage.get(this.bitIndex(x, y, z));
  }

  public void setFilled(int x, int y, int z, boolean filled) {
    this.storage.set(this.bitIndex(x, y, z), filled);
  }

  private int bitIndex(int x, int y, int z) {
    return (x * this.sizeY + y) * this.sizeZ + z;
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox collisionBox, double desiredOffset) {
    if (this.isEmpty()) {
      return desiredOffset;
    } else if (Math.abs(desiredOffset) < 0.0000001) {
      return 0.0D;
    } else {
      AxisRotation axisrotation = AxisRotation.from(axis, X_AXIS).reverse();
      Direction.Axis xRotated = axisrotation.cycle(X_AXIS);
      Direction.Axis yRotated = axisrotation.cycle(Y_AXIS);
      Direction.Axis zRotated = axisrotation.cycle(Z_AXIS);
      double d0 = collisionBox.max(xRotated);
      double d1 = collisionBox.min(xRotated);
      int i = this.findIndex(xRotated, d1 + 0.0000001);
      int j = this.findIndex(xRotated, d0 - 0.0000001);
      int k = Math.max(0, this.findIndex(yRotated, collisionBox.min(yRotated) + 0.0000001));
      int l = Math.min(this.size(yRotated), this.findIndex(yRotated, collisionBox.max(yRotated) - 0.0000001) + 1);
      int i1 = Math.max(0, this.findIndex(zRotated, collisionBox.min(zRotated) + 0.0000001));
      int j1 = Math.min(this.size(zRotated), this.findIndex(zRotated, collisionBox.max(zRotated) - 0.0000001) + 1);
      int k1 = this.size(xRotated);
      if (desiredOffset > 0.0D) {
        for (int l1 = j + 1; l1 < k1; ++l1) {
          for (int i2 = k; i2 < l; ++i2) {
            for (int j2 = i1; j2 < j1; ++j2) {
              if (this.isFullWide(axisrotation, l1, i2, j2)) {
                double d2 = this.get(xRotated, l1) - d0;
                if (d2 >= -1.0E-7D) {
                  desiredOffset = Math.min(desiredOffset, d2);
                }
                return desiredOffset;
              }
            }
          }
        }
      } else if (desiredOffset < 0.0D) {
        for (int k2 = i - 1; k2 >= 0; --k2) {
          for (int l2 = k; l2 < l; ++l2) {
            for (int i3 = i1; i3 < j1; ++i3) {
              if (this.isFullWide(axisrotation, k2, l2, i3)) {
                double d3 = this.get(xRotated, k2 + 1) - d1;
                if (d3 <= 1.0E-7D) {
                  desiredOffset = Math.max(desiredOffset, d3);
                }
                return desiredOffset;
              }
            }
          }
        }
      }
      return desiredOffset;
    }
  }

  private double get(Direction.Axis axis, int slot) {
    return (double) slot / (double) size(axis);
  }

  private int findIndex(Direction.Axis axis, double value) {
    return binarySearch(0, this.size(axis) + 1,
      (input) -> input >= 0 && (input > this.size(axis) || value < this.get(axis, input))
    ) - 1;
  }

  public boolean isFullWide(AxisRotation p_197824_1_, int x, int y, int z) {
    return this.isFullWide(p_197824_1_.cycle(x, y, z, X_AXIS), p_197824_1_.cycle(x, y, z, Y_AXIS), p_197824_1_.cycle(x, y, z, Z_AXIS));
  }

  public boolean isFullWide(int x, int y, int z) {
    return x >= 0 && y >= 0 && z >= 0 && x < this.sizeX && y < this.sizeY && z < this.sizeZ && this.filled(x, y, z);
  }

  public int size(Direction.Axis axis) {
    return axis.select(sizeX, sizeY, sizeZ);
  }

  @Override
  public double min(Direction.Axis axis) {
    return axis.select(startX, startY, startZ);
  }

  @Override
  public double max(Direction.Axis axis) {
    return axis.select(endX, endY, endZ);
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return false;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return null;
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return null;
  }

  @Override
  public List<BoundingBox> boundingBoxes() {
    return null;
  }

  public boolean isEmpty() {
    return this.storage.isEmpty();
  }

  @Override
  public String toString() {
    return "VoxelShape{" +
      "storage=" + storage +
      ", sizeX=" + sizeX +
      ", sizeY=" + sizeY +
      ", sizeZ=" + sizeZ +
      ", startX=" + startX +
      ", startY=" + startY +
      ", startZ=" + startZ +
      ", endX=" + endX +
      ", endY=" + endY +
      ", endZ=" + endZ +
      '}';
  }
}
