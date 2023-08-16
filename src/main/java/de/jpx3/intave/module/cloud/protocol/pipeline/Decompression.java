package de.jpx3.intave.module.cloud.protocol.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.zip.Inflater;

public final class Decompression extends ByteToMessageDecoder {
  private final Inflater inflater;
  private final int threshold;

  public Decompression(int threshold) {
    this.threshold = threshold;
    this.inflater = new Inflater();
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    if (in.readableBytes() != 0) {
      int compressedBytes = readVarInt(in);
      if (compressedBytes == 0) {
        out.add(in.readBytes(in.readableBytes()));
      } else {
        if (compressedBytes < threshold) {
          throw new RuntimeException("Invalid packet compression - size of " + compressedBytes + " is below threshold of " + threshold);
        }
        if (compressedBytes > 1024 * 1024 * 50) {
          throw new RuntimeException("Invalid packet compression - size of " + compressedBytes + " is larger than protocol maximum of 50MB");
        }
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        inflater.setInput(bytes);
        byte[] decompressedBytes = new byte[compressedBytes];
        inflater.inflate(decompressedBytes);
        out.add(Unpooled.wrappedBuffer(decompressedBytes));
        inflater.reset();
      }
    }
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
}
