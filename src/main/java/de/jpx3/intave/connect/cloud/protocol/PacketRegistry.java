package de.jpx3.intave.connect.cloud.protocol;

import com.google.common.collect.Maps;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.connect.cloud.protocol.packets.*;

import java.util.*;

public final class PacketRegistry {
  private static final Map<Direction, Map<Class<? extends Packet<?>>, String>> nameByPacket = Maps.newHashMap();
  private static final Map<Direction, Map<String, Class<? extends Packet<?>>>> packetsByName = Maps.newEnumMap(Direction.class);
  private static final Map<Direction, Map<String, PacketSpecification>> specifications = Maps.newEnumMap(Direction.class);

  static {
    registerClientbound(ClientboundDisconnectPacket.class);
    registerClientbound(ClientboundCombatModifierPacket.class);
    registerClientbound(ClientboundDownloadStoragePacket.class);
    registerClientbound(ClientboundHelloPacket.class);
    registerClientbound(ClientboundSetTrustfactorPacket.class);
    registerClientbound(ClientboundViolationPacket.class);
    registerClientbound(ClientboundKeepAlivePacket.class);
    registerClientbound(ClientboundLogReceivePacket.class);

    registerServerbound(ServerboundConfirmEncryptionPacket.class);
    registerServerbound(ServerboundHelloPacket.class);
    registerServerbound(ServerboundPassNayoroPacket.class);
    registerServerbound(ServerboundRequestStoragePacket.class);
    registerServerbound(ServerboundRequestTrustfactorPacket.class);
    registerServerbound(ServerboundUploadStoragePacket.class);
    registerServerbound(ServerboundKeepAlivePacket.class);
    registerServerbound(ServerboundUploadLogsPacket.class);
  }
  
  private static void registerClientbound(Class<? extends Packet<?>> packetClass) {
    register(Direction.CLIENTBOUND, packetClass);
  }

  private static void registerServerbound(Class<? extends Packet<?>> packetClass) {
    register(Direction.SERVERBOUND, packetClass);
  }

  private static void register(Direction direction, Class<? extends Packet<?>> packetClass) {
    try {
      Packet<?> packet = packetClass.newInstance();
      String packetName = packet.name();
      nameByPacket.computeIfAbsent(direction, x -> new HashMap<>())
        .put(packetClass, packetName);
      packetsByName.computeIfAbsent(direction, x -> new HashMap<>())
        .put(packetName, packetClass);
      specifications.computeIfAbsent(direction, x -> new HashMap<>())
        .put(packetName, PacketSpecification.from(packet));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Packet<?> fromName(Direction direction, String name) {
    try {
      return packetsByName.get(direction).get(name).newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String clientboundName(Class<? extends Packet<Clientbound>> packetClass) {
    return nameByPacket.get(Direction.CLIENTBOUND).get(packetClass);
  }

  public static String serverboundName(Class<? extends Packet<Serverbound>> packetClass) {
    return nameByPacket.get(Direction.SERVERBOUND).get(packetClass);
  }

  public static Set<String> packetNamesOf(Direction direction) {
    return new HashSet<>(packetsByName.get(direction).keySet());
  }

  public static Packet<?> fromAssignedId(ProtocolSpecification protocol, Direction direction, int id) {
    Map<Integer, String> idToName = protocol.packetIdsOf(direction);
    try {
      String packetName = idToName.get(id);
      return fromName(direction, packetName);
    } catch (Exception exception) {
      System.out.println("Failed to find packet id " + id + " direction " + direction + " (avail: " + idToName+")");
      throw new RuntimeException(exception);
    }
  }

  public static Map<String, PacketSpecification> packetSpecsFor(Direction direction) {
    return specifications.computeIfAbsent(direction, x -> new HashMap<>());
  }
}
