/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.plib;

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
