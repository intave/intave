package de.jpx3.intave.connect.proxy.protocol;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.connect.proxy.protocol.packets.*;

import java.util.*;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public final class PacketRegister {
  private static final Map<Integer, Class<? extends IntavePacket>> packets;

  static {
    Map<Integer, Class<? extends IntavePacket>> packetMap = new HashMap<>();
    packetMap.put(0, IntavePacketOutVersion.class);
    packetMap.put(1, IntavePacketOutExecuteCommand.class);
    packetMap.put(2, IntavePacketOutPunishment.class);
    packetMap.put(3, IntavePacketOutKicked.class);
    packetMap.put(100, IntavePacketInRequestVersion.class);
    packets = ImmutableMap.copyOf(packetMap);
  }

  public static Map<Integer, Class<? extends IntavePacket>> idToPacketTypes() {
    return packets;
  }

  public static Collection<Class<? extends IntavePacket>> packetTypes() {
    return Collections.unmodifiableCollection(packets.values());
  }

  public static Optional<Class<? extends IntavePacket>> typeOfId(int packetId) {
    return Optional.ofNullable(packets.get(packetId));
  }

  public static int getIdentifierOf(Class<? extends IntavePacket> packetClass) {
    return packets.entrySet()
      .stream()
      .filter(integerClassEntry -> integerClassEntry.getValue().equals(packetClass))
      .findFirst()
      .map(Map.Entry::getKey)
      .orElse(-1);
  }
}
