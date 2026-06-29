package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class ApplyOnShapeBoundingBoxBuilder {
  private final List<BoundingBox> boundingBoxes;

  private ApplyOnShapeBoundingBoxBuilder() {
    this.boundingBoxes = new ArrayList<>();
  }

  public void shapeAndApply(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    BoundingBox boundingBox = BoundingBox.fromBounds(minX, minY, minZ, maxX, maxY, maxZ);
    boundingBox.makeOriginBox();
    boundingBoxes.add(boundingBox);
  }

  public void shapeX16AndApply(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    BoundingBox boundingBox = BoundingBox.fromBounds(minX / 16.0, minY / 16.0, minZ / 16.0, maxX / 16.0, maxY / 16.0, maxZ / 16.0);
    boundingBox.makeOriginBox();
    boundingBoxes.add(boundingBox);
  }

  public List<BoundingBox> resolve() {
    return boundingBoxes;
  }

  public BlockShape resolveAsShape() {
    return BlockShapes.optimizedMerge(boundingBoxes);
  }

  public static ApplyOnShapeBoundingBoxBuilder create() {
    return new ApplyOnShapeBoundingBoxBuilder();
  }
}