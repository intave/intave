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
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
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
    assertEquals(expected, PacketIdRegistries.serverbound().supportedProtocolVersions());
    assertEquals(expected, PacketIdRegistries.clientbound().supportedProtocolVersions());
    for (int protocol : expected) {
      PacketIdRegistry outgoing = PacketIdRegistries.serverbound();
      PacketIdRegistry incoming = PacketIdRegistries.clientbound();
      assertEquals(0, outgoing.idOf(HANDSHAKING, "SET_PROTOCOL", protocol));
      assertEquals(-1, incoming.idOf(HANDSHAKING, "SET_PROTOCOL", protocol));
      assertEquals(0, outgoing.idOf(LOGIN, "START", protocol));
      assertEquals(1, outgoing.idOf(STATUS, "PING", protocol));
      assertEquals(1, incoming.idOf(STATUS, "PONG", protocol));
      assertTrue(outgoing.idOf(PLAY, "ROTATION", protocol) >= 0, "Missing rotation for " + protocol);
      assertTrue(incoming.idOf(PLAY, "KEEP_ALIVE", protocol) >= 0, "Missing keep alive for " + protocol);
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
              int packetId = ids.get(className.getAsString()).getAsInt();
              PacketIdRegistry registry = registry(direction.getKey());
              assertEquals(packetId,
                registry.idOf(connectionState, translation.getKey(), protocol),
                name + "/" + state.getKey() + "/" + direction.getKey() + "/" + translation.getKey());
              assertEquals(translation.getKey(), registry.typeNameOf(connectionState, packetId, protocol));
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
            int packetId = packet.getValue().getAsInt();
            PacketIdRegistry registry = registry(direction.getKey());
            assertEquals(packetId,
              registry.idOf(connectionState, packet.getKey(), protocolVersion),
              protocolVersion + "/" + state.getKey() + "/" + direction.getKey() + "/" + packet.getKey());
            assertEquals(packet.getKey(), registry.typeNameOf(connectionState, packetId, protocolVersion));
          }
        }
      }
    }
  }

  @Test
  void retainsExactIdsAcrossReleaseBoundariesAndPacketReplacements() {
    PacketIdRegistry serverbound = PacketIdRegistries.serverbound();
    assertEquals(0x05, serverbound.idOf(PLAY, "ROTATION", 47));
    assertEquals(0x0e, serverbound.idOf(PLAY, "ROTATION", 107));
    assertEquals(0x10, serverbound.idOf(PLAY, "ROTATION", 335));
    assertEquals(0x0f, serverbound.idOf(PLAY, "ROTATION", 338));
    assertEquals(0x20, serverbound.idOf(PLAY, "ROTATION", 777));
    assertEquals(1, serverbound.idOf(PLAY, "RECIPE_PLACEMENT", 335));
    assertEquals(-1, serverbound.idOf(PLAY, "RECIPE_PLACEMENT", 338));
    assertEquals(-1, serverbound.idOf(PLAY, "AUTO_RECIPE", 335));
    assertEquals(0x12, serverbound.idOf(PLAY, "AUTO_RECIPE", 338));
    assertEquals(0x1e, serverbound.idOf(PLAY, "RECIPE_BOOK_DATA", 736));
    assertEquals(-1, serverbound.idOf(PLAY, "RECIPE_BOOK_DATA", 751));
    assertEquals(0x1e, serverbound.idOf(PLAY, "RECIPE_SETTINGS", 751));
    assertEquals(5, serverbound.idOf(PLAY, "CHAT_PREVIEW", 759));
    assertEquals(6, serverbound.idOf(PLAY, "CHAT_PREVIEW", 760));
    assertEquals(-1, serverbound.idOf(PLAY, "CHAT_PREVIEW", 761));

    PacketIdRegistry clientbound = PacketIdRegistries.clientbound();
    assertEquals(0x0c, clientbound.idOf(PLAY, "BOSS", 107));
    assertEquals(-1, clientbound.idOf(PLAY, "UPDATE_ENTITY_NBT", 107));
    assertEquals(0x46, clientbound.idOf(PLAY, "UPDATE_SIGN", 109));
    assertEquals(-1, clientbound.idOf(PLAY, "UPDATE_SIGN", 110));
    assertEquals(0x47, clientbound.idOf(PLAY, "NAMED_SOUND_EFFECT", 109));
    assertEquals(0x46, clientbound.idOf(PLAY, "NAMED_SOUND_EFFECT", 110));
    assertEquals(-1, clientbound.idOf(PLAY, "BLOCK_BREAK_ACK", 490));
    assertEquals(0x5c, clientbound.idOf(PLAY, "BLOCK_BREAK_ACK", 498));
    assertEquals(0x05, clientbound.idOf(PLAY, "BLOCK_CHANGED_ACK", 759));
    assertEquals(-1, clientbound.idOf(PLAY, "BLOCK_BREAK_ACK", 759));
    assertEquals(0x32, clientbound.idOf(PLAY, "PLAYER_CHAT_HEADER", 760));
  }

  @Test
  void scopesNamesByDirectionAndConnectionState() {
    assertEquals(0x04, PacketIdRegistries.serverbound().idOf(PLAY, "POSITION", 47));
    assertEquals(0x08, PacketIdRegistries.clientbound().idOf(PLAY, "POSITION", 47));
    assertEquals(0x0b, PacketIdRegistries.serverbound().idOf(PLAY, "KEEP_ALIVE", 107));
    assertEquals(0x1f, PacketIdRegistries.clientbound().idOf(PLAY, "KEEP_ALIVE", 107));
    assertEquals(-1, PacketIdRegistries.serverbound().idOf(STATUS, "KEEP_ALIVE", 777));
    assertEquals(-1, PacketIdRegistries.serverbound().idOf(CONFIGURATION, "KEEP_ALIVE", 763));
    assertEquals(3, PacketIdRegistries.serverbound().idOf(CONFIGURATION, "KEEP_ALIVE", 764));
  }

  @Test
  void distinguishesMissingPacketsFromUnsupportedProtocols() {
    PacketIdRegistry registry = PacketIdRegistries.serverbound();
    assertEquals(-1, registry.idOf(PLAY, "NOT_A_PACKET", 777));
    assertEquals(-1, registry.idOf(PLAY, "rotation", 47));
    assertNull(registry.typeNameOf(PLAY, -1, 777));
    assertNull(registry.typeNameOf(PLAY, Integer.MAX_VALUE, 777));
    for (int unsupported : new int[]{-1, 0, 5, 48, 111, 778, 1073742165}) {
      assertThrows(IllegalArgumentException.class, () -> registry.idOf(PLAY, "ROTATION", unsupported));
      assertThrows(IllegalArgumentException.class, () -> registry.typeNameOf(PLAY, 0, unsupported));
    }
    assertThrows(NullPointerException.class, () -> registry.idOf(null, "ROTATION", 47));
    assertThrows(NullPointerException.class, () -> registry.idOf(PLAY, null, 47));
    assertThrows(NullPointerException.class, () -> registry.typeNameOf(null, 0, 47));
  }

  @Test
  void sharesImmutableRegistries() {
    assertSame(PacketIdRegistries.serverbound(), PacketIdRegistries.serverbound());
    assertSame(PacketIdRegistries.clientbound(), PacketIdRegistries.clientbound());
    assertThrows(UnsupportedOperationException.class, () -> PacketIdRegistries.serverbound().supportedProtocolVersions().clear());
  }

  private static PacketIdRegistry registry(String direction) {
    return direction.equals("serverbound") ? PacketIdRegistries.serverbound() : PacketIdRegistries.clientbound();
  }

  private static JsonElement readJson(String file) {
    return new JsonParser().parse(Resources.resourceFromJarOrBuild(DIRECTORY + file).readAsString());
  }
}
