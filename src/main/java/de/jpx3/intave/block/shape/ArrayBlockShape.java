package de.jpx3.intave.block.shape;

import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArrayBlockShape extends MemoryTraced implements BlockShape {
  private final BlockShape[] contents;

  public ArrayBlockShape(List<BlockShape> contents) {
    this.contents = contents.toArray(new BlockShape[0]);
  }

  @Override
  public double allowedXOffset(BoundingBox entity, double offsetX) {
    for (BlockShape shape : contents) {
      offsetX = shape.allowedXOffset(entity, offsetX);
    }
    return offsetX;
  }

  @Override
  public double allowedYOffset(BoundingBox entity, double offsetY) {
    for (BlockShape shape : contents) {
      offsetY = shape.allowedYOffset(entity, offsetY);
    }
    return offsetY;
  }

  @Override
  public double allowedZOffset(BoundingBox entity, double offsetZ) {
    for (BlockShape shape : contents) {
      offsetZ = shape.allowedZOffset(entity, offsetZ);
    }
    return offsetZ;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new ArrayBlockShape(contextualize(boundingBoxes(), posX, posY, posZ));
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new ArrayBlockShape(normalize(boundingBoxes(), posX, posY, posZ));
  }

  private static List<BlockShape> contextualize(List<BoundingBox> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    List<BlockShape> result = new ArrayList<>(boundingBoxes.size());
    for (int i = 0; i < boundingBoxes.size(); i++) {
      BoundingBox boundingBox = boundingBoxes.get(i);
      result.add(i, boundingBox.contextualized(posX, posY, posZ));
    }
    return result;
  }

  private static List<BlockShape> normalize(List<BoundingBox> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return Collections.emptyList();
    }
    List<BlockShape> result = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < result.size(); i++) {
      result.set(i, result.get(i).normalized(posX, posY, posZ));
    }
    return result;
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
    return contents.length == 0;
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
}
