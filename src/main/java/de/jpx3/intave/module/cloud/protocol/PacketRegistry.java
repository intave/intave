package de.jpx3.intave.module.cloud.protocol;

import com.google.common.collect.Maps;
import de.jpx3.intave.module.cloud.protocol.packets.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PacketRegistry {
  private static final Map<Direction, Map<String, Class<? extends Packet<?>>>> packetsByName = Maps.newEnumMap(Direction.class);
  private static final Map<Direction, Map<String, PacketSpecification>> specifications = Maps.newEnumMap(Direction.class);
  private static final Map<Direction, List<Class<? extends Packet<?>>>> packetsByAssignedId = Maps.newEnumMap(Direction.class);

  static {
    registerClientbound(ClientboundCloseConnectionPacket.class);
    registerClientbound(ClientboundCombatModifierPacket.class);
    registerClientbound(ClientboundDownloadStoragePacket.class);
    registerClientbound(ClientboundHelloPacket.class);
    registerClientbound(ClientboundSetTrustfactorPacket.class);
    registerClientbound(ClientboundViolationPacket.class);

    registerServerbound(ServerboundConfirmEncryptionPacket.class);
    registerServerbound(ServerboundHelloPacket.class);
    registerServerbound(ServerboundPassNayoroPacket.class);
    registerServerbound(ServerboundRequestStoragePacket.class);
    registerServerbound(ServerboundRequestTrustfactorPacket.class);
    registerServerbound(ServerboundUploadStoragePacket.class);
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

  public static Packet<?> fromAssignedId(Direction direction, int id) {
    try {
      return packetsByAssignedId.get(direction).get(id).newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static int packetIdOf(Direction direction, Class<? extends Packet> aClass) {
    return packetsByAssignedId.get(direction).indexOf(aClass);
  }

  public static void enterIdAssignment(Direction direction, List<String> packetNames) {
    List<Class<? extends Packet<?>>> classes = packetsByAssignedId.computeIfAbsent(direction, x -> new ArrayList<>());
    Map<String, Class<? extends Packet<?>>> nameAccess = packetsByName.computeIfAbsent(direction, x -> new HashMap<>());
    classes.clear();
    for (String packetName : packetNames) {
      classes.add(nameAccess.get(packetName));
    }
  }

  public static boolean hadIdAssignment(Direction direction) {
    return !packetsByAssignedId.computeIfAbsent(direction, x -> new ArrayList<>()).isEmpty();
  }

  public static Map<String, PacketSpecification> specificationFor(Direction direction) {
    return specifications.computeIfAbsent(direction, x -> new HashMap<>());
  }
}
