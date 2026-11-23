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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.PayloadInPacket;
import de.jpx3.intave.packet.contract.mutable.MutablePayloadInPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.ConnectionState.CONFIGURATION;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.packet.contract.PacketType.Client.SETTINGS;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsTrackerTest {
  private final PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
  private Field version;
  private MinecraftVersion previousVersion;

  @BeforeEach
  void linkTracker() throws Exception {
    version = MinecraftVersion.class.getDeclaredField("currentVersion");
    version.setAccessible(true);
    previousVersion = (MinecraftVersion) version.get(null);
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    linker.linkSubscriptionsIn(new SettingsTracker());
  }

  @AfterEach
  void unlinkTracker() throws Exception {
    linker.disable();
    version.set(null, previousVersion);
  }

  @Test
  void usesReportedLocaleAcrossSettingsLayoutsAndIgnoresCancelledChanges() {
    for (int protocol : new int[]{47, 107, 755, 757, 764, 768, 777}) {
      User user = client(protocol);
      user.meta().protocol().setLocale("en_us");
      PacketContract packet = settings(PLAY, protocol, "de_de");
      receive(user, SETTINGS, packet, true);
      assertEquals("en_us", user.meta().protocol().locale());
      receive(user, SETTINGS, packet, false);
      assertEquals("de_de", user.meta().protocol().locale());
      receive(user, SETTINGS, settings(PLAY, protocol, "ja_jp"), false);
      assertEquals("ja_jp", user.meta().protocol().locale());
    }
  }

  @Test
  void decodesLegacyAndModernBrandStringsWithoutMutatingPayloads() {
    for (int protocol : new int[]{47, 393, 764, 777}) {
      User user = client(protocol);
      user.meta().protocol().setClientBrand("previous");
      String tag = protocol < 393 ? "mC|bRaNd" : "minecraft:brand";
      // UTF-8 bytes and a multi-byte VarInt length must both be decoded correctly.
      String brand = "Fabric-" + "ä".repeat(80);
      PayloadInPacket packet = payload(PLAY, protocol, tag, brandBytes(brand));
      receive(user, CUSTOM_PAYLOAD_IN, packet, true);
      assertEquals("previous", user.meta().protocol().clientBrand());
      receive(user, CUSTOM_PAYLOAD_IN, packet, false);
      assertEquals(brand, user.meta().protocol().clientBrand());
      assertEquals(brand, packet.clientBrand().orElseThrow());
      assertEquals(brand, packet.clientBrand().orElseThrow());
      receive(user, CUSTOM_PAYLOAD_IN, payload(PLAY, protocol, tag, brandBytes("vanilla")), false);
      assertEquals("vanilla", user.meta().protocol().clientBrand());
    }
  }

  @Test
  void unrelatedAndMalformedPayloadsLeaveBrandUnchanged() {
    User user = client(777);
    user.meta().protocol().setClientBrand("vanilla");
    receive(user, CUSTOM_PAYLOAD_IN, payload(PLAY, 777, "plugin:brand", brandBytes("ignored")), false);
    receive(user, CUSTOM_PAYLOAD_IN, payload(PLAY, 777, "minecraft:register", new byte[0]), false);
    for (byte[] malformed : new byte[][]{
      {}, {(byte) 0x80}, {5, 'a'},
      {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f},
      {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0}
    }) {
      PayloadInPacket packet = payload(PLAY, 777, "minecraft:brand", malformed);
      receive(user, CUSTOM_PAYLOAD_IN, packet, false);
      assertTrue(packet.clientBrand().isEmpty());
    }
    assertEquals("vanilla", user.meta().protocol().clientBrand());
  }

  @Test
  void configurationSettingsAndBrandUpdateAnAssociatedUser() {
    for (int protocol : new int[]{764, 768, 777}) {
      User user = client(protocol);
      receive(user, SETTINGS, settings(CONFIGURATION, protocol, "fr_fr"), false);
      receive(user, CUSTOM_PAYLOAD_IN, payload(CONFIGURATION, protocol, "minecraft:brand", brandBytes("fabric")), false);
      assertEquals("fr_fr", user.meta().protocol().locale());
      assertEquals("fabric", user.meta().protocol().clientBrand());
    }
  }

  @Test
  void decodedBrandReflectsPayloadAndChannelEdits() {
    MutablePayloadInPacket packet = (MutablePayloadInPacket) payload(PLAY, 777, "minecraft:brand", brandBytes("vanilla"));
    assertEquals("vanilla", packet.clientBrand().orElseThrow());
    assertFalse(packet.couldHaveBeenMutated());
    packet.setPayload(brandBytes("fabric"));
    assertEquals("fabric", packet.clientBrand().orElseThrow());
    packet.setTag("plugin:brand");
    assertTrue(packet.clientBrand().isEmpty());
    assertTrue(packet.couldHaveBeenMutated());
  }

  private User client(int protocol) {
    var world = FakeWorldFactory.createWorld((name, args) -> null);
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> id;
      default -> null;
    });
    return UserFactory.createTestUserFor(player, protocol);
  }

  private PacketContract settings(ConnectionState state, int protocol, String locale) {
    return decode(state, SETTINGS, protocol, b -> {
      VARINT_STRING.encode(b, locale);
      b.writeByte(10).writeByte(0).writeBoolean(true).writeByte(127);
      if (protocol >= 107) b.writeByte(1);
      if (protocol >= 755) b.writeBoolean(true);
      if (protocol >= 757) b.writeBoolean(false);
      if (protocol >= 768) b.writeByte(2);
    });
  }

  private PayloadInPacket payload(ConnectionState state, int protocol, String tag, byte[] body) {
    return (PayloadInPacket) decode(state, CUSTOM_PAYLOAD_IN, protocol, b -> {
      VARINT_STRING.encode(b, tag);
      b.writeBytes(body);
    });
  }

  private PacketContract decode(ConnectionState state, PacketType.Client type, int protocol, Consumer<ByteBuf> body) {
    ByteBuf input = Unpooled.buffer();
    try {
      int id = PacketTypeRegistries.serverbound().idOf(state, type, protocol);
      assertTrue(id >= 0);
      VARINT.encode(input, id);
      body.accept(input);
      PacketContract packet = WirePackets.decodePacket(SERVERBOUND, state, protocol, input);
      assertNotNull(packet);
      return packet;
    } finally {
      input.release();
    }
  }

  private void receive(User user, PacketType.Client type, PacketContract packet, boolean cancelled) {
    ByteBuf before = Unpooled.buffer(), after = Unpooled.buffer();
    try {
      // Settings and payload layouts are shared between play and configuration.
      WirePackets.encodePacket(PLAY, type, user.protocolVersion(), packet, before);
      PacketReceiveEvent event = new PacketReceiveEvent(user, type, packet);
      event.setCancelled(cancelled);
      PacketEventBus.dispatch(event);
      assertFalse(event.dirty(), "Observing settings must not mark packets as mutated");
      assertEquals(cancelled, event.isCancelled());
      WirePackets.encodePacket(PLAY, type, user.protocolVersion(), packet, after);
      assertEquals(before, after);
    } finally {
      before.release();
      after.release();
    }
  }

  private byte[] brandBytes(String brand) {
    ByteBuf buffer = Unpooled.buffer();
    try {
      VARINT_STRING.encode(buffer, brand);
      byte[] bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      return bytes;
    } finally {
      buffer.release();
    }
  }
}
