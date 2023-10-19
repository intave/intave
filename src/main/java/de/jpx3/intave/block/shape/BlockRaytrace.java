package de.jpx3.intave.block.shape;

import de.jpx3.intave.share.Direction;

public final class BlockRaytrace {
  private static final BlockRaytrace NONE = null;//new BlockRaytrace(null, Integer.MAX_VALUE);

  private final Direction direction;
  private final double lengthOffset;

  public BlockRaytrace(Direction direction, double lengthOffset) {
    this.direction = direction;
    this.lengthOffset = lengthOffset;
  }

  public BlockRaytrace minSelect(BlockRaytrace other) {
    return other == NONE || other == null ? this : other.lengthOffset < lengthOffset ? other : this;
  }

  public Direction direction() {
    return direction;
  }

  public double lengthOffset() {
    return lengthOffset;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    BlockRaytrace that = (BlockRaytrace) obj;
    if (Double.compare(that.lengthOffset, lengthOffset) != 0) return false;
    return direction == that.direction;
  }

  public static BlockRaytrace none() {
    return NONE;
  }

  public static BlockRaytrace from(Direction direction, double lengthOffset) {
    return new BlockRaytrace(direction, lengthOffset);
  }
}
