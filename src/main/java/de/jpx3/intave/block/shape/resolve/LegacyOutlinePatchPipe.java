package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Set;

public final class LegacyOutlinePatchPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;

  public LegacyOutlinePatchPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
  }

  @Override
  public BlockShape collisionShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    return forward.collisionShapeOf(world, user, type, variantIndex, posX, posY, posZ);
  }

  @Override
  public BlockShape outlineShapeOf(World world, User user, Material type, int variantIndex, int posX, int posY, int posZ) {
    BlockShape outputBox;
    if (affected(type)) {
      // use collision shape for outline
      outputBox = forward.collisionShapeOf(world, user, type, variantIndex, posX, posY, posZ);
    } else {
      outputBox = forward.outlineShapeOf(world, user, type, variantIndex, posX, posY, posZ);
    }

    double xMidpoint = (outputBox.min(Direction.Axis.X_AXIS) + outputBox.max(Direction.Axis.X_AXIS)) / 2;
    double yMidpoint = (outputBox.min(Direction.Axis.Y_AXIS) + outputBox.max(Direction.Axis.Y_AXIS)) / 2;
    double zMidpoint = (outputBox.min(Direction.Axis.Z_AXIS) + outputBox.max(Direction.Axis.Z_AXIS)) / 2;

    boolean assumeIncorrectlyNormalized =
      xMidpoint >= 0 && xMidpoint <= 1 &&
      yMidpoint >= 0 && yMidpoint <= 1 &&
      zMidpoint >= 0 && zMidpoint <= 1;

    if (assumeIncorrectlyNormalized) {
      if (outputBox instanceof BoundingBox) {
        ((BoundingBox) outputBox).makeOriginBox();
      }
      outputBox = outputBox.contextualized(posX, posY, posZ);
    }
    return outputBox;
  }

  private final Set<Material> affectedMaterials = MaterialSearch.materialsThatContain(
    "LADDER",
    "STAIRS",
    "THIN_GLASS",
    "STAINED_GLASS_PANE",
    "IRON_FENCE",
    "CAULDRON",
    "CHEST",
    "TRAPPED_CHEST"
  );

  private boolean affected(Material type) {
    return affectedMaterials.contains(type);
  }

  @Override
  public void downstreamTypeReset(Material type) {
    forward.downstreamTypeReset(type);
  }
}
