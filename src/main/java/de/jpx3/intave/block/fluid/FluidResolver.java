package de.jpx3.intave.block.fluid;

import org.bukkit.Material;

public interface FluidResolver {
  Fluid liquidFrom(Material type, int variantIndex);

  default Fluid select(
    boolean isWater,
    boolean isLava,
    boolean dry,
    boolean falling,
    float height,
    int level
  ) {
    if (dry) {
      return Dry.of();
    } else if (isWater) {
      return Water.of(height, level, falling);
    } else if (isLava) {
      return Lava.of(height, level, falling);
    }
    return Dry.of();
  }

  default float heightFromLegacyLevel(int level) {
    if (level >= 8) {
      level = 0;
    }
    return (float) (level + 1) / 9.0F;
  }
}
