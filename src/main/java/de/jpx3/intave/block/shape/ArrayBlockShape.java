package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;
import de.jpx3.intave.shade.Position;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
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
  public BlockRaytrace raytrace(Position origin, Position target) {
    BlockRaytrace raytrace = null;
    for (BlockShape content : contents) {
      BlockRaytrace added = content.raytrace(origin, target);
      if (added != BlockRaytrace.none()) {
        if (raytrace == null) {
          raytrace = added;
        } else {
          raytrace = raytrace.minSelect(added);
        }
      }
    }
    return raytrace;
  }

  private final static Reference<List<BoundingBox>> EMPTY_REFERENCE = new WeakReference<>(null);
  private Reference<List<BoundingBox>> boundingBoxCache = EMPTY_REFERENCE;

  @Override
  public List<BoundingBox> boundingBoxes() {
    if (boundingBoxCache.get() == null) {
      List<BoundingBox> boundingBoxes = null;
      for (BlockShape content : contents) {
        List<BoundingBox> added = content.boundingBoxes();
        if (!added.isEmpty()) {
          if (boundingBoxes == null) {
            boundingBoxes = new ArrayList<>(contents.length);
          }
          if (added.size() == 1) {
            boundingBoxes.add(added.get(0));
          } else {
            boundingBoxes.addAll(added);
          }
        }
      }
      List<BoundingBox> boundingBoxList = boundingBoxes == null ? Collections.emptyList() : boundingBoxes;
      boundingBoxCache = new WeakReference<>(boundingBoxList);
    }
    return boundingBoxCache.get();
  }

  @Override
  public boolean isEmpty() {
    for (BlockShape content : contents) {
      if (!content.isEmpty()) {
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
