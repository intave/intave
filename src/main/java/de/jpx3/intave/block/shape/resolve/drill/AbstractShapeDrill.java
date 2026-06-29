package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.link.WrapperConverter;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractShapeDrill implements ShapeResolverPipeline {
  protected BlockShape translate(List<?> bbs) {
    if (bbs.isEmpty()) {
      return BlockShapes.emptyShape();
    }
    List<BoundingBox> list = new ArrayList<>(bbs.size());
    for (Object bb : bbs) {
      list.add(WrapperConverter.boundingBoxFromAABB(bb));
    }
	  return BlockShapes.optimizedMerge(list);
  }

  protected BlockShape translateWithOffset(List<?> bbs, int posX, int posY, int posZ) {
    if (bbs.isEmpty()) {
      return BlockShapes.emptyShape();
    }
    List<BoundingBox> list = new ArrayList<>();
    for (Object bb : bbs) {
      BoundingBox boundingBox = BoundingBox.fromNative(bb);
      boundingBox.makeOriginBox();
      list.add(boundingBox);
    }
    return BlockShapes.optimizedMerge(list).contextualized(posX, posY, posZ);
  }
}
