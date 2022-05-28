package de.jpx3.intave.block.shape;

import com.google.common.collect.Lists;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Direction;
import de.jpx3.intave.shade.Position;

import java.util.List;

final class MergeBlockShape implements BlockShape {
  private final BlockShape shapeA, shapeB;

  MergeBlockShape(BlockShape shapeA, BlockShape shapeB) {
    this.shapeA = shapeA;
    this.shapeB = shapeB;
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset) {
    return shapeA.allowedOffset(axis, entity, shapeB.allowedOffset(axis, entity, offset));
  }

  @Override
  public double min(Direction.Axis axis) {
    return Math.min(shapeA.min(axis), shapeB.min(axis));
  }

  @Override
  public double max(Direction.Axis axis) {
    return Math.max(shapeA.max(axis), shapeB.max(axis));
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      shapeA.contextualized(posX, posY, posZ),
      shapeB.contextualized(posX, posY, posZ)
    );
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      shapeA.normalized(posX, posY, posZ),
      shapeB.normalized(posX, posY, posZ)
    );
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    BlockRaytrace raytraceA = shapeA.raytrace(origin, target);
    BlockRaytrace raytraceB = shapeB.raytrace(origin, target);
    if (raytraceA == null) {
      return raytraceB;
    }
    if (raytraceB == null) {
      return raytraceA;
    }
    return raytraceA.minSelect(raytraceB);
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
  public boolean isCubic() {
    return (shapeA.isCubic() && shapeB.isEmpty())
      || (shapeB.isCubic() && shapeA.isEmpty());
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return shapeA.intersectsWith(boundingBox) || shapeB.intersectsWith(boundingBox);
  }

  @Override
  public String toString() {
    return "MergedShape{" +
      "shapeA=" + shapeA +
      ", shapeB=" + shapeB +
      '}';
  }
}
