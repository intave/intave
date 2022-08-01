package de.jpx3.intave.block.shape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class BlockShapes {
  private static final BlockShape EMPTY = new EmptyBlockShape();
  private static final BlockShape CUBE = new CubeShape(0,0,0);

  public static BlockShape emptyShape() {
    return EMPTY;
  }

  public static BlockShape originCube() {
    return CUBE;
  }

  public static BlockShape cubeAt(int posX, int posY, int posZ) {
    return new CubeShape(posX, posY, posZ);
  }

  public static BlockShape merge(@NotNull List<? extends BlockShape> shapes) {
    switch (shapes.size()) {
      case 0:
        return emptyShape();
      case 1:
        return shapes.get(0);
      case 2:
        return shapeFromTwo(shapes.get(0), shapes.get(1));
      default:
        return shapeFromMultiple(shapes);
    }
  }

  public static BlockShape merge(@NotNull BlockShape... shapes) {
    switch (shapes.length) {
      case 0:
        return emptyShape();
      case 1:
        return shapes[0];
      case 2:
        return shapeFromTwo(shapes[0], shapes[1]);
      default:
        return shapeFromMultiple(shapes);
    }
  }

  private static BlockShape shapeFromTwo(@NotNull BlockShape first, @NotNull BlockShape second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty() || first == second) {
      return first;
    }
    return new MergeBlockShape(first, second);
  }

  private static BlockShape shapeFromMultiple(BlockShape... boundingBoxes) {
    return new ArrayBlockShape(boundingBoxes);
  }

  private static BlockShape shapeFromMultiple(@NotNull List<? extends BlockShape> shapes) {
    return new ArrayBlockShape(shapes);
  }

  public static Function<@Nullable List<BlockShape>, @NotNull BlockShape> shapeMerger() {
    return shapes -> shapes == null ? emptyShape() : merge(shapes);
  }
}
