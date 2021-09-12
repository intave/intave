package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.BoundingBox;

import java.util.Collections;
import java.util.List;

final class EmptyBlockShape implements BlockShape {
  @Override
  public double allowedXOffset(BoundingBox entity, double offsetX) {
    return offsetX;
  }

  @Override
  public double allowedYOffset(BoundingBox entity, double offsetY) {
    return offsetY;
  }

  @Override
  public double allowedZOffset(BoundingBox entity, double offsetZ) {
    return offsetZ;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return this;
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return this;
  }

  @Override
  public List<BoundingBox> boundingBoxes() {
    return Collections.emptyList();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return false;
  }
}
