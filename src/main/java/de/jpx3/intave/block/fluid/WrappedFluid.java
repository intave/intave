package de.jpx3.intave.block.fluid;

public final class WrappedFluid {
  private final static WrappedFluid EMPTY = WrappedFluid.construct(FluidTag.EMPTY, 0);

  private final FluidTag fluidTag;
  private final boolean empty;
  private final float height;

  private WrappedFluid(FluidTag fluidTag, float height) {
    this.fluidTag = fluidTag;
    this.height = height;
    this.empty = fluidTag == FluidTag.EMPTY;
  }

  public FluidTag fluidTag() {
    return fluidTag;
  }

  public boolean isIn(FluidTag fluidTag) {
    return !empty && this.fluidTag == fluidTag;
  }

  public boolean isEmpty() {
    return empty;
  }

  public float height() {
    return height;
  }

  public static WrappedFluid empty() {
    return EMPTY;
  }

  public static WrappedFluid construct(FluidTag fluidTag, float height) {
    return new WrappedFluid(fluidTag, height);
  }
}