package de.jpx3.intave.packet.converter;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.reflect.Lookup;

public final class PlayerActionResolver {
  private final static Class<?> SERVER_CLASS = Lookup.serverClass("PacketPlayInEntityAction$EnumPlayerAction");

  public static PlayerAction resolveActionFromPacket(PacketContainer packet) {
    return packet.getEnumModifier(PlayerAction.class, SERVER_CLASS).read(0);
  }
}