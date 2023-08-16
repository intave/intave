package de.jpx3.intave.module.cloud.protocol.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import javax.crypto.Cipher;
import java.util.List;

public final class Decryption extends ByteToMessageDecoder {
  private byte[] inputBuffer = new byte[1024];
  private final Cipher cipher;

  public Decryption(Cipher cipher) {
    this.cipher = cipher;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    int i = in.readableBytes();
    if (this.inputBuffer.length < i) {
      this.inputBuffer = new byte[i];
    }
    in.readBytes(this.inputBuffer, 0, i);
    int outputSize = this.cipher.getOutputSize(i);
    ByteBuf buffer = ctx.alloc().heapBuffer(outputSize);
    byte[] data = buffer.array();
    int offset = buffer.arrayOffset();
    buffer.writerIndex(this.cipher.update(this.inputBuffer, 0, i, data, offset));
    out.add(buffer);
  }
}
