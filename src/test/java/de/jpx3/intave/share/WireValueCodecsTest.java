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

package de.jpx3.intave.share;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

final class WireValueCodecsTest {
  @Test
  void byteAnglesRoundTripEveryBitPattern() {
    ByteBuf input = Unpooled.buffer();
    ByteBuf output = Unpooled.buffer();
    try {
      for (int angle = 0; angle < 256; angle++) {
        input.clear().writeByte(angle).writeByte(255 - angle);
        Rotation rotation = Rotation.BYTE_STREAM_CODEC.decode(input);
        Rotation.BYTE_STREAM_CODEC.encode(output.clear(), rotation);
        assertEquals(angle, output.readUnsignedByte());
        assertEquals(255 - angle, output.readUnsignedByte());
      }
    } finally {
      input.release();
      output.release();
    }
  }

  @Test
  void fixedPointPositionsFloorNegativeCoordinatesAndKeepSignedExtremes() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      Position value = new Position(Integer.MIN_VALUE / 32.0, Integer.MAX_VALUE / 32.0, -1 / 32.0);
      Position.FIXED_POINT_STREAM_CODEC.encode(buffer, value);
      assertEquals(Integer.MIN_VALUE, buffer.readInt());
      assertEquals(Integer.MAX_VALUE, buffer.readInt());
      assertEquals(-1, buffer.readInt());
      buffer.readerIndex(0);
      assertEquals(value, Position.FIXED_POINT_STREAM_CODEC.decode(buffer));
      Position.FIXED_POINT_STREAM_CODEC.encode(buffer.clear(), new Position(-0.01, 0.01, 1.5));
      assertEquals(-1, buffer.readInt());
      assertEquals(0, buffer.readInt());
      assertEquals(48, buffer.readInt());
    } finally {
      buffer.release();
    }
  }

  @Test
  void packedPositionsKeepBothCoordinateOrdersAndSignedExtremes() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      BlockPosition value = new BlockPosition(1, 2, 3);
      BlockPosition.WIRE_STREAM_CODEC.encode(buffer, value);
      assertEquals((1L << 38) | (3L << 12) | 2, buffer.readLong());
      BlockPosition.LEGACY_STREAM_CODEC.encode(buffer, value);
      assertEquals((1L << 38) | (2L << 26) | 3, buffer.readLong());

      value = new BlockPosition(-33554432, -2048, 33554431);
      BlockPosition.WIRE_STREAM_CODEC.encode(buffer, value);
      assertEquals(value, BlockPosition.WIRE_STREAM_CODEC.decode(buffer));
      BlockPosition.LEGACY_STREAM_CODEC.encode(buffer, value);
      assertEquals(value, BlockPosition.LEGACY_STREAM_CODEC.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void shortDeltaCodecKeepsSignedRangeAndRejectsUnrepresentableValues() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      RawVector3d value = new RawVector3d(-8, 32767 / 4096.0, 0.25);
      RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(buffer, value);
      assertEquals(Short.MIN_VALUE, buffer.readShort());
      assertEquals(Short.MAX_VALUE, buffer.readShort());
      assertEquals(1024, buffer.readShort());
      buffer.readerIndex(0);
      RawVector3d decoded = RawVector3d.SHORT_DELTA_STREAM_CODEC.decode(buffer);
      assertEquals(value.x(), decoded.x());
      assertEquals(value.y(), decoded.y());
      assertEquals(value.z(), decoded.z());
      assertThrows(IllegalArgumentException.class,
        () -> RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(buffer, new RawVector3d(8, 0, 0)));
      assertThrows(IllegalArgumentException.class,
        () -> RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(buffer, new RawVector3d(Double.NaN, 0, 0)));
    } finally {
      buffer.release();
    }
  }

  @Test
  void keyWireCodecDefaultsTheNamespaceAndKeepsTheStoredFormatSeparate() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      ByteBufStreamCodecs.VARINT_STRING.encode(buffer, "test");
      MinecraftKey key = MinecraftKey.STREAM_CODEC.decode(buffer);
      assertEquals("minecraft:test", key.fullKey());
      MinecraftKey.STREAM_CODEC.encode(buffer, key);
      assertEquals("minecraft:test", ByteBufStreamCodecs.VARINT_STRING.decode(buffer));

      MinecraftKey.INTERNAL_STREAM_CODEC.encode(buffer, key);
      assertEquals("minecraft", ByteBufStreamCodecs.STRING.decode(buffer));
      assertEquals("test", ByteBufStreamCodecs.STRING.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void relativeFlagCodecsKeepTheirWidthsAndLegacyRestriction() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      EnumSet<Relative> legacy = EnumSet.of(Relative.X, Relative.Y_ROT);
      Relative.LEGACY_FLAGS_STREAM_CODEC.encode(buffer, legacy);
      assertEquals(9, buffer.readUnsignedByte());
      buffer.readerIndex(0);
      assertEquals(legacy, Relative.LEGACY_FLAGS_STREAM_CODEC.decode(buffer));
      assertThrows(IllegalArgumentException.class,
        () -> Relative.LEGACY_FLAGS_STREAM_CODEC.encode(buffer, EnumSet.of(Relative.DELTA_X)));

      buffer.clear();
      EnumSet<Relative> modern = EnumSet.of(Relative.Z, Relative.DELTA_Y);
      Relative.FLAGS_STREAM_CODEC.encode(buffer, modern);
      assertEquals(68, buffer.readInt());
      buffer.readerIndex(0);
      assertEquals(modern, Relative.FLAGS_STREAM_CODEC.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }
}
