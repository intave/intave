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

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.PacketFilterManager;
import de.jpx3.intave.IntaveLogger;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class PacketSender {
  private static final PacketFilterManager protocolManager = (PacketFilterManager) ProtocolLibrary.getProtocolManager();
  private static final boolean PROTOCOL_LIB_CORRECTED_THEIR_TYPO = Arrays.stream(ProtocolManager.class.getDeclaredMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("receiveClientPacket"));
  private static final Method RECEIVE_PACKET_METHOD;

  static {
    Method method = null;
    try {
      method = PROTOCOL_LIB_CORRECTED_THEIR_TYPO ?
        ProtocolManager.class.getMethod("receiveClientPacket", Player.class, PacketContainer.class) :
        ProtocolManager.class.getMethod("recieveClientPacket", Player.class, PacketContainer.class);
    } catch (NoSuchMethodException exception) {
      exception.printStackTrace();
    }
    RECEIVE_PACKET_METHOD = method;
  }

  public static void sendServerPacket(Player receiver, PacketContainer packet) {
    if (!protocolManager.isClosed()) {
      protocolManager.sendServerPacket(receiver, packet);
    }
  }

  public static void sendServerPacketWithoutEvent(Player receiver, PacketContainer packet) {
    if (!protocolManager.isClosed()) {
      protocolManager.sendServerPacket(receiver, packet, false);
    }
  }

  public static void receiveClientPacketFrom(Player receiver, PacketContainer packet) {
    try {
      RECEIVE_PACKET_METHOD.invoke(protocolManager, receiver, packet);
    } catch (UnsupportedOperationException exception) {
      IntaveLogger.logger().error("Your version of ProtocolLib is broken, see https://github.com/dmulloy2/ProtocolLib/issues/1552 for details on the issue");
      IntaveLogger.logger().error("We recommend you to upgrade your version");
      exception.printStackTrace();
    } catch (IllegalAccessException | InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }
}
