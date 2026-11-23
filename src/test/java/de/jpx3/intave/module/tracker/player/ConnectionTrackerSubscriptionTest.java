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

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.accessbackend.IntaveAccessService;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerStage;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.immutable.KeepAlivePacket;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import de.jpx3.intave.packet.event.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.KEEP_ALIVE_IN;
import static de.jpx3.intave.packet.contract.PacketType.Server.KEEP_ALIVE_OUT;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class ConnectionTrackerSubscriptionTest {
  private final PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
  private final List<PacketSubscription> extraSubscriptions = new ArrayList<>();
  private final List<String> updates = new ArrayList<>();
  private IntaveAccessService access;
  private Field version;
  private MinecraftVersion previousVersion;

  @BeforeEach
  void linkTracker() throws Exception {
    version = MinecraftVersion.class.getDeclaredField("currentVersion");
    version.setAccessible(true);
    previousVersion = (MinecraftVersion) version.get(null);
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    IntavePlugin plugin = (IntavePlugin) ReflectionFactory.getReflectionFactory()
      .newConstructorForSerialization(IntavePlugin.class, Object.class.getConstructor()).newInstance();
    access = new IntaveAccessService(plugin);
    Field accessField = IntavePlugin.class.getDeclaredField("accessService");
    accessField.setAccessible(true);
    accessField.set(plugin, access);
    ConnectionTracker tracker = new ConnectionTracker();
    tracker.setPlugin(plugin);
    linker.linkSubscriptionsIn(tracker);
  }

  @AfterEach
  void unlinkTracker() throws Exception {
    linker.disable();
    extraSubscriptions.forEach(PacketEventBus::removeListener);
    version.set(null, previousVersion);
  }

  @Test
  void tracksRepeatedRequestsOnProtocol47() {
    tracksRepeatedRequests(47);
  }

  @Test
  void tracksRepeatedRequestsOnProtocol335() {
    tracksRepeatedRequests(335);
  }

  @Test
  void tracksRepeatedRequestsOnProtocol340() {
    tracksRepeatedRequests(340);
  }

  @Test
  void tracksRepeatedRequestsOnProtocol764() {
    tracksRepeatedRequests(764);
  }

  @Test
  void tracksRepeatedRequestsOnProtocol777() {
    tracksRepeatedRequests(777);
  }

  private void tracksRepeatedRequests(int protocol) {
    User user = client(protocol);
    long id = protocol < 340 ? -123 : 0x123456789abcdefL;
    send(user, id, true);
    assertTrue(user.meta().connection().pendingKeepAliveIdentifiers().isEmpty());
    send(user, id, false);
    send(user, id, false);
    assertEquals(Collections.singleton(id), user.meta().connection().pendingKeepAliveIdentifiers());
    receive(user, id, true);
    assertEquals(Collections.singleton(id), user.meta().connection().pendingKeepAliveIdentifiers());
    receive(user, id, false);
    assertEquals(Collections.singleton(id), user.meta().connection().pendingKeepAliveIdentifiers());
    receive(user, id, false);
    assertTrue(user.meta().connection().pendingKeepAliveIdentifiers().isEmpty());
    assertEquals(2, updates.size());
    receive(user, id, false);
    assertEquals(2, updates.size());
  }

  @Test
  void expiresOldRequestsOnlyWhenAnUncancelledRequestIsSent() {
    User user = client(777);
    var connection = user.meta().connection();
    long now = System.currentTimeMillis();
    connection.addPendingKeepAlive(1, now - 31000);
    connection.addPendingKeepAlive(2, now);
    send(user, 3, true);
    assertTrue(connection.pendingKeepAliveIdentifiers().contains(1L));
    send(user, 3, false);
    assertFalse(connection.pendingKeepAliveIdentifiers().contains(1L));
    assertEquals(now, connection.pollPendingKeepAlive(2));
    assertTrue(connection.pollPendingKeepAlive(3) >= now);
  }

  @Test
  void observesRewrittenIdsAfterPacketMutationsInBothDirections() {
    User user = client(777);
    addObserver(CLIENTBOUND, ListenerStage.MUTABLE, ListenerPriority.LAST,
      event -> ((MutableKeepAlivePacket) event.packet()).setId(42));
    addObserver(SERVERBOUND, ListenerStage.MUTABLE, ListenerPriority.LAST,
      event -> ((MutableKeepAlivePacket) event.packet()).setId(42));
    send(user, 11, false);
    assertEquals(Collections.singleton(42L), user.meta().connection().pendingKeepAliveIdentifiers());
    receive(user, 12, false);
    assertTrue(user.meta().connection().pendingKeepAliveIdentifiers().isEmpty());
    assertEquals(1, updates.size());
  }

  @Test
  void ignoresPacketsCancelledByLateObservers() {
    User user = client(777);
    addObserver(CLIENTBOUND, ListenerStage.AFTER, ListenerPriority.LATER, event -> event.setCancelled(true));
    addObserver(SERVERBOUND, ListenerStage.AFTER, ListenerPriority.LATER, event -> event.setCancelled(true));
    send(user, 42, false);
    assertTrue(user.meta().connection().pendingKeepAliveIdentifiers().isEmpty());
    user.meta().connection().addPendingKeepAlive(42, System.currentTimeMillis());
    receive(user, 42, false);
    assertEquals(Collections.singleton(42L), user.meta().connection().pendingKeepAliveIdentifiers());
    assertTrue(updates.isEmpty());
  }

  @Test
  void ignoresZeroAndUntrackedRepliesWithoutChangingStatistics() {
    User user = client(777);
    var connection = user.meta().connection();
    connection.latency = 42;
    connection.latencyJitter = 7;
    connection.addPendingKeepAlive(0, System.currentTimeMillis());
    receive(user, 0, false);
    receive(user, 99, false);
    assertEquals(Collections.singleton(0L), connection.pendingKeepAliveIdentifiers());
    assertEquals(42, connection.latency);
    assertEquals(7, connection.latencyJitter);
    assertTrue(connection.latencyDifferenceBalance().isEmpty());
    assertTrue(updates.isEmpty());
  }

  @Test
  void keepsLatencyClampingSmoothingAndTheEightSampleJitterWindow() {
    User user = client(777);
    var connection = user.meta().connection();
    connection.latency = 200;
    connection.lastKeepAliveDifference = 300;
    connection.latencyDifferenceBalance().addAll(Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L));
    connection.addPendingKeepAlive(1, System.currentTimeMillis() - 5000);
    receive(user, 1, false);
    assertEquals(400, connection.latency);
    assertEquals(1000, connection.lastKeepAliveDifference);
    assertEquals(131, connection.latencyJitter);
    assertEquals(Arrays.asList(20L, 30L, 40L, 50L, 60L, 70L, 80L, 700L), connection.latencyDifferenceBalance());
    assertEquals(Collections.singletonList("400/700"), updates);
    connection.addPendingKeepAlive(2, System.currentTimeMillis() + 60000);
    receive(user, 2, false);
    assertEquals(300, connection.latency);
    assertEquals(0, connection.lastKeepAliveDifference);
    assertEquals(253, connection.latencyJitter);
    assertEquals(Arrays.asList("400/700", "300/1000"), updates);
  }

  private User client(int protocol) {
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getLocation" -> new Location(null, 0, 64, 0);
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, (current, name) -> switch (name) {
      case "protocolVersion" -> protocol;
      case "justJoined" -> true;
      default -> null;
    });
    access.playerAccessor().netStatisticsAccessor().netStatisticsOf(player)
      .subscribe((ping, jitter) -> updates.add(ping + "/" + jitter));
    return user;
  }

  private static KeepAlivePacket packet(PacketDirection direction, int protocol, long id) {
    return roundTrip(direction, direction == CLIENTBOUND ? KEEP_ALIVE_OUT : KEEP_ALIVE_IN, protocol, out -> {
      if (protocol < 340) VARINT.encode(out, Math.toIntExact(id));
      else out.writeLong(id);
    });
  }

  private static void send(User user, long id, boolean cancelled) {
    PacketSendEvent event = new PacketSendEvent(user, KEEP_ALIVE_OUT, packet(CLIENTBOUND, user.protocolVersion(), id));
    event.setCancelled(cancelled);
    PacketEventBus.dispatch(event);
  }

  private static void receive(User user, long id, boolean cancelled) {
    PacketReceiveEvent event = new PacketReceiveEvent(user, KEEP_ALIVE_IN, packet(SERVERBOUND, user.protocolVersion(), id));
    event.setCancelled(cancelled);
    PacketEventBus.dispatch(event);
  }

  private void addObserver(PacketDirection direction, ListenerStage stage, ListenerPriority priority, Consumer<PacketEvent> observer) {
    PacketSubscription subscription = new PacketSubscription() {
      @Override
      public void onPacket(PacketEvent event) {
        observer.accept(event);
      }

      @Override
      public ListenerStage stage() {
        return stage;
      }

      @Override
      public ListenerPriority priority() {
        return priority;
      }
    };
    if (direction == CLIENTBOUND) PacketEventBus.addListener(subscription, KEEP_ALIVE_OUT);
    else PacketEventBus.addListener(subscription, KEEP_ALIVE_IN);
    extraSubscriptions.add(subscription);
  }
}
