package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.CustomPacketPayloadWrapper;
import com.google.common.base.Charsets;
import de.jpx3.intave.klass.Lookup;
import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

public final class PayloadInReader extends AbstractPacketReader {
  public String tag() {
    String tag;
    if (packet().getStrings().getValues().isEmpty()) {
      Object minecraftKey = null;
      if (packet().getMinecraftKeys().getValues().isEmpty()) {
        for (CustomPacketPayloadWrapper value : packet().getCustomPacketPayloads().getValues()) {
          minecraftKey = value.getId();
          if (minecraftKey != null) {
            break;
          }
        }
        if (minecraftKey == null) {
          return "error";
        }
      } else {
        minecraftKey = packet().getMinecraftKeys().getValues().get(0);
      }
      try {
        tag = (String) minecraftKey.getClass().getMethod("toString").invoke(minecraftKey);
      } catch (Exception exception) {
        exception.printStackTrace();
        tag = "error";
      }
    } else {
      tag = packet().getStrings().getValues().get(0);
    }
    if (tag.startsWith("minecraft:")) {
      tag = tag.substring(10);
    }
    return tag;
  }

  public ByteBuf readBytes() {
    return (ByteBuf) packet().getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
  }

  public String readStringNormal() {
    Object packetDataSerializer = packet().getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
    try {
      return (String) packetDataSerializer.getClass().getMethod("toString", Charset.class).invoke(packetDataSerializer, Charset.defaultCharset());
    } catch (Exception exception) {
      return "error";
    }
  }

  public String readStringWithExtraByte() {
    ByteBuf bytes = (ByteBuf) packet().getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
    try {
      bytes.markReaderIndex();
      int length = bytes.readByte();
      return bytes.toString(Charsets.UTF_8);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return "";
  }
}
