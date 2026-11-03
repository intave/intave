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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.EventSink;
import ac.intave.samples.event.FlyStateUpdateEvent;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.plib.reader.AbilityInReader;
import de.jpx3.intave.packet.plib.reader.AbilityOutReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PlayerFlightRecorderTest {
  @BeforeAll
  static void configureServerVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void startsEveryRecordingWithAFullSnapshotAndRawAbilitySpeed() {
    Fixture fixture = new Fixture();
    List<FlyStateUpdateEvent> snapshots = new ArrayList<>();
    fixture.recorder.start(fixture.user, sink(snapshots), () -> assertEquals(1, snapshots.size()));
    fixture.recorder.start(fixture.user, sink(snapshots), () -> assertEquals(2, snapshots.size()));

    FlyStateUpdateEvent initial = snapshots.get(0);
    assertTrue(initial.allowFlight());
    assertEquals(0.05F, initial.flySpeed());
    assertEquals("SURVIVAL", initial.gameMode());
    assertNull(initial.clientFlying(), "Bukkit flying is not a client observation");
    assertFalse(initial.serverFlying());
    assertNotSame(initial, snapshots.get(1));
  }

  @Test
  void keepsDisallowedClientReportsAndServerCorrectionsIndependent() {
    Fixture fixture = new Fixture();
    fixture.server(false, 0.05F, false, false);
    fixture.client(true);
    fixture.client(true);
    assertEquals(2, fixture.events.size());
    FlyStateUpdateEvent denied = fixture.last();
    assertFalse(denied.allowFlight());
    assertTrue(denied.clientFlying());
    assertFalse(denied.serverFlying());

    fixture.server(true, 0.05F, true, false);
    fixture.client(false);
    assertFalse(fixture.last().clientFlying());
    assertTrue(fixture.last().serverFlying());
    assertTrue(denied.clientFlying(), "Earlier snapshots must stay unchanged");
    assertFalse(denied.serverFlying());

    List<FlyStateUpdateEvent> snapshots = new ArrayList<>();
    fixture.recorder.start(fixture.user, sink(snapshots), () -> {});
    assertFalse(snapshots.get(0).clientFlying(), "Keep observations made before recording started");
  }

  @Test
  void recordsPermissionAndSpeedChangesWithoutAFlightToggle() {
    Fixture fixture = new Fixture();
    fixture.server(false, 0.05F, false, false);
    fixture.server(true, 0.05F, false, false);
    fixture.server(true, 0.2F, false, false);
    fixture.server(true, 0.2F, false, false);
    assertEquals(3, fixture.events.size());
    assertTrue(fixture.last().allowFlight());
    assertEquals(0.2F, fixture.last().flySpeed(), "Packet speed is already on the raw abilities scale");
    assertFalse(fixture.last().serverFlying());
    assertNull(fixture.last().clientFlying());
  }

  @Test
  void ignoresCancelledServerPacketsAndClearsUnavailableSpeed() {
    Fixture fixture = new Fixture();
    fixture.server(true, 0.05F, true, false);
    fixture.server(false, 0.1F, false, true);
    assertEquals(1, fixture.events.size());
    assertTrue(fixture.last().serverFlying());
    fixture.server(true, Float.NaN, true, false);
    assertNull(fixture.last().flySpeed());
    assertTrue(fixture.last().serverFlying());
  }

  @Test
  void recordsAcceptedBukkitChangesWithoutWaitingForAnAbilitiesPacket() {
    Fixture fixture = new Fixture();
    UserRepository.manuallyRegisterUser(fixture.player, fixture.user);
    try {
      fixture.recorder.start(fixture.user, sink(new ArrayList<>()), () -> {});
      fixture.client(true);
      PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(fixture.player, true);
      toggle.setCancelled(true);
      fixture.recorder.flight(toggle);
      assertFalse(fixture.last().serverFlying());
      toggle.setCancelled(false);
      fixture.recorder.flight(toggle);
      assertTrue(fixture.last().clientFlying());
      assertTrue(fixture.last().serverFlying());

      PlayerGameModeChangeEvent mode = new PlayerGameModeChangeEvent(fixture.player, GameMode.CREATIVE);
      mode.setCancelled(true);
      fixture.recorder.gameMode(mode);
      assertEquals("SURVIVAL", fixture.last().gameMode());
      mode.setCancelled(false);
      fixture.recorder.gameMode(mode);
      assertEquals("CREATIVE", fixture.last().gameMode());
      assertTrue(fixture.last().serverFlying());
    } finally {
      UserRepository.unregisterUser(fixture.player);
    }
  }

  @Test
  void doesNotShareObservationsBetweenPlayers() {
    Fixture first = new Fixture();
    Fixture second = new Fixture();
    first.client(true);
    first.recorder.clientAbilities(second.user, clientReader(false));
    FlyStateUpdateEvent other = first.last();
    assertFalse(other.clientFlying());
    assertNull(other.serverFlying());
    first.server(true, 0.05F, false, false);
    assertTrue(first.last().clientFlying());
  }

  private static EventSink sink(List<FlyStateUpdateEvent> snapshots) {
    return new EventSink() {
      @Override public void visitAny(Event event) { snapshots.add(assertInstanceOf(FlyStateUpdateEvent.class, event)); }
      @Override public String name() { return "test"; }
    };
  }

  private static AbilityInReader clientReader(boolean flying) {
    return new AbilityInReader() {
      @Override public boolean requestedFlying() { return flying; }
    };
  }

  private static final class Fixture {
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(
      Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
        switch (method.getName()) {
          case "getUniqueId": return id;
          case "getAllowFlight": return true;
          case "getFlySpeed": return 0.1F;
          case "getGameMode": return GameMode.SURVIVAL;
          case "isFlying": return false;
          default: throw new AssertionError(method.getName());
        }
      });
    private final User user = (User) Proxy.newProxyInstance(
      User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
        switch (method.getName()) {
          case "hasPlayer": return true;
          case "player": return player;
          case "id": return id;
          case "unregister": return null;
          default: throw new AssertionError(method.getName());
        }
      });
    private final List<FlyStateUpdateEvent> events = new ArrayList<>();
    private final PlayerFlightRecorder recorder = new PlayerFlightRecorder((user, event) -> events.add(event));

    private FlyStateUpdateEvent last() {
      return events.get(events.size() - 1);
    }

    private void client(boolean flying) {
      recorder.clientAbilities(user, clientReader(flying));
    }

    private void server(boolean allowed, float speed, boolean flying, boolean cancelled) {
      recorder.serverAbilities(user, new Cancellable() {
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { throw new AssertionError(); }
      }, new AbilityOutReader() {
        @Override public boolean flyingAllowed() { return allowed; }
        @Override public float flyingSpeed() { return speed; }
        @Override public boolean flying() { return flying; }
      });
    }
  }
}
