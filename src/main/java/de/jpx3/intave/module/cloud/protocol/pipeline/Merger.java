package de.jpx3.intave.module.cloud.protocol.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class Merger extends MessageToByteEncoder<ByteBuf> {
  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf input, ByteBuf output) throws Exception {
    int length = input.readableBytes();
    int varIntSize = varIntSizeOf(length);

    if (varIntSize > 3) {
      throw new IllegalArgumentException("Can't fit " + length + " into 3 bytes");
    }
    output.ensureWritable(varIntSize + length);
    writeVarInt(output, length);
    output.writeBytes(input, input.readerIndex(), length);
  }

  public static int varIntSizeOf(int input) {
    for (int i = 1; i < 5; ++i) {
      if ((input & -1 << i * 7) == 0) {
        return i;
      }
    }
    return 5;
  }

  private int readVarInt(ByteBuf in) {
    int i = 0;
    int bytePosition = 0;
    while (true) {
      int nextByte = in.readByte();
      i |= (nextByte & 0b1111111) << bytePosition++ * 7;
      if (bytePosition > 5) {
        throw new RuntimeException("VarInt too big");
      }
      if ((nextByte & 0b10000000) != 0b10000000) {
        break;
      }
    }
    return i;
  }

  private void writeVarInt(ByteBuf out, int paramInt) {
    while (true) {
      if ((paramInt & 0xFFFFFF80) == 0) {
        out.writeByte(paramInt);
        return;
      }
      out.writeByte(paramInt & 0x7F | 0x80);
      paramInt >>>= 7;
    }
  }
}
