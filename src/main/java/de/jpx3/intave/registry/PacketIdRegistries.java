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
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PacketIdRegistries {
  private static final String DIRECTORY = "registry/packet_ids/";
  private static final PacketIdRegistry SERVERBOUND;
  private static final PacketIdRegistry CLIENTBOUND;

  static {
    Map<Integer, Map<ConnectionState, Map<String, Integer>>> serverbound = new HashMap<>();
    Map<Integer, Map<ConnectionState, Map<String, Integer>>> clientbound = new HashMap<>();
    JsonObject names = readVersionedObject("type_to_name.json").getAsJsonObject("states");
    for (JsonElement entry : readJson("index.json").getAsJsonArray()) {
      String version = entry.getAsString();
      JsonObject data = readVersionedObject(version + ".json");
      int protocol = data.get("protocolVersion").getAsInt();
      JsonObject states = data.getAsJsonObject("states");
      serverbound.put(protocol, readStates(states, "serverbound", names, version));
      clientbound.put(protocol, readStates(states, "clientbound", names, version));
    }
    JsonObject additional = readVersionedObject("additional_protocols.json").getAsJsonObject("protocols");
    for (Map.Entry<String, JsonElement> entry : additional.entrySet()) {
      int protocol = Integer.parseInt(entry.getKey());
      JsonObject states = entry.getValue().getAsJsonObject().getAsJsonObject("states");
      serverbound.put(protocol, readStates(states, "serverbound", null, null));
      clientbound.put(protocol, readStates(states, "clientbound", null, null));
    }
    SERVERBOUND = new PacketIdRegistry(serverbound);
    CLIENTBOUND = new PacketIdRegistry(clientbound);
  }

  public static void setup() {
    // intentionally left blank
  }

  private PacketIdRegistries() {
  }

  public static PacketIdRegistry serverbound() {
    return SERVERBOUND;
  }

  public static PacketIdRegistry clientbound() {
    return CLIENTBOUND;
  }

  private static Map<ConnectionState, Map<String, Integer>> readStates(
    JsonObject source, String direction, JsonObject names, String version
  ) {
    Map<ConnectionState, Map<String, Integer>> states = new EnumMap<>(ConnectionState.class);
    for (Map.Entry<String, JsonElement> state : source.entrySet()) {
      ConnectionState connectionState = ConnectionState.valueOf(state.getKey().toUpperCase(Locale.ROOT));
      JsonObject sourceIds = state.getValue().getAsJsonObject().getAsJsonObject(direction);
      Map<String, Integer> ids = new HashMap<>();
      if (names == null) {
        for (Map.Entry<String, JsonElement> packet : sourceIds.entrySet()) {
          ids.put(packet.getKey(), packet.getValue().getAsInt());
        }
      } else {
        JsonObject nativeIds = sourceIds.getAsJsonObject("ids");
        JsonObject translations = names.getAsJsonObject(state.getKey()).getAsJsonObject(direction);
        for (Map.Entry<String, JsonElement> packet : translations.entrySet()) {
          JsonElement className = packet.getValue().getAsJsonObject().get(version);
          if (className != null) {
            ids.put(packet.getKey(), nativeIds.get(className.getAsString()).getAsInt());
          }
        }
      }
      states.put(connectionState, ids);
    }
    return states;
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
