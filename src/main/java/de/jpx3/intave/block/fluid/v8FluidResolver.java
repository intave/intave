package de.jpx3.intave.block.fluid;

import org.bukkit.Material;

final class v8FluidResolver implements FluidResolver {
  private static final Material STATIONARY_WATER = Material.getMaterial("STATIONARY_WATER");
  private static final Material STATIONARY_LAVA = Material.getMaterial("STATIONARY_LAVA");

  @Override
  public Fluid liquidFrom(Material type, int variantIndex) {
    boolean isWater = type == Material.WATER || type == STATIONARY_WATER;
    boolean isLava = type == Material.LAVA || type == STATIONARY_LAVA;
    if (!isWater && !isLava) {
      return Dry.of();
    }
    float height = 1 - heightFromLegacyLevel(variantIndex);
    boolean falling = variantIndex >= 8;
    if (isWater) {
      return Water.of(height, falling ? 0 : variantIndex, falling);
    } else {
      return Lava.of(height, falling ? 0 : variantIndex, falling);
    }
  }
}
