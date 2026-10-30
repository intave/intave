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

package de.jpx3.intave.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

final class ByteBufStreamCodecsTest {
  @Test
  void varIntByteArraysUseTheirOwnLengthAndRejectInvalidLengths() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      ByteBufStreamCodecs.VARINT_BYTES.encode(buffer, new byte[128]);
      assertEquals(0x80, buffer.readUnsignedByte());
      assertEquals(1, buffer.readUnsignedByte());
      buffer.readerIndex(0);
      buffer.writeByte(0x55);
      assertArrayEquals(new byte[128], ByteBufStreamCodecs.VARINT_BYTES.decode(buffer));
      assertEquals(0x55, buffer.readUnsignedByte());
      ByteBufStreamCodecs.VARINT.encode(buffer.clear(), -1);
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.VARINT_BYTES.decode(buffer));
      buffer.clear().writeByte(2).writeByte(0);
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.VARINT_BYTES.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void fixedBitSetsPadInLittleEndianOrderAndRejectBitsOutsideTheirWidth() {
    StreamCodec<ByteBuf, ByteBuf, BitSet> codec = ByteBufStreamCodecs.fixedBitSet(20);
    ByteBuf buffer = Unpooled.buffer();
    try {
      BitSet value = new BitSet();
      value.set(0);
      value.set(19);
      codec.encode(buffer, value);
      assertEquals(1, buffer.readUnsignedByte());
      assertEquals(0, buffer.readUnsignedByte());
      assertEquals(8, buffer.readUnsignedByte());
      buffer.readerIndex(0);
      assertEquals(value, codec.decode(buffer));
      value.set(20);
      assertThrows(IllegalArgumentException.class, () -> codec.encode(buffer.clear(), value));
      buffer.clear().writeByte(0).writeByte(0).writeByte(16);
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      buffer.clear().writeZero(2);
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.fixedBitSet(-1));
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.fixedByteArray(-1));
    } finally {
      buffer.release();
    }
  }

  @Test
  void publicVarIntCodecHandlesSignedBoundariesAndRejectsIncompleteValues() {
    int[] values = {0, 127, 128, Integer.MAX_VALUE, Integer.MIN_VALUE, -1};
    int[] lengths = {1, 1, 2, 5, 5, 5};
    ByteBuf buffer = Unpooled.buffer();
    try {
      for (int i = 0; i < values.length; i++) {
        buffer.clear();
        ByteBufStreamCodecs.VARINT.encode(buffer, values[i]);
        assertEquals(lengths[i], buffer.readableBytes());
        assertEquals(values[i], ByteBufStreamCodecs.VARINT.decode(buffer).intValue());
        assertFalse(buffer.isReadable());
      }
      buffer.clear().writeByte(0x80);
      assertThrows(IndexOutOfBoundsException.class, () -> ByteBufStreamCodecs.VARINT.decode(buffer));
      buffer.clear();
      for (int i = 0; i < 6; i++) buffer.writeByte(0x80);
      assertThrows(RuntimeException.class, () -> ByteBufStreamCodecs.VARINT.decode(buffer));
    } finally {
      buffer.release();
    }
  }

  @Test
  void packetStringsUseVarIntUtf8WithoutChangingTheExistingStringFormat() {
    ByteBuf buffer = Unpooled.buffer();
    try {
      ByteBufStreamCodecs.STRING.encode(buffer, "abc");
      assertEquals(3, buffer.readInt());
      assertEquals(3, buffer.readableBytes());
      buffer.readerIndex(0);
      assertEquals("abc", ByteBufStreamCodecs.STRING.decode(buffer));

      buffer.clear();
      StreamCodec<ByteBuf, ByteBuf, String> codec = ByteBufStreamCodecs.stringCodec(2);
      codec.encode(buffer, "\u00e4\u20ac");
      assertEquals(5, buffer.readUnsignedByte());
      assertEquals(5, buffer.readableBytes());
      buffer.readerIndex(0);
      assertEquals("\u00e4\u20ac", codec.decode(buffer));
      assertFalse(buffer.isReadable());
    } finally {
      buffer.release();
    }
  }

  @Test
  void packetStringsEnforceByteAndCharacterBounds() {
    StreamCodec<ByteBuf, ByteBuf, String> codec = ByteBufStreamCodecs.stringCodec(2);
    ByteBuf buffer = Unpooled.buffer();
    try {
      assertThrows(IllegalArgumentException.class, () -> codec.encode(buffer, "abc"));
      buffer.writeByte(3).writeByte('a').writeByte('b').writeByte('c');
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      buffer.clear().writeByte(7);
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      buffer.clear().writeByte(2).writeByte('a');
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      buffer.clear();
      ByteBufStreamCodecs.VARINT.encode(buffer, -1);
      assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.stringCodec(-1));
      assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.stringCodec(Integer.MAX_VALUE));
    } finally {
      buffer.release();
    }
  }
}
