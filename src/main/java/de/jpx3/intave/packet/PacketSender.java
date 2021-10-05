package de.jpx3.intave.packet;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.PacketFilterManager;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public final class PacketSender {
  private static final PacketFilterManager protocolManager = (PacketFilterManager) ProtocolLibrary.getProtocolManager();

  public static void sendServerPacket(Player receiver, PacketContainer packet) {
    if (!protocolManager.isClosed()) {
      try {
        protocolManager.sendServerPacket(receiver, packet);
      } catch (InvocationTargetException exception) {
        exception.printStackTrace();
      }
    }
  }
}