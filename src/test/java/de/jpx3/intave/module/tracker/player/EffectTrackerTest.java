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

import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableEntityEffectPacket;
import de.jpx3.intave.packet.contract.mutable.MutableRemoveEntityEffectPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.jpx3.intave.packet.contract.PacketType.Server.ENTITY_EFFECT;
import static de.jpx3.intave.packet.contract.PacketType.Server.REMOVE_ENTITY_EFFECT;
import static org.junit.jupiter.api.Assertions.*;

final class EffectTrackerTest {
  private NativeFeedbackTestSupport feedback;
  private final List<Player> players = new ArrayList<>();

  @BeforeEach
  void linkTracker() throws Exception {
    feedback = new NativeFeedbackTestSupport();
    feedback.linker.linkSubscriptionsIn(new EffectTracker());
  }

  @AfterEach
  void unlinkTracker() throws Exception {
    players.forEach(UserRepository::unregisterUser);
    feedback.close();
  }

  @Test
  void effectsAndRemovalsFollowAcknowledgementsInOrder() {
    for (int protocol : new int[]{47, 758, 759, 764, 766, 777}) {
      Harness h = client(protocol);
      var data = h.user.meta().potions();
      for (String effect : new String[]{"speed", "slowness", "jump_boost"}) {
        h.send(ENTITY_EFFECT, h.effect(effect, 2, 100), false);
      }
      assertEquals(0, data.potionEffectSpeedDuration);
      assertEquals(0, data.potionEffectSlownessDuration);
      assertEquals(0, data.potionEffectJumpDuration);
      h.acknowledgeNext();
      assertEquals(3, data.potionEffectSpeedAmplifier());
      assertEquals(99, data.potionEffectSpeedDuration);
      assertEquals(0, data.potionEffectSlownessDuration);
      h.acknowledgeNext();
      assertEquals(3, data.potionEffectSlownessAmplifier());
      assertEquals(99, data.potionEffectSlownessDuration);
      h.acknowledgeNext();
      assertEquals(2, data.potionEffectJumpAmplifier());
      assertEquals(99, data.potionEffectJumpDuration);

      for (String effect : new String[]{"speed", "slowness", "jump_boost"}) {
        h.send(REMOVE_ENTITY_EFFECT, h.removal(effect), false);
      }
      assertEquals(99, data.potionEffectSpeedDuration);
      h.acknowledgeNext();
      assertEquals(0, data.potionEffectSpeedAmplifier());
      assertEquals(0, data.potionEffectSpeedDuration);
      assertEquals(99, data.potionEffectSlownessDuration);
      h.acknowledgeNext();
      assertEquals(0, data.potionEffectSlownessAmplifier());
      assertEquals(0, data.potionEffectSlownessDuration);
      h.acknowledgeNext();
      assertEquals(0, data.potionEffectJumpAmplifier());
      assertEquals(0, data.potionEffectJumpDuration);
      assertTrue(h.pending.isEmpty());
    }
  }

  @Test
  void cancelledPacketsAndOtherEntitiesDoNotScheduleChanges() {
    Harness h = client(777);
    var data = h.user.meta().potions();
    data.potionEffectSpeedAmplifier(3);
    data.potionEffectSpeedDuration = 99;
    h.send(ENTITY_EFFECT, h.effect("speed", 5, 200), true);
    h.send(REMOVE_ENTITY_EFFECT, h.removal("speed"), true);
    var otherEffect = h.effect("speed", 5, 200);
    otherEffect.setEntityId(13);
    h.send(ENTITY_EFFECT, otherEffect, false);
    var otherRemoval = h.removal("speed");
    otherRemoval.setEntityId(13);
    h.send(REMOVE_ENTITY_EFFECT, otherRemoval, false);
    assertTrue(h.pending.isEmpty());
    assertEquals(3, data.potionEffectSpeedAmplifier());
    assertEquals(99, data.potionEffectSpeedDuration);
  }

  @Test
  void feedbackCapturesSentValuesAndPreservesInfiniteDurationHandling() {
    for (int protocol : new int[]{762, 763, 777}) {
      Harness h = client(protocol);
      var effect = h.effect("speed", 4, -1);
      h.send(ENTITY_EFFECT, effect, false);
      effect.setEffectType(MinecraftKey.withDefaultNamespace("slowness"));
      effect.setEffectAmplifier(10);
      effect.setEffectDuration(200);
      h.acknowledgeNext();
      var data = h.user.meta().potions();
      assertEquals(5, data.potionEffectSpeedAmplifier());
      assertEquals(protocol >= 763 ? Integer.MAX_VALUE - 1 : -2, data.potionEffectSpeedDuration);
      assertEquals(0, data.potionEffectSlownessDuration);
      var removal = h.removal("speed");
      h.send(REMOVE_ENTITY_EFFECT, removal, false);
      removal.setEffectType(MinecraftKey.withDefaultNamespace("slowness"));
      h.acknowledgeNext();
      assertEquals(0, data.potionEffectSpeedDuration);
    }
  }

  @Test
  void unrelatedEffectsLeaveMovementEffectsIntact() {
    Harness h = client(777);
    var data = h.user.meta().potions();
    data.potionEffectSpeedAmplifier(3);
    data.potionEffectSpeedDuration = 99;
    h.send(ENTITY_EFFECT, h.effect("haste", 4, 200), false);
    h.send(REMOVE_ENTITY_EFFECT, h.removal("haste"), false);
    h.acknowledgeNext();
    h.acknowledgeNext();
    assertEquals(3, data.potionEffectSpeedAmplifier());
    assertEquals(99, data.potionEffectSpeedDuration);
  }

  private Harness client(int protocol) {
    var world = FakeWorldFactory.createWorld((name, args) -> null);
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> id;
      case "getEntityId" -> 12;
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, protocol);
    UserRepository.manuallyRegisterUser(player, user);
    players.add(player);
    return new Harness(user);
  }

  private static final class Harness {
    private final User user;
    private final Queue<TypedPacketContract> pending = new ArrayDeque<>();

    private Harness(User user) {
      this.user = user;
    }

    private MutableEntityEffectPacket effect(String name, int amplifier, int duration) {
      MutableEntityEffectPacket packet = WirePackets.createPlayPacket(ENTITY_EFFECT, user.protocolVersion());
      packet.setEntityId(12);
      packet.setEffectType(MinecraftKey.withDefaultNamespace(name));
      packet.setEffectAmplifier(amplifier);
      packet.setEffectDuration(duration);
      return packet;
    }

    private MutableRemoveEntityEffectPacket removal(String name) {
      MutableRemoveEntityEffectPacket packet = WirePackets.createPlayPacket(REMOVE_ENTITY_EFFECT, user.protocolVersion());
      packet.setEntityId(12);
      packet.setEffectType(MinecraftKey.withDefaultNamespace(name));
      return packet;
    }

    private void send(PacketType.Server type, PacketContract packet, boolean cancelled) {
      PacketSendEvent event = new PacketSendEvent(user, type, packet);
      event.setCancelled(cancelled);
      List<TypedPacketContract> sent = PacketEventBus.dispatch(event);
      if (cancelled) {
        assertTrue(sent.isEmpty());
      } else {
        assertSame(packet, sent.get(0).contract());
        pending.addAll(sent.subList(1, sent.size()));
      }
    }

    private void acknowledgeNext() {
      assertFalse(pending.isEmpty(), "Expected a pending acknowledgement");
      var reply = NativeFeedbackTestSupport.acknowledgement(user, pending.remove());
      PacketEventBus.dispatch(reply);
      assertTrue(reply.isCancelled());
    }
  }
}
