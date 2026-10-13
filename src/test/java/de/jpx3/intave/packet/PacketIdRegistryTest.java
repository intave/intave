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
import de.jpx3.intave.resource.Resources;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static de.jpx3.intave.packet.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.*;

final class PacketIdRegistryTest {
  private static final String DIRECTORY = "registry/packet_ids/";

  @Test
  void coversEveryBundledReleaseProtocolWithoutServerInitialization() {
    Set<Integer> expected = new HashSet<>();
    for (String line : Resources.resourceFromJarOrBuild("protocolversions").readAsString().split("\\R")) {
      if (line.startsWith("release ")) {
        expected.add(Integer.parseInt(line.split(" ")[1]));
      }
    }
    assertEquals(expected, PacketIdRegistry.serverbound().supportedProtocolVersions());
    assertEquals(expected, PacketIdRegistry.clientbound().supportedProtocolVersions());
    for (int protocol : expected) {
      PacketIdRegistry outgoing = PacketIdRegistry.serverbound();
      PacketIdRegistry incoming = PacketIdRegistry.clientbound();
      assertEquals(0, outgoing.id(HANDSHAKING, "SET_PROTOCOL", protocol));
      assertEquals(-1, incoming.id(HANDSHAKING, "SET_PROTOCOL", protocol));
      assertEquals(0, outgoing.id(LOGIN, "START", protocol));
      assertEquals(1, outgoing.id(STATUS, "PING", protocol));
      assertEquals(1, incoming.id(STATUS, "PONG", protocol));
      assertTrue(outgoing.id(PLAY, "ROTATION", protocol) >= 0, "Missing rotation for " + protocol);
      assertTrue(incoming.id(PLAY, "KEEP_ALIVE", protocol) >= 0, "Missing keep alive for " + protocol);
    }
  }

  @Test
  void resolvesEveryNativeClassThroughItsMasterName() {
    JsonObject names = readJson("type_to_name.json").getAsJsonObject().getAsJsonObject("states");
    for (JsonElement version : readJson("index.json").getAsJsonArray()) {
      String name = version.getAsString();
      JsonObject data = readJson(name + ".json").getAsJsonObject();
      int protocol = data.get("protocolVersion").getAsInt();
      for (Map.Entry<String, JsonElement> state : data.getAsJsonObject("states").entrySet()) {
        ConnectionState connectionState = ConnectionState.valueOf(state.getKey().toUpperCase(Locale.ROOT));
        for (Map.Entry<String, JsonElement> direction : state.getValue().getAsJsonObject().entrySet()) {
          JsonObject ids = direction.getValue().getAsJsonObject().getAsJsonObject("ids");
          JsonObject translations = names.getAsJsonObject(state.getKey()).getAsJsonObject(direction.getKey());
          Set<String> resolved = new HashSet<>();
          for (Map.Entry<String, JsonElement> translation : translations.entrySet()) {
            JsonElement className = translation.getValue().getAsJsonObject().get(name);
            if (className != null) {
              assertTrue(resolved.add(className.getAsString()), "Duplicate translation " + translation.getKey());
              assertEquals(ids.get(className.getAsString()).getAsInt(),
                registry(direction.getKey()).id(connectionState, translation.getKey(), protocol),
                name + "/" + state.getKey() + "/" + direction.getKey() + "/" + translation.getKey());
            }
          }
          assertEquals(ids.entrySet().size(), resolved.size(), "Unmapped classes in " + name);
        }
      }
    }
  }

  @Test
  void resolvesEverySupplementalPacket() {
    JsonObject protocols = readJson("additional_protocols.json").getAsJsonObject().getAsJsonObject("protocols");
    for (Map.Entry<String, JsonElement> protocol : protocols.entrySet()) {
      int protocolVersion = Integer.parseInt(protocol.getKey());
      for (Map.Entry<String, JsonElement> state : protocol.getValue().getAsJsonObject().getAsJsonObject("states").entrySet()) {
        ConnectionState connectionState = ConnectionState.valueOf(state.getKey().toUpperCase(Locale.ROOT));
        for (Map.Entry<String, JsonElement> direction : state.getValue().getAsJsonObject().entrySet()) {
          for (Map.Entry<String, JsonElement> packet : direction.getValue().getAsJsonObject().entrySet()) {
            assertEquals(packet.getValue().getAsInt(),
              registry(direction.getKey()).id(connectionState, packet.getKey(), protocolVersion),
              protocolVersion + "/" + state.getKey() + "/" + direction.getKey() + "/" + packet.getKey());
          }
        }
      }
    }
  }

