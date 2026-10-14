package de.jpx3.intave.block.shape.resolve.drill;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractShapeDrill implements ShapeResolverPipeline {
  @Override
  public abstract BlockShape collisionShapeOf(
    World world, User user, Material type, int variantIndex,
    int posX, int posY, int posZ
  );

  @Override
  public abstract BlockShape outlineShapeOf(
    World world, User user, Material type, int variantIndex,
    int posX, int posY, int posZ
  );

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
