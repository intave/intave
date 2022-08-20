package de.jpx3.intave.block.shape;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;

public final class BlockShapeTests extends Tests {
  public BlockShapeTests() {
    super("BST");
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void emptyMustBeEmpty() {
    BlockShape empty = BlockShapes.emptyShape();
    assertTrue(empty.isEmpty());
    assertFalse(empty.isCubic());
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void testArrayMustBeEmpty() {
    BlockShape empty = BlockShapes.emptyShape();
    ArrayBlockShape array = new ArrayBlockShape(empty);
    assertTrue(array.isEmpty());
  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void arrayMustNotBeEmptyIfContainsNonEmpty() {
    BlockShape empty = BlockShapes.emptyShape();
    BlockShape nonEmpty = BlockShapes.originCube();
    ArrayBlockShape array = new ArrayBlockShape(empty, nonEmpty);
    assertFalse(array.isEmpty());
  }

  @Test(
    testCode = "D",
    severity = Severity.ERROR
  )
  public void testCubicBoundingBoxMustBeCubic() {
    BlockShape blockShape = BoundingBox.originFrom(0, 0, 0, 1, 1, 1);
    assertTrue(blockShape.isCubic());
    blockShape = blockShape.contextualized(1, 1, 1);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "E",
    severity = Severity.ERROR
  )
  public void testCubeShapeMustBeCubic() {
    BlockShape blockShape = BlockShapes.originCube();
    assertTrue(blockShape.isCubic());
    blockShape = blockShape.contextualized(1, 1, 1);
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "F",
    severity = Severity.ERROR
  )
  public void testArrayShapeMustBeCubic() {
    BlockShape blockShape = BlockShapes.originCube();
    blockShape.contextualized(1, 1, 1);
    for (int i = 0; i < 10; i++) {
      blockShape = new ArrayBlockShape(blockShape);
    }
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "G",
    severity = Severity.ERROR
  )
  public void testMergeShapeMustBeCubic() {
    BlockShape blockShape = BlockShapes.originCube();
    blockShape.contextualized(1, 1, 1);
    for (int i = 0; i < 10; i++) {
      blockShape = new MergeBlockShape(blockShape, BlockShapes.emptyShape());
    }
    assertTrue(blockShape.isCubic());
  }

  @Test(
    testCode = "H",
    severity = Severity.ERROR
  )
  public void testMergeShapeMustBeEmpty() {
    BlockShape blockShape = BlockShapes.originCube();
    blockShape.contextualized(1, 1, 1);
    for (int j = 0; j < 10; j++) {
      for (int i = 0; i < j; i++) {
        BlockShape shape = BlockShapes.originCube();
        shape.contextualized(i, i, i);
        blockShape = new MergeBlockShape(blockShape, shape);
      }
      if (j == 0) {
        assertTrue(blockShape.isCubic());
      } else {
        assertFalse(blockShape.isCubic());
      }
    }
  }

  @Test(
    testCode = "I",
    severity = Severity.ERROR
  )
  public void testMerge() {
    BlockShape blockShape = BlockShapes.originCube();
    BlockShape empty = BlockShapes.emptyShape();
    assertSame(blockShape, BlockShapes.merge(blockShape, empty));
    assertSame(blockShape, BlockShapes.merge(blockShape, blockShape));
  }
}
