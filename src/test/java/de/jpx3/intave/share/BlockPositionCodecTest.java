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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlockPositionCodecTest {
  @Test
  void streamCodecWritesSignedCoordinatesInXyzOrder() {
    BlockPosition expected = new BlockPosition(Integer.MIN_VALUE, 321, Integer.MAX_VALUE);
    ByteBuf buffer = Unpooled.buffer();
    try {
      BlockPosition.STREAM_CODEC.encode(buffer, expected);

      assertEquals(12, buffer.readableBytes());
      assertEquals(expected.getX(), buffer.readInt());
      assertEquals(expected.getY(), buffer.readInt());
      assertEquals(expected.getZ(), buffer.readInt());

      buffer.readerIndex(0);
      assertEquals(expected, BlockPosition.STREAM_CODEC.decode(buffer));
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }

  @Test
  void legacyStreamCodecReadsPackedCoordinates() {
    BlockPosition expected = new BlockPosition(-30_000_000, -2048, 30_000_000);
    ByteBuf buffer = Unpooled.buffer();
    try {
      buffer.writeLong(expected.toLong());

      assertEquals(expected, BlockPosition.LEGACY_STREAM_CODEC.decode(buffer));
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }
}
