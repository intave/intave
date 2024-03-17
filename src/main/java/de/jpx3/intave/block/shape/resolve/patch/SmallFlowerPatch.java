package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;

import static de.jpx3.intave.share.ClientMath.coordinateRandom;

public class SmallFlowerPatch extends BlockShapePatch {
  private static final BoundingBox LEAF = BoundingBox.originFromX16(5.0D, 0.0D, 5.0D, 11.0D, 10.0D, 11.0D);
  private static final BlockShape[][] CACHE = new BlockShape[16][16];

  @Override
  protected BlockShape outlinePatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    if (shape.isEmpty()) {
      return shape;
    }
    long randomCoordinate = coordinateRandom(posX, 0, posZ);
    int xOffsetKey = (int) (randomCoordinate & 15L);
    int zOffsetKey = (int) (randomCoordinate >> 8 & 15L);
    BlockShape box = CACHE[xOffsetKey][zOffsetKey];
    if (box == null) {
      double allowedOffset = 0.25D;
      double offsetX = MathHelper.minmax(-allowedOffset,((double) ((float) xOffsetKey / 15.0F) - 0.5D) * 0.5D, allowedOffset);
      double offsetZ = MathHelper.minmax(-allowedOffset,((double) ((float) zOffsetKey / 15.0F) - 0.5D) * 0.5D, allowedOffset);
      double offsetY = 0.0;
      box = CACHE[xOffsetKey][zOffsetKey] = LEAF.originOffset(offsetX, offsetY, offsetZ);
    }
    return box;
  }

  @Override
  protected boolean appliesTo(Material material) {
    return smallFlowers.contains(material);
  }

  private boolean dontTryAgain = false;
  private final Set<Material> smallFlowers = smallFlowers();

  private Set<Material> smallFlowers() {
    if (dontTryAgain) {
      return Collections.emptySet();
    }
    try {
      Class<?> myClass = Class.forName("org.bukkit.Tag");
      // noinspection JavaReflectionMemberAccess
      Object tag = myClass.getField("SMALL_FLOWERS").get(null);
      // noinspection unchecked
      return (Set<Material>) myClass.getMethod("getValues").invoke(tag);
    } catch (Exception e) {
      dontTryAgain = true;
      return Collections.emptySet();
    }
  }
}
