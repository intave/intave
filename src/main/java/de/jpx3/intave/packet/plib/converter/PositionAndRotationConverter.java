/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.plib.converter;

import com.comphenix.protocol.reflect.EquivalentConverter;
import de.jpx3.intave.codec.CodecTranslator;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.PositionAndRotation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class PositionAndRotationConverter implements EquivalentConverter<PositionAndRotation> {
  public static final PositionAndRotationConverter INSTANCE = new PositionAndRotationConverter();

  private PositionAndRotationConverter() {}

  public Class<?> nativeType() {
    return Native.TYPE;
  }

  @Override
  public Object getGeneric(PositionAndRotation specific) {
    ByteBuf buffer = Unpooled.buffer(32);
    try {
      PositionAndRotation.STREAM_CODEC.encode(buffer, specific);
      return Native.CODEC.decode(buffer);
    } finally {
      buffer.release();
    }
  }

  @Override
  public PositionAndRotation getSpecific(Object generic) {
    ByteBuf buffer = Unpooled.buffer(32);
    try {
      Native.CODEC.encode(buffer, generic);
      return PositionAndRotation.STREAM_CODEC.decode(buffer);
    } finally {
      buffer.release();
    }
  }

  @Override
  public Class<PositionAndRotation> getSpecificType() {
    return PositionAndRotation.class;
  }

  private static final class Native {
    private static final Class<?> TYPE = resolveType();
    @SuppressWarnings("unchecked")
    private static final StreamCodec<ByteBuf, ByteBuf, Object> CODEC =
      (StreamCodec<ByteBuf, ByteBuf, Object>) CodecTranslator.translatedCodecOf(TYPE);

    private static Class<?> resolveType() {
      try {
        return Class.forName("net.minecraft.core.PositionAndRotation");
      } catch (ClassNotFoundException exception) {
        throw new IllegalStateException("Native PositionAndRotation requires a compatible 26.3+ server", exception);
      }
    }
  }
}
