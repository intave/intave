package de.jpx3.intave.block.shape;

import java.util.ArrayList;
import java.util.List;

public final class ShapeCombiner {
  private final static ShapeCombiner EMPTY = new ShapeCombiner();
  private final List<BlockShape> shapes = new ArrayList<>();

  private ShapeCombiner() {
  }

  private ShapeCombiner(BlockShape init) {
    shapes.add(init);
  }

  public ShapeCombiner append(BlockShape blockShape) {
    if (EMPTY == this) {
      return new ShapeCombiner(blockShape);
    }
    shapes.add(blockShape);
    return this;
  }

  public BlockShape compile() {
    if (EMPTY == this) {
      return BlockShapes.empty();
    } else {
      int size = shapes.size();
      if (size == 1) {
        return shapes.get(0);
      } else if (size == 2) {
        return new MergeBlockShape(shapes.get(0), shapes.get(1));
      } else {
        return new ArrayBlockShape(shapes);
      }
    }
  }

  public static ShapeCombiner create() {
    return EMPTY;
  }
}
