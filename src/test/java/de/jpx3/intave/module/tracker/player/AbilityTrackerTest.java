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
import de.jpx3.intave.packet.contract.immutable.CameraPacket;
import de.jpx3.intave.packet.contract.mutable.MutableAbilityInPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.ability.outbound.AbilityOutWirePacket;
import de.jpx3.intave.packet.wire.world.gamestate.GameStateChangeWirePacket;
import de.jpx3.intave.registry.PacketTypeRegistries;
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

import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.CREATIVE;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.SURVIVAL;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.*;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static de.jpx3.intave.share.GameStateChangeType.CHANGE_GAME_MODE;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class AbilityTrackerTest {
  private NativeFeedbackTestSupport feedback;
  private final List<Player> players = new ArrayList<>();

  @BeforeEach
  void linkTracker() throws Exception {
    feedback = new NativeFeedbackTestSupport();
    feedback.linker.linkSubscriptionsIn(new AbilityTracker());
  }

  @AfterEach
  void unlinkTracker() throws Exception {
    players.forEach(UserRepository::unregisterUser);
    feedback.close();
  }

  @Test
  void respawnResetsCustomSpeedOnlyAfterFeedback() {
    for (int protocol : new int[]{VER_1_8, VER_1_21_4, VER_26_1_1}) {
      Harness h = client(protocol);
      h.respawn(false);
      assertEquals(0.2F, h.speed(), "In-flight movement still uses the old speed");

      h.acknowledgeAll();
      assertEquals(0.05F, h.speed(), "A new client player starts with default abilities: " + protocol);
    }
  }

  @Test
  void respawnDiscardsAnEarlierAbilitiesUpdate() {
    Harness h = client(VER_26_1_1);
    h.abilities(0.4F);
    h.respawn(false);
    assertEquals(0.2F, h.speed());

    h.acknowledgeNext();
    assertEquals(0.4F, h.speed());
    h.acknowledgeAll();
    assertEquals(0.05F, h.speed());
  }

  @Test
  void laterAbilitiesRestoreCustomSpeedAfterRespawn() {
    Harness h = client(VER_26_1_1);
    h.respawn(false);
    h.abilities(0.4F);
    assertEquals(0.2F, h.speed());

    h.acknowledgeNext();
    assertEquals(0.05F, h.speed());
    h.acknowledgeNext();
    assertEquals(0.4F, h.speed());
    assertTrue(h.pending.isEmpty());
  }

  @Test
  void cancelledRespawnKeepsCustomSpeed() {
    Harness h = client(VER_26_1_1);
    h.respawn(true);
    h.acknowledgeAll();
    assertEquals(0.2F, h.speed());
  }

  @Test
  void cameraUsesOneVarIntInEveryRegisteredProtocol() {
    for (int protocol : PacketTypeRegistries.clientbound().supportedProtocolVersions()) {
      CameraPacket camera = roundTrip(CLIENTBOUND, CAMERA, protocol, b -> b.writeByte(0xac).writeByte(0x02));
      assertEquals(300, camera.entityId());
    }
  }

  @Test
  void cameraFollowsTheAcknowledgedTarget() {
    Harness h = client(VER_1_21_4);
    CameraPacket other = roundTrip(CLIENTBOUND, CAMERA, h.user.protocolVersion(), b -> b.writeByte(0xac).writeByte(0x02));
    CameraPacket self = roundTrip(CLIENTBOUND, CAMERA, h.user.protocolVersion(), b -> b.writeByte(h.user.player().getEntityId()));
    var data = h.user.meta().abilities();
    h.send(CAMERA, other, true);
    assertFalse(data.hasViewEntity);
    h.send(CAMERA, other, false);
    h.send(CAMERA, self, false);
    assertFalse(data.hasViewEntity);
    h.acknowledgeNext();
    assertTrue(data.hasViewEntity);
    h.acknowledgeNext();
    assertFalse(data.hasViewEntity);
  }

  @Test
  void onlyGameModeChangesSetPendingAndAcknowledgedModes() {
    Harness h = client(VER_1_8);
    var data = h.user.meta().abilities();
    data.setGameMode(SURVIVAL);
    data.setPendingGameMode(SURVIVAL);
    var creative = new GameStateChangeWirePacket(CHANGE_GAME_MODE.id(), 0.75F);
    h.send(GAME_STATE_CHANGE, creative, true);
    h.send(GAME_STATE_CHANGE, new GameStateChangeWirePacket(0, 1F), false);
    assertFalse(data.inGameModeIncludePending(CREATIVE));
    assertTrue(h.pending.isEmpty());
    h.send(GAME_STATE_CHANGE, creative, false);
    assertTrue(data.inGameModeIncludePending(CREATIVE));
    assertTrue(data.inGameMode(SURVIVAL));
    h.acknowledgeNext();
    assertTrue(data.inGameMode(CREATIVE));
  }

  @Test
  void revokingFlightTracksTeleportsUntilFeedback() {
    Harness h = client(VER_26_1_1);
    var data = h.user.meta().abilities();
    var movement = h.user.meta().movement();
    data.setAllowFlying(true);
    movement.verifiedLastPositionX = 12.5;
    var revoke = new AbilityOutWirePacket(0, 0.08F, 0.15F);
    h.send(ABILITIES_OUT, revoke, true);
    assertEquals(0, movement.criticalFlyingDisallowStacks);
    h.send(ABILITIES_OUT, revoke, false);
    assertEquals(1, movement.criticalFlyingDisallowStacks);
    assertEquals(12.5, movement.criticalEnterPosX);
    assertTrue(data.allowFlying());
    PacketContract teleport = WirePackets.createPlayPacket(POSITION, h.user.protocolVersion());
    h.send(POSITION, teleport, true);
    assertFalse(movement.criticalFlyingDisallowWasTeleported);
    h.send(POSITION, teleport, false);
    assertTrue(movement.criticalFlyingDisallowWasTeleported);
    h.acknowledgeNext();
    assertFalse(data.allowFlying());
    assertEquals(0, movement.criticalFlyingDisallowStacks);
  }

  @Test
  void clientFlightRequestsKeepTheirTickBoundary() {
    Harness h = client(VER_1_8);
    var data = h.user.meta().abilities();
    data.setAllowFlying(false);
    h.requestFlight(true, false);
    assertFalse(data.acknowledgedFlying());
    data.setAllowFlying(true);
    h.requestFlight(true, true);
    assertTrue(data.acknowledgedFlying());
    assertFalse(data.flying());
    h.requestFlight(true, false);
    assertTrue(data.flying());
    h.requestFlight(false, false);
    assertFalse(data.acknowledgedFlying());
    assertTrue(data.disabledFlying);
    assertTrue(data.flying());
    data.tickComplete();
    assertFalse(data.flying());
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
      user.meta().abilities().setFlySpeed(0.2F);
    }

    private void respawn(boolean cancelled) {
      send(RESPAWN, WirePackets.createPlayPacket(RESPAWN, user.protocolVersion()), cancelled);
    }

    private void abilities(float speed) {
      send(ABILITIES_OUT, new AbilityOutWirePacket(6, speed, 0.1F), false);
    }

    private void requestFlight(boolean flying, boolean cancelled) {
      MutableAbilityInPacket packet = WirePackets.createPlayPacket(PacketType.Client.ABILITIES_IN, user.protocolVersion());
      packet.setRequestedFlying(flying);
      PacketReceiveEvent event = new PacketReceiveEvent(user, PacketType.Client.ABILITIES_IN, packet);
      event.setCancelled(cancelled);
      PacketEventBus.dispatch(event);
    }

    private void send(PacketType.Server type, PacketContract packet, boolean cancelled) {
      PacketSendEvent event = new PacketSendEvent(user, type, packet);
      event.setCancelled(cancelled);
      List<TypedPacketContract> sent = PacketEventBus.dispatch(event);
      if (cancelled) {
        assertTrue(sent.isEmpty());
        return;
      }
      assertSame(packet, sent.get(0).contract());
      pending.addAll(sent.subList(1, sent.size()));
    }

    private float speed() {
      return user.meta().abilities().flySpeed();
    }

    private void acknowledgeNext() {
      assertFalse(pending.isEmpty(), "Expected a pending packet acknowledgement");
      PacketReceiveEvent reply = NativeFeedbackTestSupport.acknowledgement(user, pending.remove());
      PacketEventBus.dispatch(reply);
      assertTrue(reply.isCancelled());
    }

    private void acknowledgeAll() {
      while (!pending.isEmpty()) acknowledgeNext();
    }
  }
}
