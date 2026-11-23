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

import ac.intave.samples.event.EventSink;
import ac.intave.samples.event.PlayerVitalsEvent;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableExperiencePacket;
import de.jpx3.intave.packet.contract.mutable.MutableUpdateHealthPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.EXPERIENCE;
import static de.jpx3.intave.packet.contract.PacketType.Server.UPDATE_HEALTH;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerVitalsTrackerTest {
  @Test
  void invalidatesCachedMaxHealthWhenTheAttributeChanges() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );

    assertEquals(20.0, abilities.maxHealth());
    abilities.modifyBaseValue("generic.maxHealth", 24.0);
    assertEquals(24.0, abilities.maxHealth());
  }

  @Test
  void invalidatesCachedArmorWhenTheAttributesChange() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );

    assertEquals(0.0, abilities.armor());
    assertEquals(0.0, abilities.armorToughness());
    abilities.modifyBaseValue("generic.armor", 8.0);
    abilities.modifyBaseValue("generic.armorToughness", 3.0);
    assertEquals(8.0, abilities.armor());
    assertEquals(3.0, abilities.armorToughness());
  }

  @Test
  void combinesAcknowledgedHealthAndExperienceUpdates() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    AbilityMetadata abilities = new AbilityMetadata(
      FakePlayerFactory.createPlayer((ignored, arguments) -> null)
    );
    abilities.synchronizeExperience(0.25F, 4, 80);

    abilities.synchronizeVitals(13.5F, 7, 1.5F);
    PlayerVitalsEvent afterHealth = PlayerVitalsTracker.event(abilities);
    assertEquals(13.5F, afterHealth.health());
    assertEquals(7, afterHealth.foodLevel());
    assertEquals(1.5F, afterHealth.saturation());
    assertEquals(0.25F, afterHealth.xpProgress());
    assertEquals(4, afterHealth.xpLevel());
    assertEquals(80, afterHealth.totalXp());
    assertEquals(20.0, afterHealth.maxHealth());
    assertEquals(0.0, afterHealth.armor());
    assertEquals(0.0, afterHealth.armorToughness());
    assertNull(afterHealth.absorption());
    assertNull(afterHealth.exhaustion());

    abilities.synchronizeExperience(0.75F, 12, 325);
    PlayerVitalsEvent afterExperience = PlayerVitalsTracker.event(abilities);
    assertEquals(13.5F, afterExperience.health());
    assertEquals(7, afterExperience.foodLevel());
    assertEquals(1.5F, afterExperience.saturation());
    assertEquals(0.75F, afterExperience.xpProgress());
    assertEquals(12, afterExperience.xpLevel());
    assertEquals(325, afterExperience.totalXp());
  }

  @Nested
  class PacketSubscriptions {
    private NativeFeedbackTestSupport feedback;
    private final List<Player> players = new ArrayList<>();
    private final List<PlayerVitalsEvent> emitted = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void linkTrackerAndRecordEvents() throws Exception {
      feedback = new NativeFeedbackTestSupport();
      feedback.linker.linkSubscriptionsIn(new PlayerVitalsTracker());
      Nayoro nayoro = new Nayoro();
      EventSink sink = new EventSink() {
        @Override
        public void visit(PlayerVitalsEvent event) {
          emitted.add(event);
        }

        @Override
        public String name() {
          return "vitals-test";
        }
      };
      Field sinks = Nayoro.class.getDeclaredField("eventSinks");
      sinks.setAccessible(true);
      sinks.set(nayoro, UserLocal.withInitial(() -> Collections.singleton(sink)));
      Field poolField = Modules.class.getDeclaredField("pool");
      poolField.setAccessible(true);
      Object pool = poolField.get(null);
      Field mappings = pool.getClass().getDeclaredField("moduleClassMappings");
      mappings.setAccessible(true);
      ((Map<Class<? extends Module>, Module>) mappings.get(pool)).put(Nayoro.class, nayoro);
    }

    @AfterEach
    void unlinkTracker() throws Exception {
      players.forEach(UserRepository::unregisterUser);
      feedback.close();
    }

    @Test
    void updatesAndEmitsSentValuesOnlyAfterEachAcknowledgement() {
      for (int protocol : new int[]{47, 755, 777}) {
        User user = client(protocol);
        var abilities = user.meta().abilities();
        var movement = user.meta().movement();
        MutableUpdateHealthPacket health = health(user, 13.5F, 7, 1.5F);
        MutableExperiencePacket experience = experience(user, 0.75F, 12, 325);
        TypedPacketContract healthAck = send(user, UPDATE_HEALTH, health, false).get(1);
        TypedPacketContract experienceAck = send(user, EXPERIENCE, experience, false).get(1);
        health.setHealth(1);
        health.setFoodLevel(0);
        health.setSaturation(0);
        experience.setProgress(0);
        experience.setLevel(0);
        experience.setTotalExperience(0);
        assertEquals(20, abilities.health);
        assertEquals(20, abilities.foodLevel);
        assertEquals(4, abilities.experienceLevel);
        assertTrue(movement.isSprinting());
        assertTrue(emitted.isEmpty());

        acknowledge(user, healthAck);
        assertEquals(13.5F, abilities.health);
        assertEquals(7, abilities.foodLevel);
        assertEquals(1.5F, abilities.saturation);
        assertTrue(movement.isSprinting(), "Food above six must preserve sprinting");
        assertEquals(1, emitted.size());
        PlayerVitalsEvent first = emitted.get(0);
        assertEquals(13.5F, first.health());
        assertEquals(7, first.foodLevel());
        assertEquals(1.5F, first.saturation());
        assertEquals(0.25F, first.xpProgress());
        assertEquals(4, first.xpLevel());
        assertEquals(80, first.totalXp());

        acknowledge(user, experienceAck);
        assertEquals(0.75F, abilities.experienceProgress);
        assertEquals(12, abilities.experienceLevel);
        assertEquals(325, abilities.totalExperience);
        assertEquals(2, emitted.size());
        PlayerVitalsEvent second = emitted.get(1);
        assertEquals(13.5F, second.health());
        assertEquals(7, second.foodLevel());
        assertEquals(1.5F, second.saturation());
        assertEquals(0.75F, second.xpProgress());
        assertEquals(12, second.xpLevel());
        assertEquals(325, second.totalXp());
        assertEquals(4, first.xpLevel(), "Earlier emitted snapshots remain unchanged");
        assertEquals(0, user.meta().connection().feedbackQueue().size());
      }
    }

    @Test
    void lowFoodStopsSprintingAtAcknowledgementAndRefillingDoesNotRestartIt() {
      for (int protocol : new int[]{47, 755, 777}) {
        for (int food : new int[]{6, 0}) {
          User user = client(protocol);
          var movement = user.meta().movement();
          TypedPacketContract hungry = send(user, UPDATE_HEALTH, health(user, 18, food, 0), false).get(1);
          TypedPacketContract refilled = send(user, UPDATE_HEALTH, health(user, 20, 20, 5), false).get(1);
          assertTrue(movement.isSprinting());
          acknowledge(user, hungry);
          assertFalse(movement.isSprinting());
          assertEquals(food, emitted.get(0).foodLevel());
          acknowledge(user, refilled);
          assertFalse(movement.isSprinting());
          assertEquals(20, emitted.get(1).foodLevel());
        }
      }
    }

    @Test
    void cancelledPacketsDoNotQueueFeedbackOrEmitVitals() {
      for (int protocol : new int[]{47, 755, 777}) {
        User user = client(protocol);
        assertTrue(send(user, UPDATE_HEALTH, health(user, 1, 0, 0), true).isEmpty());
        assertTrue(send(user, EXPERIENCE, experience(user, 0.75F, 12, 325), true).isEmpty());
        var abilities = user.meta().abilities();
        assertEquals(20, abilities.health);
        assertEquals(20, abilities.foodLevel);
        assertEquals(5, abilities.saturation);
        assertEquals(0.25F, abilities.experienceProgress);
        assertEquals(4, abilities.experienceLevel);
        assertEquals(80, abilities.totalExperience);
        assertTrue(user.meta().movement().isSprinting());
        assertEquals(0, user.meta().connection().feedbackQueue().size());
        assertTrue(emitted.isEmpty());
      }
    }

    private User client(int protocol) {
      emitted.clear();
      var world = FakeWorldFactory.createWorld((name, args) -> null);
      UUID id = UUID.randomUUID();
      var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
        case "getWorld" -> world;
        case "getLocation" -> new Location(world, 0, 64, 0);
        case "getUniqueId" -> id;
        default -> null;
      });
      User user = UserFactory.createTestUserFor(player, protocol);
      UserRepository.manuallyRegisterUser(player, user);
      players.add(player);
      user.meta().abilities().synchronizeVitals(20, 20, 5);
      user.meta().abilities().synchronizeExperience(0.25F, 4, 80);
      user.meta().movement().setSprinting(true);
      return user;
    }

    private MutableUpdateHealthPacket health(User user, float health, int food, float saturation) {
      return roundTrip(CLIENTBOUND, UPDATE_HEALTH, user.protocolVersion(), b -> {
        b.writeFloat(health);
        VARINT.encode(b, food);
        b.writeFloat(saturation);
      });
    }

    private MutableExperiencePacket experience(User user, float progress, int level, int total) {
      return roundTrip(CLIENTBOUND, EXPERIENCE, user.protocolVersion(), b -> {
        b.writeFloat(progress);
        VARINT.encode(b, level);
        VARINT.encode(b, total);
      });
    }

    private List<TypedPacketContract> send(User user, PacketType.Server type, PacketContract packet, boolean cancelled) {
      PacketSendEvent event = new PacketSendEvent(user, type, packet);
      event.setCancelled(cancelled);
      List<TypedPacketContract> sent = PacketEventBus.dispatch(event);
      if (!cancelled) {
        assertEquals(2, sent.size());
        assertSame(packet, sent.get(0).contract());
      }
      return sent;
    }

    private void acknowledge(User user, TypedPacketContract packet) {
      var reply = NativeFeedbackTestSupport.acknowledgement(user, packet);
      PacketEventBus.dispatch(reply);
      assertTrue(reply.isCancelled());
    }
  }
}
