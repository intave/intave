package de.jpx3.intave.world.blockshape.boxresolver.patcher;

import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public final class ApplyOnShapeBoundingBoxBuilder {
  private final List<WrappedAxisAlignedBB> boundingBoxes;

  private ApplyOnShapeBoundingBoxBuilder() {
    this.boundingBoxes = new ArrayList<>();
  }
  
  public void shapeAndApply(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.fromBounds(minX, minY, minZ, maxX, maxY, maxZ);
    boundingBox.makeOriginBox();
    boundingBoxes.add(boundingBox);
  }

  public void shapeX16AndApply(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.fromBounds(minX / 16.0, minY / 16.0, minZ / 16.0, maxX / 16.0, maxY / 16.0, maxZ / 16.0);
    boundingBox.makeOriginBox();
    boundingBoxes.add(boundingBox);
  }

  public List<WrappedAxisAlignedBB> resolve() {
    return boundingBoxes;
  }

  public static ApplyOnShapeBoundingBoxBuilder create() {
    return new ApplyOnShapeBoundingBoxBuilder();
  }
}