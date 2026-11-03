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
import de.jpx3.intave.packet.plib.reader.AbilityInReader;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.AbilityMetadata;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AbilityTrackerFlightTest {
  @Test
  void acknowledgesClientTogglesWithoutWaitingForAServerEcho() {
    User user = user();
    AbilityMetadata abilities = user.meta().abilities();
    abilities.setAllowFlying(true);
    AbilityTracker tracker = new AbilityTracker();

    toggle(tracker, user, true, false);
    toggle(tracker, user, true, false);
    assertTrue(abilities.flying());
    assertTrue(abilities.acknowledgedFlying());
    assertTrue(abilities.startedFlying());
    assertFalse(abilities.acknowledgeFlying(true), "A later server echo must not duplicate the toggle");

    abilities.tickComplete();
    assertFalse(abilities.startedFlying());

    toggle(tracker, user, false, false);
    assertFalse(abilities.acknowledgedFlying());
    assertTrue(abilities.flying(), "Keep predicted flight until the existing tick boundary");
    assertTrue(abilities.disabledFlying, "The next movement tick has ambiguous flight ordering");
    abilities.tickComplete();
    assertFalse(abilities.flying());
  }

  @Test
  void ignoresDisallowedAndCancelledRequests() {
    User user = user();
    AbilityTracker tracker = new AbilityTracker();
    toggle(tracker, user, true, false);
    assertFalse(user.meta().abilities().acknowledgedFlying());
    user.meta().abilities().setAllowFlying(true);
    toggle(tracker, user, true, true);
    assertFalse(user.meta().abilities().acknowledgedFlying());
  }

  private static void toggle(AbilityTracker tracker, User user, boolean flying, boolean cancelled) {
    AbilityInReader reader = new AbilityInReader() {
      @Override public boolean requestedFlying() { return flying; }
    };
    if (!cancelled) tracker.receiveAbilities(user, reader);
    tracker.recordClientAbilities(user, reader, new Cancellable() {
      @Override public boolean isCancelled() { return cancelled; }
      @Override public void setCancelled(boolean value) { throw new AssertionError(); }
    });
  }

  private static User user() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    World world = FakeWorldFactory.createWorld((name, args) -> null);
    return UserFactory.createTestUserFor(FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> UUID.randomUUID();
      default -> null;
    }), 769);
  }
}
