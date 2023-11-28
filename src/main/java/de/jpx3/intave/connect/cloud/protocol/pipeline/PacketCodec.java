package de.jpx3.intave.connect.cloud.protocol.pipeline;

import de.jpx3.intave.connect.cloud.protocol.Direction;
import de.jpx3.intave.connect.cloud.protocol.Packet;
import de.jpx3.intave.connect.cloud.protocol.ProtocolSpecification;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public final class PacketCodec extends ByteToMessageCodec<Packet<?>> {
  private final ProtocolSpecification protocol;
  private final Direction receiving;
  private final Direction sending;

  public PacketCodec(ProtocolSpecification protocol, Direction receiving) {
    this.protocol = protocol;
    this.receiving = receiving;
    this.sending = receiving.opposite();
  }

  @Override
  protected void encode(ChannelHandlerContext channelHandlerContext, Packet<?> packet, ByteBuf byteBuf) {
    if (packet.direction() != sending) {
      throw new RuntimeException("Packet " + packet.name() + " is not " + sending.name().toLowerCase());
    }
    if (protocol.packetIdsKnownFor(sending)) {
      int id = protocol.packetId(sending, packet.name());
      if (id == -1) {
        System.out.println("Unknown id for " + packet.name());
        // do nothing
        return;
      }
      byteBuf.writeByte(id);
    } else {
      byteBuf.writeByte(-1);
      writeString(packet.name(), byteBuf);
    }
    packet.serialize(new ByteBufOutputStream(byteBuf));
    byteBuf.writeByte(-1);
  }

  @Override
  protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
    int id = byteBuf.readByte();
    Packet<?> packet;
    if (id == -1) {
      String name = readString(byteBuf);
      packet = protocol.packetFromName(receiving, name);
    } else {
      packet = protocol.packetFromId(receiving, id);
    }
    if (packet.direction() != receiving) {
      throw new RuntimeException("Packet " + packet.name() + " is not " + receiving.name().toLowerCase());
    }
    packet.deserialize(new ByteBufInputStream(byteBuf));
    list.add(packet);

    if (byteBuf.readByte() != -1) {
      throw new RuntimeException("Packet not fully read");
    }
  }

  private void writeString(String string, ByteBuf byteBuf) {
    byte[] bytes = string.getBytes();
    writeVarInt(byteBuf, bytes.length);
    byteBuf.writeBytes(bytes);
  }

  private String readString(ByteBuf byteBuf) {
    int length = readVarInt(byteBuf);
    byte[] bytes = new byte[length];
    byteBuf.readBytes(bytes);
    return new String(bytes);
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
