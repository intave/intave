package de.jpx3.intave.block.shape.pipe.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.link.WrapperLinkage;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractShapeDrill implements ShapeResolverPipeline {
  protected BlockShape translate(List<?> bbs) {
    if (bbs.isEmpty()) {
      return BlockShapes.empty();
    }
    List<BoundingBox> list = new ArrayList<>();
    for (Object bb : bbs) {
      list.add(WrapperLinkage.boundingBoxOf(bb));
    }
    return BlockShapes.ofBoxes(list);
  }

  protected BlockShape translateWithOffset(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return BlockShapes.empty();
    }
    List<BoundingBox> list = new ArrayList<>();
    for (Object bb : bbs) {
      list.add(BoundingBox.fromNative(bb).offset(posX, posY, posZ));
    }
    return BlockShapes.ofBoxes(list);
  }
}
