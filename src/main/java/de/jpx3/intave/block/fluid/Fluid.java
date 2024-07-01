package de.jpx3.intave.block.fluid;

public interface Fluid {
  boolean isDry();
  boolean isOfWater();
  boolean isOfLava();
  float height();
  int level();

  boolean falling();
  boolean isSource();

  default boolean affectsFlow(Fluid other) {
    return other.isOfWater() || other.similarTo(this);
  }

  default boolean similarTo(Fluid other) {
    return isOfWater() == other.isOfWater() && isOfLava() == other.isOfLava();
  }
}
