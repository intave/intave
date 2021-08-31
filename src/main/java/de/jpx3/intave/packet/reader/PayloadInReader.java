package de.jpx3.intave.packet.reader;

import com.google.common.base.Charsets;
import de.jpx3.intave.reflect.Lookup;
import io.netty.buffer.ByteBuf;

public final class PayloadInReader extends AbstractPacketReader {
  public String tag() {
    String tag;
    if (packet.getStrings().getValues().isEmpty()) {
      Object minecraftKey = packet.getMinecraftKeys().getValues().get(0);
      try {
        tag = (String) minecraftKey.getClass().getMethod("toString").invoke(minecraftKey);
      } catch (Exception exception) {
        exception.printStackTrace();
        tag = "error";
      }
    } else {
      tag = packet.getStrings().getValues().get(0);
    }
    if (tag.startsWith("minecraft:")) {
      tag = tag.substring(10);
    }
    return tag;
  }

  public String readString() {
    ByteBuf bytes = (ByteBuf) packet.getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
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
