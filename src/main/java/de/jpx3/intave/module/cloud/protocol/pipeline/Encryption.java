package de.jpx3.intave.module.cloud.protocol.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import javax.crypto.Cipher;

public final class Encryption extends MessageToByteEncoder<ByteBuf> {
  private byte[] inputBuffer = new byte[1024];
  private byte[] outputBuffer = new byte[1024];
  private final Cipher cipher;

  public Encryption(Cipher cipher) {
    this.cipher = cipher;
  }

  @Override
  protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf input, ByteBuf output) throws Exception {
    int i = input.readableBytes();
    if (this.inputBuffer.length < i) {
      this.inputBuffer = new byte[i];
    }
    input.readBytes(this.inputBuffer, 0, i);
    int outputSize = this.cipher.getOutputSize(i);
    if (this.outputBuffer.length < outputSize) {
      this.outputBuffer = new byte[outputSize];
    }
    output.writeBytes(this.outputBuffer, 0, this.cipher.update(this.inputBuffer, 0, i, this.outputBuffer));
  }
}
