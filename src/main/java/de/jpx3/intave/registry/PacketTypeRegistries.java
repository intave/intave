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

package de.jpx3.intave.registry;

import com.google.common.collect.ImmutableBiMap;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static de.jpx3.intave.codec.JsonStreamCodecs.INTEGER;
import static de.jpx3.intave.codec.JsonStreamCodecs.stringMapCodec;

public final class PacketTypeRegistries {
  private static final Map<String, PacketType.Client> CLIENT_TYPES = indexTypes(PacketType.Client.values());
  private static final Map<String, PacketType.Server> SERVER_TYPES = indexTypes(PacketType.Server.values());
  private static final ProtocolRegistryFiles<PacketIds> TABLES = new ProtocolRegistryFiles<>(
    "packet_ids", stringMapCodec(stringMapCodec(stringMapCodec(INTEGER))), PacketIds::new
  );
  private static final PacketTypeRegistry<PacketType.Client> SERVERBOUND = new PacketTypeRegistry<>(
    TABLES.supportedProtocolVersions(), protocol -> TABLES.forProtocol(protocol).serverbound
  );
  private static final PacketTypeRegistry<PacketType.Server> CLIENTBOUND = new PacketTypeRegistry<>(
    TABLES.supportedProtocolVersions(), protocol -> TABLES.forProtocol(protocol).clientbound
  );

  private PacketTypeRegistries() {
  }

  public static void setup() {
    TABLES.supportedProtocolVersions().forEach(TABLES::forProtocol);
  }

  public static PacketTypeRegistry<PacketType.Client> serverbound() {
    return SERVERBOUND;
  }

  public static PacketTypeRegistry<PacketType.Server> clientbound() {
    return CLIENTBOUND;
  }

  private static <T extends PacketType> Map<String, T> indexTypes(T[] values) {
    Map<String, T> types = new HashMap<>();
    for (T value : values) {
      if (types.put(value.registryName(), value) != null) {
        throw new IntaveInternalException("Duplicate packet registry name " + value.registryName());
      }
    }
    return types;
  }

  private static final class PacketIds {
    private final Map<ConnectionState, ImmutableBiMap<PacketType.Client, Integer>> serverbound = new EnumMap<>(ConnectionState.class);
    private final Map<ConnectionState, ImmutableBiMap<PacketType.Server, Integer>> clientbound = new EnumMap<>(ConnectionState.class);

    private PacketIds(Map<String, Map<String, Map<String, Integer>>> states) {
      states.forEach((name, directions) -> {
        ConnectionState state = ConnectionState.valueOf(name.toUpperCase(Locale.ROOT));
        serverbound.put(state, resolve(directions.get("serverbound"), CLIENT_TYPES));
        clientbound.put(state, resolve(directions.get("clientbound"), SERVER_TYPES));
      });
    }

    private static <T extends PacketType> ImmutableBiMap<T, Integer> resolve(Map<String, Integer> ids, Map<String, T> types) {
      ImmutableBiMap.Builder<T, Integer> table = ImmutableBiMap.builder();
      ids.forEach((name, id) -> {
        T type = types.get(name);
        if (type == null) {
          throw new IntaveInternalException("Unknown packet registry name " + name);
        }
        if (id < 0) {
          throw new IntaveInternalException("Invalid packet ID " + id + " for " + name);
        }
        table.put(type, id);
      });
      return table.build();
    }
  }
}
