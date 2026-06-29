package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class BoundingBoxBuilder {
  private final List<BoundingBox> boundingBoxes = new ArrayList<>(1);
  private double minX;
  private double minY;
  private double minZ;
  private double maxX;
  private double maxY;
  private double maxZ;

  private BoundingBoxBuilder() {
  }

  public void cubeShape() {
    shape(0, 0, 0, 1, 1, 1);
  }

  public void shape(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.maxX = maxX;
    this.maxY = maxY;
    this.maxZ = maxZ;
  }

  public void shape(BoundingBox bb) {
    this.minX = bb.minX;
    this.minY = bb.minY;
    this.minZ = bb.minZ;
    this.maxX = bb.maxX;
    this.maxY = bb.maxY;
    this.maxZ = bb.maxZ;
  }

  public List<BoundingBox> applyAndResolve() {
    apply();
    return resolve();
  }

  public BlockShape applyAndResolveAsShape() {
    return BlockShapes.optimizedMerge(applyAndResolve());
  }

  public void apply() {
    BoundingBox boundingBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    boundingBox.makeOriginBox();
    boundingBoxes.add(boundingBox);
  }

  public List<BoundingBox> resolve() {
    return boundingBoxes;
  }


  public static BoundingBoxBuilder create() {
    return new BoundingBoxBuilder();
  }
}
