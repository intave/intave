package de.jpx3.intave.share;

import de.jpx3.intave.codec.CodecTranslator;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class PositionMoveRotationConverter {
  public static final Class<?> NATIVE_CLASS = positionMoveRotationClass();
  private static final ThreadLocal<ByteBuf> caches = ThreadLocal.withInitial(FriendlyByteBuf::from256Unpooled);
  private static final StreamCodec<ByteBuf, ByteBuf, PositionMoveRotation> intaveCodec = PositionMoveRotation.STREAM_CODEC;
  private static final StreamCodec<ByteBuf, ByteBuf, Object> nativeCodec = (StreamCodec<ByteBuf, ByteBuf, Object>)
    CodecTranslator.translatedCodecOf(NATIVE_CLASS);

  private PositionMoveRotationConverter() {
  }

  public static Object toNative(PositionMoveRotation positionMoveRotation) {
    ByteBuf medium = caches.get();
    intaveCodec.encode(medium, positionMoveRotation);
    Object decode = nativeCodec.decode(medium);
    medium.clear();
    return decode;
  }

  public static PositionMoveRotation fromNative(Object nativePositionMoveRotation) {
    ByteBuf medium = caches.get();
    nativeCodec.encode(medium, nativePositionMoveRotation);
    PositionMoveRotation decode = intaveCodec.decode(medium);
    medium.clear();
    return decode;
  }

  private static Class<?> positionMoveRotationClass() {
    try {
      return Class.forName("net.minecraft.world.entity.PositionMoveRotation");
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException("Cannot find PositionMoveRotation class", exception);
    }
  }
}
