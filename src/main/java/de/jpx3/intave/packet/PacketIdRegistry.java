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

package de.jpx3.intave.packet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exact wire IDs by master packet name, for every release in {@code protocolversions}.
 * Direction is selected once because the same master name can occur in both directions.
 * All bundled ID tables and class-name translations are loaded together on first use.
 */
public final class PacketIdRegistry {
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

  private final Map<Integer, Map<ConnectionState, Map<String, Integer>>> protocols;
  private final Set<Integer> supportedProtocolVersions;

  private PacketIdRegistry(Map<Integer, Map<ConnectionState, Map<String, Integer>>> protocols) {
    this.protocols = Collections.unmodifiableMap(protocols);
    this.supportedProtocolVersions = Collections.unmodifiableSet(new TreeSet<>(protocols.keySet()));
  }

  /** Client to server packets. The returned registry is shared and immutable. */
  public static PacketIdRegistry serverbound() {
    return SERVERBOUND;
  }

  /** Server to client packets. The returned registry is shared and immutable. */
  public static PacketIdRegistry clientbound() {
    return CLIENTBOUND;
  }

  /**
   * Returns the wire ID, or {@code -1} if this master name/state has no packet in
   * the requested release. Names are case-sensitive, for example {@code ROTATION}.
   *
   * @throws IllegalArgumentException if the exact protocol version is unsupported;
   *         snapshots, unknown versions and gaps are never rounded to a release
   */
  public int id(ConnectionState connectionState, String typeName, int protocolVersion) {
    Objects.requireNonNull(connectionState, "connectionState");
    Objects.requireNonNull(typeName, "typeName");
    Map<ConnectionState, Map<String, Integer>> states = protocols.get(protocolVersion);
    if (states == null) {
      throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
    }
    return states.getOrDefault(connectionState, Collections.emptyMap()).getOrDefault(typeName, -1);
  }

  public Set<Integer> supportedProtocolVersions() {
    return supportedProtocolVersions;
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
      states.put(connectionState, Collections.unmodifiableMap(ids));
    }
    return Collections.unmodifiableMap(states);
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
