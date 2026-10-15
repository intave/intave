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
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.AbilityOutReader;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class AbilityTrackerRespawnTest {
  @Test
  void respawnResetsCustomSpeedOnlyAfterFeedback() throws Exception {
    for (int protocol : new int[]{VER_1_8, VER_1_21_4, VER_26_1_1}) {
      Harness h = new Harness(protocol);
      h.respawn(false);
      assertEquals(0.2F, h.speed(), "In-flight movement still uses the old speed");

      h.acknowledgeAll();
      assertEquals(0.05F, h.speed(), "A new client player starts with default abilities: " + protocol);
    }
  }

  @Test
  void respawnDiscardsAnEarlierAbilitiesUpdate() throws Exception {
    Harness h = new Harness(VER_26_1_1);
    h.abilities(0.4F);
    h.respawn(false);
    assertEquals(0.2F, h.speed());

    h.acknowledgeNext();
    assertEquals(0.4F, h.speed());
    h.acknowledgeAll();
    assertEquals(0.05F, h.speed());
  }

  @Test
  void laterAbilitiesRestoreCustomSpeedAfterRespawn() throws Exception {
    Harness h = new Harness(VER_26_1_1);
    h.respawn(false);
    h.abilities(0.4F);
    assertEquals(0.2F, h.speed());

    h.acknowledgeNext();
    assertEquals(0.05F, h.speed());
    h.acknowledgeNext();
    assertEquals(0.4F, h.speed());
    assertTrue(h.feedback.isEmpty());
  }

  @Test
  void cancelledRespawnKeepsCustomSpeed() throws Exception {
    Harness h = new Harness(VER_26_1_1);
    h.respawn(true);
    h.acknowledgeAll();
    assertEquals(0.2F, h.speed());
  }

  private static final class Harness {
    private final AbilityTracker tracker = new AbilityTracker();
    private final Queue<EmptyFeedbackCallback> feedback = new ArrayDeque<>();
    private final User user;

    private Harness(int protocol) {
      MinecraftVersion.setCurrent(new MinecraftVersion("1.21.11"));
      var world = FakeWorldFactory.createWorld((name, args) -> null);
      UUID id = UUID.randomUUID();
      var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
        case "getWorld" -> world;
        case "getLocation" -> new Location(world, 0, 64, 0);
        case "getUniqueId" -> id;
        default -> null;
      });
      User delegate = UserFactory.createTestUserFor(player, protocol);
      user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
        switch (method.getName()) {
          case "tickFeedback" -> { feedback.add((EmptyFeedbackCallback) args[0]); return null; }
          case "packetTickFeedback" -> { feedback.add((EmptyFeedbackCallback) args[1]); return null; }
          default -> { return method.invoke(delegate, args); }
        }
      });
      user.meta().abilities().setFlySpeed(0.2F);
    }

    private void respawn(boolean cancelled) throws Exception {
      Cancellable packet = new Cancellable() {
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean value) { throw new AssertionError("Respawn must not be rewritten"); }
      };
      // Dispatch through the subscription so the test also detects a missing respawn listener.
      for (var method : AbilityTracker.class.getDeclaredMethods()) {
        PacketSubscription subscription = method.getAnnotation(PacketSubscription.class);
        if (subscription != null && Arrays.asList(subscription.packetsOut()).contains(RESPAWN)) {
          method.invoke(tracker, user, packet);
        }
      }
    }

    private void abilities(float speed) {
      tracker.sentAbilities(user, new AbilityOutReader() {
        @Override public float flyingSpeed() { return speed; }
        @Override public float walkingSpeed() { return 0.1F; }
        @Override public boolean flying() { return true; }
        @Override public boolean flyingAllowed() { return true; }
      }, null);
    }

    private float speed() {
      return user.meta().abilities().flySpeed();
    }

    private void acknowledgeNext() {
      assertFalse(feedback.isEmpty(), "Expected a pending packet acknowledgement");
      feedback.remove().success();
    }

    private void acknowledgeAll() {
      while (!feedback.isEmpty()) acknowledgeNext();
    }
  }
}
