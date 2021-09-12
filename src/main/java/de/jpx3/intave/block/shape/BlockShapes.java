package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class BlockShapes {
  private final static BlockShape EMPTY = new EmptyBlockShape();

  public static BlockShape ofBoxes(List<BoundingBox> boundingBoxes) {
    if (boundingBoxes.isEmpty()) {
      return EMPTY;
    } else if (boundingBoxes.size() == 1) {
      return boundingBoxes.get(0);
    } else if (boundingBoxes.size() == 2) {
      return new MergeBlockShape(boundingBoxes.get(0), boundingBoxes.get(1));
    } else {
      return new ArrayBlockShape(new ArrayList<>(boundingBoxes));
    }
  }

  public static BlockShape empty() {
    return EMPTY;
  }

  public static BlockShape merge(BlockShape shapeA, BlockShape shapeB) {
    if (shapeA.isEmpty()) {
      return shapeB;
    }
    if (shapeB.isEmpty()) {
      return shapeA;
    }
    return new MergeBlockShape(shapeA, shapeB);
  }
}
