package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ArrayBlockShape extends MemoryTraced implements BlockShape {
  private final BlockShape[] contents;

  public ArrayBlockShape(List<BlockShape> contents) {
    this.contents = contents.toArray(new BlockShape[0]);
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset) {
    for (BlockShape shape : contents) {
      offset = shape.allowedOffset(axis, entity, offset);
    }
    return offset;
  }

  @Override
  public double min(Direction.Axis axis) {
    double min = Integer.MAX_VALUE;
    boolean hasMin = false;
    for (BlockShape content : contents) {
      min = Math.min(content.min(axis), min);
      hasMin = true;
    }
    return hasMin ? min : 0;
  }

  @Override
  public double max(Direction.Axis axis) {
    double max = Integer.MIN_VALUE;
    boolean hasMax = false;
    for (BlockShape content : contents) {
      max = Math.max(content.min(axis), max);
      hasMax = true;
    }
    return hasMax ? max : 0;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    List<BlockShape> list = new ArrayList<>();
    for (BlockShape blockShape : contents) {
      list.add(blockShape.contextualized(posX, posY, posZ));
    }
    return new ArrayBlockShape(list);
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    List<BlockShape> list = new ArrayList<>();
    for (BlockShape blockShape : contents) {
      list.add(blockShape.normalized(posX, posY, posZ));
    }
    return new ArrayBlockShape(list);
  }

  @Override
  public List<BoundingBox> boundingBoxes() {
    List<BoundingBox> boundingBoxes = null;
    for (BlockShape content : contents) {
      List<BoundingBox> added = content.boundingBoxes();
      if (!added.isEmpty()) {
        if (boundingBoxes == null) {
          boundingBoxes = new ArrayList<>();
        }
        boundingBoxes.addAll(added);
      }
    }
    return boundingBoxes == null ? Collections.emptyList() : boundingBoxes;
  }

  @Override
  public boolean isEmpty() {
    for (BlockShape content : contents) {
      if (content.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    for (BlockShape content : contents) {
      if (content.intersectsWith(boundingBox)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "ArrayBlockShape{" +
      "contents=" + Arrays.toString(contents) +
      '}';
  }
}
