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

package de.jpx3.intave.packet.plib.converter;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.share.PlayerAction;

public final class PlayerActionResolver {
  private static final Class<?> SERVER_CLASS = Lookup.serverClass("PacketPlayInEntityAction$EnumPlayerAction");

  public static PlayerAction resolveActionFromPacket(PacketContainer packet) {
    return packet.getEnumModifier(PlayerAction.class, SERVER_CLASS).read(0);
  }
}