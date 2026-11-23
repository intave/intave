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

import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.module.feedback.NativeFeedbackTestSupport;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.mutable.MutableEntityMetadataPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.ENTITY_METADATA;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class MetadataTrackerTest {
	private NativeFeedbackTestSupport feedback;
	private final List<Player> players = new ArrayList<>();

	@BeforeEach
	void linkTracker() throws Exception {
		feedback = new NativeFeedbackTestSupport();
		feedback.linker.linkSubscriptionsIn(new MetadataTracker());
	}

	@AfterEach
	void unlinkTracker() throws Exception {
		players.forEach(UserRepository::unregisterUser);
		feedback.close();
	}

	@Test
	void appliesSentGlidingPoseAndItemFlagsOnlyAfterAcknowledgement() {
		for (int protocol : new int[]{47,107,210,477,573,755,761,766,773,777}) {
			User user = client(protocol, Material.RED_BED);
			var movement = user.meta().movement();
			var inventory = user.meta().inventory();
			inventory.setHandActive(true);
			var packet = packet(user);
			packet.setEntityFlags((byte) 0x80);
			if (protocol >= 107) packet.setLivingFlags((byte) 1);
			if (protocol >= 477) packet.setPose(Pose.CROUCHING);
			TypedPacketContract ack = send(user, packet, false).get(1);
			assertFalse(movement.gliding);
			assertEquals(Pose.STANDING, movement.pose());

			packet.setEntityFlags((byte) 0);
			if (protocol >= 107) packet.setLivingFlags((byte) 0);
			if (protocol >= 477) packet.setPose(Pose.SWIMMING);
			acknowledge(user, ack);
			assertEquals(protocol >= 107, movement.gliding);
			assertEquals(protocol >= 477 ? Pose.CROUCHING : Pose.STANDING, movement.pose());
			assertTrue(inventory.handActive());
			if (protocol >= 107) {
				var stop = packet(user);
				stop.setLivingFlags((byte) 0);
				acknowledge(user, send(user, stop, false).get(1));
				assertFalse(inventory.handActive(), "The acknowledged start must confirm this use sequence");
			}
		}
	}

	@Test
	void repeatedUpdatesUseTheLastSentValuesAfterAcknowledgement() {
		User user = client(776, Material.RED_BED);
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, 776, out -> {
			VARINT.encode(out, 12);
			out.writeByte(0); VARINT.encode(out, 0); out.writeByte(0);
			out.writeByte(6); VARINT.encode(out, 20); VARINT.encode(out, 0); // Standing.
			out.writeByte(15); VARINT.encode(out, 0); out.writeByte(0);
			out.writeByte(0); VARINT.encode(out, 0); out.writeByte(0x80);
			out.writeByte(6); VARINT.encode(out, 20); VARINT.encode(out, 5); // Crouching.
			out.writeByte(15); VARINT.encode(out, 0); out.writeByte(4);
			out.writeByte(255);
		});
		var ack = send(user, packet, false).get(1);
		assertFalse(user.meta().movement().gliding);
		assertEquals(Pose.STANDING, user.meta().movement().pose());
		packet.setEntityFlags((byte) 0);
		packet.setPose(Pose.STANDING);
		acknowledge(user, ack);
		assertTrue(user.meta().movement().gliding);
		assertEquals(Pose.CROUCHING, user.meta().movement().pose());
	}

	@Test
	void sparseUpdatesPreserveSleepingStateAndExplicitClearWaitsForAcknowledgement() {
		for (int protocol : new int[]{477,573,755,777}) {
			User user = client(protocol, Material.RED_BED);
			var movement = user.meta().movement();
			var bed = new BlockPosition(10, 64, -5);
			var sleeping = packet(user);
			sleeping.setBedPosition(bed);
			sleeping.setPose(Pose.SLEEPING);
			var sleepAck = send(user, sleeping, false).get(1);
			sleeping.clearBedPosition();
			assertNull(movement.sleepingBedPosition);
			acknowledge(user, sleepAck);
			assertEquals(bed, movement.sleepingBedPosition);
			assertEquals(10.5, movement.position().getX());
			assertEquals(64.6875, movement.position().getY());
			assertEquals(-4.5, movement.position().getZ());
			assertEquals(Pose.SLEEPING, movement.pose());

			var sparse = packet(user);
			sparse.setHealth(10);
			acknowledge(user, send(user, sparse, false).get(1));
			assertEquals(bed, movement.sleepingBedPosition);
			assertEquals(Pose.SLEEPING, movement.pose());

			var wake = packet(user);
			wake.clearBedPosition();
			wake.setPose(Pose.STANDING);
			var wakeAck = send(user, wake, false).get(1);
			assertEquals(bed, movement.sleepingBedPosition);
			acknowledge(user, wakeAck);
			assertNull(movement.sleepingBedPosition);
			assertEquals(Pose.STANDING, movement.pose());
		}
	}

	@Test
	void strawBedOffsetsFollowTheReceivingClientProtocol() {
		for (int protocol : new int[]{776,777}) {
			User user = client(protocol, Material.valueOf("STRAW_BED"));
			var packet = packet(user);
			packet.setBedPosition(new BlockPosition(10, 64, -5));
			acknowledge(user, send(user, packet, false).get(1));
			assertEquals(protocol == 777 ? 64.375 : 64.6875, user.meta().movement().position().getY());
		}
	}

	@Test
	void cancelledAndOtherEntityPacketsDoNotScheduleFeedback() {
		User user = client(777, Material.RED_BED);
		var packet = packet(user);
		packet.setEntityFlags((byte) 0x80);
		packet.setPose(Pose.SLEEPING);
		assertTrue(send(user, packet, true).isEmpty());
		packet.setEntityId(42);
		assertEquals(1, send(user, packet, false).size());
		assertFalse(user.meta().movement().gliding);
		assertEquals(Pose.STANDING, user.meta().movement().pose());
	}

	@Test
	void oldAcknowledgementsCannotStopANewerItemUseSequence() {
		User user = client(777, Material.RED_BED);
		var inventory = user.meta().inventory();
		inventory.setHandActive(true);
		var start = packet(user);
		start.setLivingFlags((byte) 1);
		acknowledge(user, send(user, start, false).get(1));
		var stop = packet(user);
		stop.setLivingFlags((byte) 0);
		var stopAck = send(user, stop, false).get(1);
		inventory.setHandActive(true); // New use after the stop packet was sent.
		inventory.applyClientItemUseFlags(inventory.activeItemUseSequence(), true);
		acknowledge(user, stopAck);
		assertTrue(inventory.handActive());
		acknowledge(user, send(user, stop, false).get(1));
		assertFalse(inventory.handActive());
	}

	private User client(int protocol, Material bed) {
		var world = FakeWorldFactory.createWorld((name, args) -> null);
		UUID id = UUID.randomUUID();
		var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
			case "getWorld" -> world;
			case "getLocation" -> new Location(world, 0, 64, 0);
			case "getUniqueId" -> id;
			case "getEntityId" -> 12;
			default -> null;
		});
		BlockCache cache = (BlockCache) Proxy.newProxyInstance(BlockCache.class.getClassLoader(), new Class[]{BlockCache.class},
			(proxy, method, args) -> {
				if (method.getName().equals("peekStateAt")) return new BlockCacheEntry(null, null, bed, 0);
				throw new UnsupportedOperationException(method.getName());
			});
		User user = UserFactory.createTestUserFor(player, (ignored, method) -> switch (method) {
			case "protocolVersion" -> protocol;
			case "blockCache" -> cache;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
		players.add(player);
		return user;
	}

	private MutableEntityMetadataPacket packet(User user) {
		MutableEntityMetadataPacket packet = WirePackets.createPlayPacket(ENTITY_METADATA, user.protocolVersion());
		packet.setEntityId(12);
		return packet;
	}

	private List<TypedPacketContract> send(User user, MutableEntityMetadataPacket packet, boolean cancelled) {
		var event = new PacketSendEvent(user, ENTITY_METADATA, packet);
		event.setCancelled(cancelled);
		return PacketEventBus.dispatch(event);
	}

	private void acknowledge(User user, TypedPacketContract packet) {
		var reply = NativeFeedbackTestSupport.acknowledgement(user, packet);
		PacketEventBus.dispatch(reply);
		assertTrue(reply.isCancelled());
	}
}

