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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PacketIdRegistries {
  private static final String DIRECTORY = "registry/packet_ids/";
  private static final PacketIdRegistry<PacketType.Client> SERVERBOUND;
  private static final PacketIdRegistry<PacketType.Server> CLIENTBOUND;

  static {
    Map<Integer, Map<ConnectionState, Map<PacketType.Client, Integer>>> serverbound = new HashMap<>();
    Map<Integer, Map<ConnectionState, Map<PacketType.Server, Integer>>> clientbound = new HashMap<>();
    Map<String, PacketType.Client> clientTypes = indexTypes(PacketType.Client.values());
    Map<String, PacketType.Server> serverTypes = indexTypes(PacketType.Server.values());
    JsonObject names = readVersionedObject("type_to_name.json").getAsJsonObject("states");
    for (JsonElement entry : readJson("index.json").getAsJsonArray()) {
      String version = entry.getAsString();
      JsonObject data = readVersionedObject(version + ".json");
      int protocol = data.get("protocolVersion").getAsInt();
      JsonObject states = data.getAsJsonObject("states");
      serverbound.put(protocol, readStates(states, "serverbound", names, version, clientTypes));
      clientbound.put(protocol, readStates(states, "clientbound", names, version, serverTypes));
    }
    JsonObject additional = readVersionedObject("additional_protocols.json").getAsJsonObject("protocols");
    for (Map.Entry<String, JsonElement> entry : additional.entrySet()) {
      int protocol = Integer.parseInt(entry.getKey());
      JsonObject states = entry.getValue().getAsJsonObject().getAsJsonObject("states");
      serverbound.put(protocol, readStates(states, "serverbound", null, null, clientTypes));
      clientbound.put(protocol, readStates(states, "clientbound", null, null, serverTypes));
    }
    SERVERBOUND = new PacketIdRegistry<>(PacketType.Client.class, serverbound);
    CLIENTBOUND = new PacketIdRegistry<>(PacketType.Server.class, clientbound);
  }

  public static void setup() {
    // intentionally left blank
  }

  private PacketIdRegistries() {
  }

  public static PacketIdRegistry<PacketType.Client> serverbound() {
    return SERVERBOUND;
  }

  public static PacketIdRegistry<PacketType.Server> clientbound() {
    return CLIENTBOUND;
  }

  private static <T extends Enum<T> & PacketType> Map<ConnectionState, Map<T, Integer>> readStates(
    JsonObject source, String direction, JsonObject names, String version, Map<String, T> packetTypes
  ) {
    Map<ConnectionState, Map<T, Integer>> states = new EnumMap<>(ConnectionState.class);
    for (Map.Entry<String, JsonElement> state : source.entrySet()) {
      ConnectionState connectionState = ConnectionState.valueOf(state.getKey().toUpperCase(Locale.ROOT));
      JsonObject sourceIds = state.getValue().getAsJsonObject().getAsJsonObject(direction);
      Map<T, Integer> ids = new HashMap<>();
      if (names == null) {
        for (Map.Entry<String, JsonElement> packet : sourceIds.entrySet()) {
          ids.put(requireType(packetTypes, packet.getKey()), packet.getValue().getAsInt());
        }
      } else {
        JsonObject nativeIds = sourceIds.getAsJsonObject("ids");
        JsonObject translations = names.getAsJsonObject(state.getKey()).getAsJsonObject(direction);
        for (Map.Entry<String, JsonElement> packet : translations.entrySet()) {
          JsonElement className = packet.getValue().getAsJsonObject().get(version);
          if (className != null) {
            ids.put(requireType(packetTypes, packet.getKey()), nativeIds.get(className.getAsString()).getAsInt());
          }
        }
      }
      states.put(connectionState, ids);
    }
    return states;
  }

  private static <T extends PacketType> Map<String, T> indexTypes(T[] values) {
    Map<String, T> types = new HashMap<>();
    for (T value : values) {
      T previous = types.put(value.registryName(), value);
      if (previous != null) {
        throw new IntaveInternalException("Duplicate packet registry name " + value.registryName());
      }
    }
    return types;
  }

  private static <T extends PacketType> T requireType(Map<String, T> types, String name) {
    T type = types.get(name);
    if (type == null) {
      throw new IntaveInternalException("Unknown packet registry name " + name);
    }
    return type;
  }

  private static JsonObject readVersionedObject(String file) {
    JsonObject object = readJson(file).getAsJsonObject();
    if (object.get("schemaVersion").getAsInt() != 1) {
      throw new IntaveInternalException("Unsupported packet registry schema in " + file);
    }
    return object;
  }

  private static JsonElement readJson(String file) {
    Resource resource = Resources.resourceFromJarOrBuild(DIRECTORY + file);
    if (!resource.available()) {
      throw new IntaveInternalException("Missing packet registry " + file);
    }
    try {
      return new JsonParser().parse(resource.readAsString());
    } catch (RuntimeException exception) {
      throw new IntaveInternalException("Unable to load packet registry " + file, exception);
    }
  }
}
