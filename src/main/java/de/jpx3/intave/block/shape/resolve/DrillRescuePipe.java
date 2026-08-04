package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class DrillRescuePipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline next;
  private final Set<Material> reportedFailures = ConcurrentHashMap.newKeySet();

  public DrillRescuePipe(ShapeResolverPipeline next) {
    this.next = next;
  }

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
    try {
      return next.collisionShapeOf(world, player, type, variantIndex, posX, posY, posZ);
    } catch (Exception exception) {
      // we catch irregularities here elsewhere
      return rescueShape(type, posX, posY, posZ, exception);
    }
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
    try {
      return next.outlineShapeOf(world, player, type, variantIndex, posX, posY, posZ);
    } catch (Exception exception) {
      // we catch irregularities here elsewhere
      return rescueShape(type, posX, posY, posZ, exception);
    }
  }

  /**
   * Answers a single failed lookup without letting anything downstream remember it.
   * A block we cannot read the shape of is reported once per type: it means every
   * collision, pose and ray trace involving that block is a guess, which shows up as
   * movement and interaction false positives, so it should not stay silent.
   */
  private BlockShape rescueShape(Material type, int posX, int posY, int posZ, Exception exception) {
    if (reportedFailures.add(type)) {
      IntaveLogger.logger().warn("Could not resolve the shape of " + type
        + " (" + exception + ") - collisions and ray traces involving it are approximated");
      if (IntaveLogger.traceEnabled()) {
        IntaveLogger.logger().exception(exception);
      }
    }
    return BoundingBox
      // anything but a full or empty box, or it will be remembered
      .originFrom(0.25, 0.25, 0.25, 0.75, 0.75, 0.75)
      .contextualized(posX, posY, posZ);
  }
}
