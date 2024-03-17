package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.packet.PacketRegistry;

import java.util.*;
import java.util.function.Supplier;

import static de.jpx3.intave.module.linker.packet.PacketId.Client;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLOSE_WINDOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketReaders {
  private static final Map<PacketType, ThreadLocal<? extends PacketReader>> readerLocals = new HashMap<>();

  public static void setup() {
    setup(ABILITIES_OUT, AbilityOutReader::new);
    setup(ANIMATION, EntityReader::new);
    setup(ATTACH_ENTITY, AttachEntityReader::new);
    setup(BLOCK_ACTION, BlockActionReader::new);
    setup(BLOCK_CHANGE, SingleBlockChangeReader::new);
    setup(BLOCK_BREAK, SingleBlockChangeReader::new);
    setup(BLOCK_BREAK_ANIMATION, EntityReader::new);
    setup(CAMERA, EntityReader::new);
    setup(COLLECT, EntityReader::new);
    setup(COMBAT_EVENT, CombatEventReader::new);
    setup(ENTITY, EntityReader::new);
    setup(ENTITY_DESTROY, EntityDestroyReader::new);
    setup(ENTITY_EFFECT, EntityEffectReader::new);
    setup(ENTITY_EQUIPMENT, EntityReader::new);
    setup(ENTITY_HEAD_ROTATION, EntityReader::new);
    setup(ENTITY_LOOK, EntityReader::new);
    setup(ENTITY_METADATA, EntityMetadataReader::new);
    setup(ENTITY_MOVE_LOOK, EntityReader::new);
    setup(ENTITY_STATUS, EntityReader::new);
    setup(ENTITY_SOUND, EntityReader::new);
    setup(ENTITY_TELEPORT, EntityReader::new);
    setup(ENTITY_VELOCITY, EntityReader::new);
    setup(GAME_STATE_CHANGE, GameStateChangeReader::new);
    setup(LOGIN, EntityReader::new);
    setup(LOOK_AT, EntityReader::new);
    setup(MAP_CHUNK, MapChunkReader::new);
    setup(MAP_CHUNK_BULK, MapChunkBulkReader::new);
    setup(MOUNT, MountEntityReader::new);
    setup(MULTI_BLOCK_CHANGE, MultiBlockChangeReader::new);
    setup(NAMED_ENTITY_SPAWN, EntityReader::new);
    setup(OPEN_WINDOW, OpenWindowReader::new);
    setup(OPEN_WINDOW_HORSE, OpenWindowReader::new);
    setup(CLOSE_WINDOW, WindowCloseReader::new);
    setup(PLAYER_INFO, PlayerInfoReader::new);
    setup(PLAYER_INFO_REMOVE, PlayerInfoRemoveReader::new);
    setup(REMOVE_ENTITY_EFFECT, EntityReader::new);
    setup(REL_ENTITY_MOVE, EntityReader::new);
    setup(REL_ENTITY_MOVE_LOOK, EntityReader::new);
    setup(SPAWN_ENTITY, EntityReader::new);
    setup(SPAWN_ENTITY_LIVING, EntityReader::new);
    setup(SPAWN_ENTITY_PAINTING, EntityReader::new);
    setup(SPAWN_ENTITY_WEATHER, EntityReader::new);
    setup(SPAWN_ENTITY_EXPERIENCE_ORB, EntityReader::new);
    setup(UPDATE_ATTRIBUTES, EntityReader::new);
    setup(UPDATE_ENTITY_NBT, EntityReader::new);
    setup(USE_BED, EntityReader::new);

    setup(ABILITIES_IN, AbilityInReader::new);
    setup(BLOCK_DIG, BlockPositionReader::new);
    setup(BLOCK_PLACE, BlockInteractionReader::new);
    setup(CUSTOM_PAYLOAD_IN, PayloadInReader::new);
    setup(ENTITY_ACTION_IN, PlayerActionReader::new);
    setup(USE_ITEM, BlockInteractionReader::new);
    setup(USE_ENTITY, EntityUseReader::new);
    setup(WINDOW_ITEMS, WindowBulkItemReader::new);
    setup(WINDOW_CLICK, WindowClickReader::new);
    setup(SET_SLOT, WindowSingleItemReader::new);

    // for some
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

  private static PacketType searchByName(Collection<? extends PacketType> packetPool, String name) {
    Collection<PacketType> packetTypes = PacketType.fromName(name);
    return packetTypes.stream().filter(packetPool::contains).findFirst().orElse(
      packetPool.stream().filter(packetType -> matches(packetType, name)).findFirst().orElse(null)
    );
  }

  private static boolean matches(PacketType packetType, String name) {
    return packetType != null && packetType.name() != null && packetType.name().equalsIgnoreCase(name);
  }

  public static <T extends PacketReader> T readerOf(PacketContainer container) {
    PacketType type = container.getType();
    ThreadLocal<? extends PacketReader> readerThreadLocal = readerLocals.get(type);
    if (readerThreadLocal == null) {

      // perform a name-based lookup, enter if found
      for (Map.Entry<PacketType, ThreadLocal<? extends PacketReader>> entry : readerLocals.entrySet()) {
        if (matches(entry.getKey(), type.name())) {
          // must be the same protocol
//          if (!type.getProtocol().equals(entry.getKey().getProtocol())) {
//            continue;
//          }
          readerThreadLocal = entry.getValue();
          readerLocals.put(type, readerThreadLocal);
          break;
        }
      }

      if (readerThreadLocal == null) {
        throw new IllegalStateException("No reader available for type " + type.name());
      }
    }
    PacketReader interpreter = readerThreadLocal.get();
    interpreter.enter(container);
    //noinspection unchecked
    return (T) interpreter;
  }

  public static boolean hasReader(PacketType type) {
    return readerLocals.containsKey(type);
  }
}
