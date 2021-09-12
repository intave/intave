package de.jpx3.intave.block.shape;

import com.google.common.collect.Lists;
import de.jpx3.intave.shade.BoundingBox;

import java.util.List;

public final class MergeBlockShape implements BlockShape {
  private final BlockShape shapeA, shapeB;

  public MergeBlockShape(BlockShape shapeA, BlockShape shapeB) {
    this.shapeA = shapeA;
    this.shapeB = shapeB;
  }

  @Override
  public double allowedXOffset(BoundingBox entity, double offsetX) {
    return shapeA.allowedXOffset(entity, shapeB.allowedXOffset(entity, offsetX));
  }

  @Override
  public double allowedYOffset(BoundingBox entity, double offsetY) {
    return shapeA.allowedYOffset(entity, shapeB.allowedYOffset(entity, offsetY));
  }

  @Override
  public double allowedZOffset(BoundingBox entity, double offsetZ) {
    return shapeA.allowedZOffset(entity, shapeB.allowedZOffset(entity, offsetZ));
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      shapeA.contextualized(posX, posY, posZ),
      shapeA.contextualized(posX, posY, posZ)
    );
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      shapeA.normalized(posX, posY, posZ),
      shapeA.normalized(posX, posY, posZ)
    );
  }

  @Override
  public List<BoundingBox> boundingBoxes() {
    if (shapeA.isEmpty()) {
      return shapeB.boundingBoxes();
    }
    if (shapeB.isEmpty()) {
      return shapeA.boundingBoxes();
    }
    List<BoundingBox> merge = Lists.newArrayList(shapeA.boundingBoxes());
    merge.addAll(shapeB.boundingBoxes());
    return merge;
  }

  @Override
  public boolean isEmpty() {
    return shapeA.isEmpty() && shapeB.isEmpty();
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return shapeA.intersectsWith(boundingBox) || shapeB.intersectsWith(boundingBox);
  }
}
