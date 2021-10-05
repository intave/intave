package de.jpx3.intave.block.fluid;

public final class Fluid {
  private final static Fluid EMPTY = new Fluid(FluidTag.EMPTY, true, 0);

  private final FluidTag fluidTag;
  private final boolean empty;
  private final boolean source;
  private final float height;

  private Fluid(FluidTag fluidTag, boolean source, float height) {
    this.fluidTag = fluidTag;
    this.source = source;
    this.height = height;
    this.empty = fluidTag == FluidTag.EMPTY;
  }

  public FluidTag fluidTag() {
    return fluidTag;
  }

  public boolean isOf(FluidTag fluidTag) {
    return !empty && this.fluidTag == fluidTag;
  }

  public boolean isEmpty() {
    return empty;
  }

  public float height() {
    return height;
  }

  public boolean source() {
    return source;
  }

  public static Fluid empty() {
    return EMPTY;
  }

  public static Fluid construct(FluidTag fluidTag, boolean source, float height) {
    if (fluidTag == FluidTag.EMPTY) {
      return EMPTY;
    }
    return new Fluid(fluidTag, source, height);
  }
}