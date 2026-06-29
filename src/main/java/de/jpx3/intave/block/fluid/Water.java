package de.jpx3.intave.block.fluid;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

final class Water implements Fluid {
  private final float height;
  private final int level;
  private final boolean falling;

  public static final StreamCodec<ByteBuf, ByteBuf, Water> STREAM_CODEC = StreamCodec.compound(
    ByteBufStreamCodecs.FLOAT, Water::height,
    ByteBufStreamCodecs.INTEGER, Water::level,
    ByteBufStreamCodecs.BOOLEAN, Water::falling,
    Water::of
  );

  private Water(float height, int level, boolean falling) {
    this.height = height;
    this.level = level;
    this.falling = falling;
  }

  @Override
  public boolean isDry() {
    return false;
  }

  @Override
  public boolean isOfWater() {
    return true;
  }

  @Override
  public boolean isOfLava() {
    return false;
  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public int level() {
    return level;
  }

  @Override
  public boolean falling() {
    return falling;
  }

  @Override
  public boolean isSource() {
    return false;
  }

  @Override
  public String toString() {
    return "Water{" +
      "height=" + height +
      ", falling=" + falling +
      '}';
  }

  public static Water of(float height, int level, boolean falling) {
    return new Water(height, level, falling);
  }
}
