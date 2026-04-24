package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import org.bukkit.Material;

final class ModernFluidResolver implements FluidResolver {
  @Override
  public Fluid liquidFrom(Material type, int variantIndex) {
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    boolean water = isWaterCarrier(type, variant);
    boolean lava = isLava(type);
    if (!water && !lava) {
      return Dry.of();
    }

    Integer blockLevel = variant.propertyOf("level");
    int level = fluidLevelFromBlockLevel(blockLevel);
    Boolean fallingProperty = variant.propertyOf("falling");
    boolean falling = fallingProperty != null ? fallingProperty : blockLevel != null && blockLevel >= 8;
    return select(water, lava, false, falling, level / 9.0F, level);
  }

  private boolean isWaterCarrier(Material type, BlockVariant variant) {
    if (isWater(type)) {
      return true;
    }
    Boolean waterlogged = variant.propertyOf("waterlogged");
    if (Boolean.TRUE.equals(waterlogged)) {
      return true;
    }
    String name = type.name();
    return "BUBBLE_COLUMN".equals(name)
      || "KELP".equals(name)
      || "KELP_PLANT".equals(name)
      || "SEAGRASS".equals(name)
      || "TALL_SEAGRASS".equals(name);
  }

  private boolean isWater(Material type) {
    String name = type.name();
    return "WATER".equals(name) || "STATIONARY_WATER".equals(name);
  }

  private boolean isLava(Material type) {
    String name = type.name();
    return "LAVA".equals(name) || "STATIONARY_LAVA".equals(name);
  }

  private int fluidLevelFromBlockLevel(Integer blockLevel) {
    if (blockLevel == null || blockLevel <= 0 || blockLevel >= 8) {
      return 8;
    }
    return 8 - blockLevel;
  }
}
