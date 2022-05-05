package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.packet.PacketRegistry;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static de.jpx3.intave.module.linker.packet.PacketId.Client;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketReaders {
  private final static Map<PacketType, ThreadLocal<? extends PacketReader>> readerLocals = new ConcurrentHashMap<>();

  public static void setup() {
    setup(BLOCK_CHANGE, SingleBlockChangeReader::new);
    setup(BLOCK_BREAK, SingleBlockChangeReader::new);
    setup(MULTI_BLOCK_CHANGE, MultiBlockChangeReader::new);
    setup(MAP_CHUNK, MapChunkReader::new);
    setup(MAP_CHUNK_BULK, MapChunkBulkReader::new);
    setup(ENTITY_METADATA, EntityReader::new);
    setup(ENTITY_VELOCITY, EntityReader::new);
    setup(SPAWN_ENTITY_LIVING, EntityReader::new);
    setup(SPAWN_ENTITY, EntityReader::new);
    setup(ENTITY_EFFECT, EntityReader::new);
    setup(REMOVE_ENTITY_EFFECT, EntityReader::new);
    setup(NAMED_ENTITY_SPAWN, EntityReader::new);
    setup(UPDATE_ATTRIBUTES, EntityReader::new);
    setup(BLOCK_BREAK_ANIMATION, EntityReader::new);

    setup(ENTITY_DESTROY, EntityDestroyReader::new);
    setup(CUSTOM_PAYLOAD_IN, PayloadInReader::new);
    setup(BLOCK_PLACE, BlockInteractionReader::new);
    setup(USE_ITEM, BlockInteractionReader::new);
    setup(USE_ENTITY, EntityUseReader::new);
    setup(BLOCK_DIG, BlockPositionReader::new);
  }

  private static void setup(Server serverPacket, Supplier<? extends PacketReader> supplier) {
    PacketType packetType = searchByName(selectPacketTypesFor(ConnectionSide.SERVER_SIDE), serverPacket.lookupName());
    if (packetType != null) {
      readerLocals.put(packetType, ThreadLocal.withInitial(supplier));
    }
  }

  private static void setup(Client clientPacket, Supplier<? extends PacketReader> supplier) {
    PacketType packetType = searchByName(selectPacketTypesFor(ConnectionSide.CLIENT_SIDE), clientPacket.lookupName());
    if (packetType != null) {
      readerLocals.put(packetType, ThreadLocal.withInitial(supplier));
    }
  }

  private static Collection<PacketType> selectPacketTypesFor(ConnectionSide connectionSide) {
    Set<PacketType> availableTypes = new HashSet<>();
    if (connectionSide.isForServer()) availableTypes.addAll(PacketRegistry.getServerPacketTypes());
    if (connectionSide.isForClient()) availableTypes.addAll(PacketRegistry.getClientPacketTypes());
    return availableTypes;
  }

  private static PacketType searchByName(Collection<PacketType> packetPool, String name) {
    return packetPool.stream().filter(packetType -> matches(packetType, name)).findFirst().orElse(null);
  }

  private static boolean matches(PacketType packetType, String name) {
    return packetType.name().equalsIgnoreCase(name);
  }

  public static <T extends PacketReader> T readerOf(PacketContainer container) {
    PacketType type = container.getType();
    ThreadLocal<? extends PacketReader> threadLocal = readerLocals.get(type);
    if (threadLocal == null) {
      throw new IllegalStateException("No interpreter available for " + type);
    }
    PacketReader interpreter = threadLocal.get();
    interpreter.flush(container);
    if (interpreter instanceof CompiledPacketReader) {
      ((CompiledPacketReader) interpreter).compile();
    }
    //noinspection unchecked
    return (T) interpreter;
  }
}
