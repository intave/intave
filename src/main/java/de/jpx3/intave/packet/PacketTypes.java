package de.jpx3.intave.packet;

import com.comphenix.protocol.PacketType;

public class PacketTypes {
  private final static PacketType cteType;

  static {
    PacketType clientTickEndType;
    try {
      clientTickEndType = PacketType.Play.Client.CLIENT_TICK_END;
    } catch (NoSuchFieldError error) {
      clientTickEndType = null;
    }
    cteType = clientTickEndType;
  }

  public static boolean isClientEndTick(PacketType type) {
    return type != null && type.equals(cteType);
  }
}
