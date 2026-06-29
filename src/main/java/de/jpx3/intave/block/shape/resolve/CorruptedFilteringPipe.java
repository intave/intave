package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.patch.ApplyOnShapeBoundingBoxBuilder;
import de.jpx3.intave.block.shape.resolve.patch.BoundingBoxBuilder;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

final class CorruptedFilteringPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public CorruptedFilteringPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    List<BoundingBox> corrupted = resolveCorrupted(type, blockState);
    return corrupted != null ? BlockShapes.optimizedMerge(corrupted).contextualized(posX, posY, posZ) : forward.collisionShapeOf(world, player, type, blockState, posX, posY, posZ);
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
    return forward.outlineShapeOf(world, player, type, variantIndex, posX, posY, posZ);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }

  public List<BoundingBox> resolveCorrupted(Material type, int data) {
    if (type == BlockTypeAccess.END_PORTAL_FRAME) {
      ApplyOnShapeBoundingBoxBuilder builder = ApplyOnShapeBoundingBoxBuilder.create();
      builder.shapeX16AndApply(0.0D, 0.0D, 0.0D, 16.0D, 13.0D, 16.0D);

      // faulty, do not use data literally
      boolean hasEye = (data & 4) != 0;
      if (hasEye) {
        builder.shapeX16AndApply(4.0D, 13.0D, 4.0D, 12.0D, 16.0D, 12.0D);
        // 0.3125F, 0.8125F, 0.3125F, 0.6875F, 1.0F, 0.6875F
      }
      return builder.resolve();
    } else if (type == BlockTypeAccess.SKULL) {
      BoundingBoxBuilder builder = BoundingBoxBuilder.create();

      // faulty, do not use data literally
      switch (data & 7) {
        case 1:
          builder.shape(0.25F, 0.0F, 0.25F, 0.75F, 0.5F, 0.75F); // up
          break;
        case 2:
          builder.shape(0.25F, 0.25F, 0.5F, 0.75F, 0.75F, 1.0F); // north
          break;
        case 3:
          builder.shape(0.25F, 0.25F, 0.0F, 0.75F, 0.75F, 0.5F); // south
          break;
        case 4:
          builder.shape(0.5F, 0.25F, 0.25F, 1.0F, 0.75F, 0.75F); // west
          break;
        case 5:
          builder.shape(0.0F, 0.25F, 0.25F, 0.5F, 0.75F, 0.75F); // east
          break;
      }
    }
    return null;
  }
}
