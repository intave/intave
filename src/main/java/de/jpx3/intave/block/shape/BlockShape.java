package de.jpx3.intave.block.shape;

import de.jpx3.intave.shade.BoundingBox;

import java.util.List;

// is more a BlockCollider than a BlockShape, but BlockShape definitely sounds cooler
public interface BlockShape {
  double allowedXOffset(BoundingBox entity, double offsetX);
  double allowedYOffset(BoundingBox entity, double offsetY);
  double allowedZOffset(BoundingBox entity, double offsetZ);
  boolean intersectsWith(BoundingBox boundingBox);

  BlockShape contextualized(int posX, int posY, int posZ);
  BlockShape normalized(int posX, int posY, int posZ);

  @Deprecated
  List<BoundingBox> boundingBoxes();
  boolean isEmpty();
}