  @Test
  void retainsExactIdsAcrossReleaseBoundariesAndPacketReplacements() {
    PacketIdRegistry serverbound = PacketIdRegistry.serverbound();
    assertEquals(0x05, serverbound.id(PLAY, "ROTATION", 47));
    assertEquals(0x0e, serverbound.id(PLAY, "ROTATION", 107));
    assertEquals(0x10, serverbound.id(PLAY, "ROTATION", 335));
    assertEquals(0x0f, serverbound.id(PLAY, "ROTATION", 338));
    assertEquals(0x20, serverbound.id(PLAY, "ROTATION", 777));
    assertEquals(1, serverbound.id(PLAY, "RECIPE_PLACEMENT", 335));
    assertEquals(-1, serverbound.id(PLAY, "RECIPE_PLACEMENT", 338));
    assertEquals(-1, serverbound.id(PLAY, "AUTO_RECIPE", 335));
    assertEquals(0x12, serverbound.id(PLAY, "AUTO_RECIPE", 338));
    assertEquals(0x1e, serverbound.id(PLAY, "RECIPE_BOOK_DATA", 736));
    assertEquals(-1, serverbound.id(PLAY, "RECIPE_BOOK_DATA", 751));
    assertEquals(0x1e, serverbound.id(PLAY, "RECIPE_SETTINGS", 751));
    assertEquals(5, serverbound.id(PLAY, "CHAT_PREVIEW", 759));
    assertEquals(6, serverbound.id(PLAY, "CHAT_PREVIEW", 760));
    assertEquals(-1, serverbound.id(PLAY, "CHAT_PREVIEW", 761));

    PacketIdRegistry clientbound = PacketIdRegistry.clientbound();
    assertEquals(0x0c, clientbound.id(PLAY, "BOSS", 107));
    assertEquals(-1, clientbound.id(PLAY, "UPDATE_ENTITY_NBT", 107));
    assertEquals(0x46, clientbound.id(PLAY, "UPDATE_SIGN", 109));
    assertEquals(-1, clientbound.id(PLAY, "UPDATE_SIGN", 110));
    assertEquals(0x47, clientbound.id(PLAY, "NAMED_SOUND_EFFECT", 109));
    assertEquals(0x46, clientbound.id(PLAY, "NAMED_SOUND_EFFECT", 110));
    assertEquals(-1, clientbound.id(PLAY, "BLOCK_BREAK_ACK", 490));
    assertEquals(0x5c, clientbound.id(PLAY, "BLOCK_BREAK_ACK", 498));
    assertEquals(0x05, clientbound.id(PLAY, "BLOCK_CHANGED_ACK", 759));
    assertEquals(-1, clientbound.id(PLAY, "BLOCK_BREAK_ACK", 759));
    assertEquals(0x32, clientbound.id(PLAY, "PLAYER_CHAT_HEADER", 760));
  }

  @Test
  void scopesNamesByDirectionAndConnectionState() {
    assertEquals(0x04, PacketIdRegistry.serverbound().id(PLAY, "POSITION", 47));
    assertEquals(0x08, PacketIdRegistry.clientbound().id(PLAY, "POSITION", 47));
    assertEquals(0x0b, PacketIdRegistry.serverbound().id(PLAY, "KEEP_ALIVE", 107));
    assertEquals(0x1f, PacketIdRegistry.clientbound().id(PLAY, "KEEP_ALIVE", 107));
    assertEquals(-1, PacketIdRegistry.serverbound().id(STATUS, "KEEP_ALIVE", 777));
    assertEquals(-1, PacketIdRegistry.serverbound().id(CONFIGURATION, "KEEP_ALIVE", 763));
    assertEquals(3, PacketIdRegistry.serverbound().id(CONFIGURATION, "KEEP_ALIVE", 764));
  }

  @Test
  void distinguishesMissingPacketsFromUnsupportedProtocols() {
    PacketIdRegistry registry = PacketIdRegistry.serverbound();
    assertEquals(-1, registry.id(PLAY, "NOT_A_PACKET", 777));
    assertEquals(-1, registry.id(PLAY, "rotation", 47));
    for (int unsupported : new int[]{-1, 0, 5, 48, 111, 778, 1073742165}) {
      assertThrows(IllegalArgumentException.class, () -> registry.id(PLAY, "ROTATION", unsupported));
    }
    assertThrows(NullPointerException.class, () -> registry.id(null, "ROTATION", 47));
    assertThrows(NullPointerException.class, () -> registry.id(PLAY, null, 47));
  }

  @Test
  void sharesImmutableRegistries() {
    assertSame(PacketIdRegistry.serverbound(), PacketIdRegistry.serverbound());
    assertSame(PacketIdRegistry.clientbound(), PacketIdRegistry.clientbound());
    assertThrows(UnsupportedOperationException.class, () -> PacketIdRegistry.serverbound().supportedProtocolVersions().clear());
  }

  private static PacketIdRegistry registry(String direction) {
    return direction.equals("serverbound") ? PacketIdRegistry.serverbound() : PacketIdRegistry.clientbound();
  }

  private static JsonElement readJson(String file) {
    return new JsonParser().parse(Resources.resourceFromJarOrBuild(DIRECTORY + file).readAsString());
  }
}
