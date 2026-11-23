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
import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.packet.contract.immutable.WindowBulkItemsPacket;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.WINDOW_ITEMS;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryTrackerWindowItemsTest {
  @Test
  void acknowledgesOnlyPlayerInventoryContentsAndKeepsSlotNumbers() {
    int protocol = 777;
    MinecraftVersion.setCurrent(new MinecraftVersion("26.3"));
    var world = FakeWorldFactory.createWorld((name, args) -> null);
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> id;
      default -> null;
    });
    User delegate = UserFactory.createTestUserFor(player, protocol);
    Queue<EmptyFeedbackCallback> feedback = new ArrayDeque<>();
    User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
      if (method.getName().equals("tickFeedback")) {
        feedback.add((EmptyFeedbackCallback) args[0]);
        return null;
      }
      return method.invoke(delegate, args);
    });
    InventoryTracker tracker = new InventoryTracker();
    for (int containerId : new int[]{1, 0}) {
      WindowBulkItemsPacket packet = roundTrip(CLIENTBOUND, WINDOW_ITEMS, protocol, out -> {
        VARINT.encode(out, containerId);
        out.writeByte(0).writeByte(2);
        out.writeByte(0);
        out.writeByte(1);
        VARINT.encode(out, ItemRegistry.forProtocol(protocol).idOf(MinecraftKey.withDefaultNamespace("totem_of_undying")));
        out.writeByte(0).writeByte(0);
        out.writeByte(0);
      });
      PacketSendEvent event = new PacketSendEvent(user, WINDOW_ITEMS, packet);
      event.setCancelled(true);
      tracker.sentWindowItems(user, event, packet);
      assertTrue(feedback.isEmpty());
      event.setCancelled(false);
      tracker.sentWindowItems(user, event, packet);
      assertEquals(containerId == 0 ? 1 : 0, feedback.size());
    }
    assertTrue(user.meta().inventory().items().isEmpty());
    feedback.remove().success();
    assertEquals(Arrays.asList("AIR", "TOTEM_OF_UNDYING"), user.meta().inventory().items());
  }
}
