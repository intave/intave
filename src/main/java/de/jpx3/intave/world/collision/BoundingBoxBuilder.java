package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public final class BoundingBoxBuilder {
  private final List<WrappedAxisAlignedBB> boundingBoxes = new ArrayList<>();
  protected double minX;
  protected double minY;
  protected double minZ;
  protected double maxX;
  protected double maxY;
  protected double maxZ;

  private BoundingBoxBuilder() {
  }

  public void shape(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.maxX = maxX;
    this.maxY = maxY;
    this.maxZ = maxZ;
  }

  public List<WrappedAxisAlignedBB> applyAndResolve() {
    apply();
    return resolve();
  }

  public void apply() {
    WrappedAxisAlignedBB boundingBox = new WrappedAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    boundingBox.setOriginBox(true);
    boundingBoxes.add(boundingBox);
  }

  public List<WrappedAxisAlignedBB> resolve() {
    return boundingBoxes;
  }

  @Override
  public String toString() {
    return "BoundingBoxBuilder{" +
      "boundingBoxes=" + boundingBoxes +
      ", minX=" + minX +
      ", minY=" + minY +
      ", minZ=" + minZ +
      ", maxX=" + maxX +
      ", maxY=" + maxY +
      ", maxZ=" + maxZ +
      '}';
  }

  public static BoundingBoxBuilder create() {
    return new BoundingBoxBuilder();
  }
}
