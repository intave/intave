package de.jpx3.intave.block.tick;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;

public final class ShulkerBox {
  private boolean opening;
  private final Direction direction;
  private int ticks;

  private ShulkerBox(boolean opening, Direction direction) {
    this.ticks = opening ? 0 : 10;
    this.opening = opening;
    this.direction = direction;
  }

  public void open() {
    opening = true;
  }

  public void close() {
    opening = false;
  }

  public boolean shouldTick() {
    if (opening) {
      return ticks < 10;
    } else {
      return ticks > 0;
    }
  }

  public boolean complete() {
    return !opening && ticks <= 0;
  }

  public void tick() {
    if (opening) {
      ticks++;
    } else {
      ticks--;
    }
  }

  private static final int INTRINSIC_OFFSET = 3;
  private static final BoundingBox FULL_BLOCK = BoundingBox.originFrom(0, 0, 0, 1, 1, 1);
  private static final BlockShape[][] CACHE = new BoundingBox[Direction.values().length][10 + INTRINSIC_OFFSET * 2];

  public BlockShape originShape() {
    if (ticks > 10 || ticks < 0) {
      return FULL_BLOCK;
    }
    int directionId = direction.ordinal();
    BlockShape cached = CACHE[directionId][ticks + INTRINSIC_OFFSET];
    if (cached == null) {
      double progress = progress();
      return CACHE[directionId][ticks + INTRINSIC_OFFSET] = FULL_BLOCK.expand(
        0.5 * progress * direction.offsetX(),
        0.5 * progress * direction.offsetY(),
        0.5 * progress * direction.offsetZ()
      );
    }
    return cached;
  }

  public double progress() {
    return ticks / 10.0;
  }

  public static ShulkerBox opening(Direction direction) {
    return new ShulkerBox(true, direction);
  }

  public static ShulkerBox closing(Direction direction) {
    return new ShulkerBox(false, direction);
  }
}
