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
import de.jpx3.intave.share.PositionMoveRotation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class PosMoveRotConverter implements EquivalentConverter<PositionMoveRotation> {
  public static final PosMoveRotConverter INSTANCE = new PosMoveRotConverter();
  private static final ThreadLocal<ByteBuf> caches = ThreadLocal.withInitial(Unpooled::buffer);
  public static final Class<?> nativePositionMoveRotClass = positionMoveRotationClass();
  private static final StreamCodec<ByteBuf, ByteBuf, PositionMoveRotation> intaveCodec = PositionMoveRotation.STREAM_CODEC;
  private static final StreamCodec<ByteBuf, ByteBuf, Object> nativeCodec = (StreamCodec<ByteBuf, ByteBuf, Object>)
    CodecTranslator.translatedCodecOf(nativePositionMoveRotClass);

  private PosMoveRotConverter() {}

  @Override
  public Object getGeneric(PositionMoveRotation specific) {
    if (specific == null) {
      return null;
    }
    ByteBuf medium = caches.get();
    intaveCodec.encode(medium, specific);
    Object decode = nativeCodec.decode(medium);
    medium.clear();
    return decode;
  }

  @Override
  public PositionMoveRotation getSpecific(Object generic) {
    if (generic == null) {
      return null;
    }
    ByteBuf medium = caches.get();
    nativeCodec.encode(medium, generic);
    PositionMoveRotation decode = intaveCodec.decode(medium);
    medium.clear();
    return decode;
  }

  private static Class<?> positionMoveRotationClass() {
    try {
      return Class.forName("net.minecraft.world.entity.PositionMoveRotation");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot find PositionMoveRotation class", e);
    }
  }

  @Override
  public Class<PositionMoveRotation> getSpecificType() {
    return PositionMoveRotation.class;
  }
}
