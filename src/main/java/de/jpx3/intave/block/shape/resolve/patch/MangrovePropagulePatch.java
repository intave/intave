package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.ClientMath.coordinateRandom;

public class MangrovePropagulePatch extends BlockShapePatch {
  private static final BoundingBox[] SHAPE_PER_AGE = new BoundingBox[]{
    BoundingBox.originFromX16(7.0D, 13.0D, 7.0D, 9.0D, 16.0D, 9.0D),
    BoundingBox.originFromX16(7.0D, 10.0D, 7.0D, 9.0D, 16.0D, 9.0D),
    BoundingBox.originFromX16(7.0D, 7.0D, 7.0D, 9.0D, 16.0D, 9.0D),
    BoundingBox.originFromX16(7.0D, 3.0D, 7.0D, 9.0D, 16.0D, 9.0D),
    BoundingBox.originFromX16(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D)
  };

  private static final BlockShape[][][] CACHE = new BlockShape[SHAPE_PER_AGE.length][16][16];

  @Override
  protected BlockShape outlinePatch(World world, Player player, int posX, int posY, int posZ, Material type, int variantIndex, BlockShape shape) {
    if (shape.isEmpty()) {
      return shape;
    }
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    boolean hanging = variant.propertyOf("hanging");
    int age = hanging ? variant.propertyOf("age") : 4;
    long randomCoordinate = coordinateRandom(posX, 0, posZ);
    int xOffsetKey = (int) (randomCoordinate & 15L);
    int zOffsetKey = (int) (randomCoordinate >> 8 & 15L);
    BlockShape box = CACHE[age][xOffsetKey][zOffsetKey];
    if (box == null) {
      double allowedOffset = 0.25D;
      double offsetX = MathHelper.minmax(-allowedOffset,((double) ((float) xOffsetKey / 15.0F) - 0.5D) * 0.5D, allowedOffset);
      double offsetZ = MathHelper.minmax(-allowedOffset,((double) ((float) zOffsetKey / 15.0F) - 0.5D) * 0.5D, allowedOffset);
      double offsetY = 0.0;
      box = CACHE[age][xOffsetKey][zOffsetKey] = SHAPE_PER_AGE[age].originOffset(offsetX, offsetY, offsetZ);
    }
    return box;
  }

  @Override
  protected boolean appliesTo(Material material) {
    return material.name().contains("MANGROVE_PROPAGULE");
  }
}
