package de.jpx3.intave.block.fluid;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

final class Lava implements Fluid {
  private final float height;
  private final int heightIndex;
  private final boolean falling;

  public static final StreamCodec<ByteBuf, ByteBuf, Lava> STREAM_CODEC = StreamCodec.compound(
    ByteBufStreamCodecs.FLOAT, Lava::height,
    ByteBufStreamCodecs.INTEGER, Lava::level,
    ByteBufStreamCodecs.BOOLEAN, Lava::falling,
    Lava::of
  );

  private Lava(float height, int heightIndex, boolean falling) {
    this.height = height;
    this.heightIndex = heightIndex;
    this.falling = falling;
  }

  @Override
  public boolean isDry() {
    return false;
  }

  @Override
  public boolean isOfWater() {
    return false;
  }

  @Override
  public boolean isOfLava() {
    return true;
  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public int level() {
    return heightIndex;
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
    return "Lava{" +
      "height=" + height +
      ", falling=" + falling +
      '}';
  }

  public static Lava of(float height, int level, boolean falling) {
    return new Lava(height, level, falling);
  }
}
