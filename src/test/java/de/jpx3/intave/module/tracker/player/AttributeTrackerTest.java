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
import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableEntityAttributesPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.module.feedback.NativeFeedbackTestSupport.acknowledgement;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.UPDATE_ATTRIBUTES;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class AttributeTrackerTest {
  @Test
  void appliesOnlyThePlayersAcknowledgedSnapshotsAcrossClientAndServerVersions() throws Exception {
    try (NativeFeedbackTestSupport feedback = new NativeFeedbackTestSupport()) {
      feedback.linker.linkSubscriptionsIn(new AttributeTracker());
      for (MinecraftVersion server : List.of(MinecraftVersions.VER1_8_0, MinecraftVersions.VER1_16_0, MinecraftVersions.VER26_3)) {
        MinecraftVersion.setCurrent(server);
        for (int protocol : new int[]{47, 755, 766, 767, 777}) {
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
          try {
            verifyUpdates(user, protocol);
          } finally {
            UserRepository.unregisterUser(player);
          }
        }
      }
    }
  }

  private static void verifyUpdates(User user, int protocol) {
    var abilities = user.meta().abilities();
    var movement = user.meta().movement();
    assertNull(abilities.findWireAttribute("plugin:movement_speed"));
    String speed = "generic.movementSpeed";
    double initialSpeed = abilities.attributeValue(speed);
    MutableEntityAttributesPacket boost = movementSpeed(protocol, true, 0.25);
    boost.setEntityId(15);
    assertEquals(1, dispatch(user, boost, false).size());
    boost.setEntityId(user.player().getEntityId());
    assertTrue(dispatch(user, boost, true).isEmpty());
    assertEquals(0, user.meta().connection().feedbackQueue().size());

    List<TypedPacketContract> first = dispatch(user, boost, false);
    // A later packet edit cannot change what the tracker already queued.
    boost.setAttributes(Collections.emptyList());
    List<TypedPacketContract> second = dispatch(user, movementSpeed(protocol, false, 0.4), false);
    assertEquals(2, first.size());
    assertEquals(2, second.size());
    assertEquals(initialSpeed, abilities.attributeValue(speed));
    assertFalse(movement.hasSprintSpeed);

    PacketEventBus.dispatch(acknowledgement(user, first.get(1)));
    assertEquals(0.325, abilities.attributeValue(speed), 1e-12);
    assertTrue(movement.hasSprintSpeed);
    PacketEventBus.dispatch(acknowledgement(user, second.get(1)));
    assertEquals(0.4, abilities.attributeValue(speed));
    assertFalse(movement.hasSprintSpeed);
    assertEquals(0, user.meta().connection().feedbackQueue().size());

    if (protocol == 755) {
      MutableEntityAttributesPacket health = roundTrip(CLIENTBOUND, UPDATE_ATTRIBUTES, protocol, out -> {
        VARINT.encode(out, 12);
        VARINT.encode(out, 2);
        VARINT_STRING.encode(out, "plugin:movement_speed");
        out.writeDouble(100).writeByte(0);
        VARINT_STRING.encode(out, "minecraft:generic.max_health");
        out.writeDouble(30).writeByte(0);
      });
      var sent = dispatch(user, health, false);
      assertEquals(20, abilities.maxHealth());
      PacketEventBus.dispatch(acknowledgement(user, sent.get(1)));
      assertEquals(30, abilities.maxHealth());
      assertEquals(0.4, abilities.attributeValue(speed));
    }
  }

  private static List<TypedPacketContract> dispatch(User user, MutableEntityAttributesPacket packet, boolean cancelled) {
    PacketSendEvent event = new PacketSendEvent(user, UPDATE_ATTRIBUTES, packet);
    event.setCancelled(cancelled);
    return PacketEventBus.dispatch(event);
  }

  private static MutableEntityAttributesPacket movementSpeed(int protocol, boolean sprint, double base) {
    return roundTrip(CLIENTBOUND, UPDATE_ATTRIBUTES, protocol, out -> {
      VARINT.encode(out, 12);
      if (protocol < 755) out.writeInt(1);
      else VARINT.encode(out, 1);
      if (protocol < 735) VARINT_STRING.encode(out, "generic.movementSpeed");
      else if (protocol < 766) VARINT_STRING.encode(out, "minecraft:generic.movement_speed");
      else VARINT.encode(out, protocol == 766 ? 17 : protocol == 767 ? 21 : 26);
      out.writeDouble(base);
      VARINT.encode(out, sprint ? 1 : 0);
      if (sprint) {
        if (protocol < 767) out.writeLong(0x662a6b8dda3e4c1cL).writeLong(0x881396ea6097278dL);
        else VARINT_STRING.encode(out, "minecraft:sprinting");
        out.writeDouble(0.3).writeByte(2);
      }
    });
  }
}
