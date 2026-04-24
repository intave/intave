package de.jpx3.intave.share;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public final class FriendlyByteBuf {
  public static ByteBuf from256Unpooled() {
    return wrapping(Unpooled.buffer(256, 2048));
  }

  public static ByteBuf wrapping(ByteBuf byteBuf) {
    return byteBuf;
  }

  public static String readUtf(ByteBuf friendly, int maxLength) {
    int byteLength = readVarInt(friendly);
    if (byteLength < 0) {
      throw new IllegalStateException("Negative string length");
    }
    int maximumBytes = maxLength * 4;
    if (byteLength > maximumBytes) {
      throw new IllegalStateException("Encoded string is too long: " + byteLength + " > " + maximumBytes);
    }
    String value = friendly.toString(friendly.readerIndex(), byteLength, StandardCharsets.UTF_8);
    friendly.skipBytes(byteLength);
    if (value.length() > maxLength) {
      throw new IllegalStateException("Decoded string is too long: " + value.length() + " > " + maxLength);
    }
    return value;
  }

  public static void setup() {
  }

  private static int readVarInt(ByteBuf byteBuf) {
    int value = 0;
    int position = 0;
    byte currentByte;
    do {
      currentByte = byteBuf.readByte();
      value |= (currentByte & 0x7F) << position;
      position += 7;
      if (position >= 32) {
        throw new IllegalStateException("VarInt is too big");
      }
    } while ((currentByte & 0x80) == 0x80);
    return value;
  }
}
