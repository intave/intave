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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.wire.entity.position.EntityPositionSyncPositionMoveRotationWirePacket;
import de.jpx3.intave.packet.wire.entity.position.EntityPositionWirePacket;
import de.jpx3.intave.packet.wire.player.ground.PlayerGroundWirePacket;
import de.jpx3.intave.packet.wire.player.position.PlayerPositionWirePacket;
import de.jpx3.intave.packet.wire.player.positionrotation.PlayerPositionRotationWirePacket;
import de.jpx3.intave.packet.wire.player.rotation.PlayerRotationWirePacket;
import de.jpx3.intave.packet.wire.vehicle.move.VehicleMoveWithoutGroundWirePacket;
import de.jpx3.intave.packet.wire.world.border.WorldBorderInitializeWirePacket;
import de.jpx3.intave.packet.wire.world.border.WorldBorderSetCenterWirePacket;
import de.jpx3.intave.packet.wire.world.border.WorldBorderSetSizeWirePacket;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Map;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class WirePacketCodecsTest {
	@Test
	void everyRegistrationMatchesItsDirectionStateTypeAndProtocol() throws ReflectiveOperationException {
		for (PacketDirection direction : PacketDirection.values()) {
			PacketIdRegistry<?> registry = registry(direction);
			for (int protocol : registry.supportedProtocolVersions()) {
				for (ConnectionState state : ConnectionState.values()) {
					for (Map.Entry<? extends PacketType, Integer> entry :
							registry.packetIds(state, protocol).entrySet()) {
						if (!WirePackets.packetIdKnown(
								direction, state, entry.getValue(), protocol)) continue;
						PacketContract packet =
								WirePackets.createPacket(
										direction, state, entry.getValue(), protocol);
						assertTrue(Modifier.isPublic(packet.getClass().getModifiers()));
						assertTrue(
								packet.getClass().getPackage().getName()
										.startsWith("de.jpx3.intave.packet.wire."));
						assertTrue(
								packet.appliesTo(state, entry.getKey(), protocol),
								entry.toString());
						assertTrue(packet.getClass().getMethod("appliesTo", ConnectionState.class, PacketType.class).isDefault());
						assertFalse(((Mutable) packet).couldHaveBeenMutated(), "Matching must not expose mutable packet fields");
					}
				}
			}
		}
	}

	@Test
	void contractsProvideMatchingWithoutAWireImplementation() {
		KeepAlivePacket keepAlive = () -> {
			throw new AssertionError("Matching must not read packet data");
		};
		for (ConnectionState state : ConnectionState.values()) {
			boolean expected = state == PLAY || state == ConnectionState.CONFIGURATION;
			assertEquals(expected, keepAlive.appliesTo(state, PacketType.Client.KEEP_ALIVE_IN));
			assertEquals(expected, keepAlive.appliesTo(state, PacketType.Server.KEEP_ALIVE_OUT));
			assertFalse(keepAlive.appliesTo(state, PacketType.Server.PING));
		}
		HeldItemSlotPacket heldSlot = () -> 7;
		assertTrue(heldSlot.appliesTo(PLAY, PacketType.Client.HELD_ITEM_SLOT_IN));
		assertTrue(heldSlot.appliesTo(PLAY, PacketType.Server.HELD_ITEM_SLOT_OUT));
		assertFalse(heldSlot.appliesTo(ConnectionState.LOGIN, PacketType.Client.HELD_ITEM_SLOT_IN));
	}

	@Test
	void sharedContractsKeepDistinctWireCodecs() {
		assertEquals(PlayerGroundWirePacket.class, WirePackets.createPlayPacket(PacketType.Client.FLYING, 768).getClass());
		assertEquals(PlayerPositionWirePacket.class, WirePackets.createPlayPacket(PacketType.Client.POSITION, 768).getClass());
		assertEquals(PlayerRotationWirePacket.class, WirePackets.createPlayPacket(PacketType.Client.LOOK, 768).getClass());
		assertEquals(PlayerPositionRotationWirePacket.class, WirePackets.createPlayPacket(PacketType.Client.POSITION_LOOK, 768).getClass());
		assertEquals(VehicleMoveWithoutGroundWirePacket.class, WirePackets.createPlayPacket(PacketType.Client.VEHICLE_MOVE, 768).getClass());
		assertEquals(EntityPositionWirePacket.class, WirePackets.createPlayPacket(PacketType.Server.ENTITY_TELEPORT, 768).getClass());
		assertEquals(EntityPositionSyncPositionMoveRotationWirePacket.class, WirePackets.createPlayPacket(PacketType.Server.ENTITY_POSITION_SYNC, 768).getClass());
		assertEquals(WorldBorderInitializeWirePacket.class, WirePackets.createPlayPacket(PacketType.Server.INITIALIZE_BORDER, 768).getClass());
		assertEquals(WorldBorderSetCenterWirePacket.class, WirePackets.createPlayPacket(PacketType.Server.SET_BORDER_CENTER, 768).getClass());
		assertEquals(WorldBorderSetSizeWirePacket.class, WirePackets.createPlayPacket(PacketType.Server.SET_BORDER_SIZE, 768).getClass());
	}

	@Test
	void nbtAndRegistryDependentPacketsAreNotRegistered() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			for (PacketType packetType : new PacketType[] {PacketType.Client.WINDOW_CLICK, PacketType.Client.SET_CREATIVE_SLOT}) {
				assertUnsupported(SERVERBOUND, packetType, protocol);
			}
			for (PacketType packetType : new PacketType[] {
						PacketType.Server.WINDOW_ITEMS,
						PacketType.Server.SET_SLOT,
						PacketType.Server.ENTITY_EQUIPMENT,
						PacketType.Server.OPEN_WINDOW,
						PacketType.Server.ENTITY_METADATA,
						PacketType.Server.ENTITY_EFFECT,
						PacketType.Server.REMOVE_ENTITY_EFFECT,
						PacketType.Server.BLOCK_ACTION,
						PacketType.Server.BLOCK_CHANGE,
						PacketType.Server.SPAWN_ENTITY
					}) {
				assertUnsupported(CLIENTBOUND, packetType, protocol);
			}
			if (protocol < 107) assertUnsupported(SERVERBOUND, PacketType.Client.USE_ITEM_ON, protocol);
			if (protocol >= 765) {
				assertUnsupported(CLIENTBOUND, PacketType.Server.EXPLOSION, protocol);
				assertUnsupported(CLIENTBOUND, PacketType.Server.TAB_COMPLETE_OUT, protocol);
				assertUnsupported(CLIENTBOUND, PacketType.Server.SCOREBOARD_OBJECTIVE, protocol);
			}
			if (protocol >= 775) assertUnsupported(CLIENTBOUND, PacketType.Server.UPDATE_TIME, protocol);
		}
	}

	@Test
	void theSameCanonicalNameUsesDirectionSpecificFields() {
		HeldItemSlotPacket received =
				roundTrip(SERVERBOUND, PacketType.Client.HELD_ITEM_SLOT_IN, 768, b -> b.writeShort(7));
		HeldItemSlotPacket sent =
				roundTrip(CLIENTBOUND, PacketType.Server.HELD_ITEM_SLOT_OUT, 768, b -> b.writeByte(7));
		assertEquals(7, received.slot());
		assertEquals(7, sent.slot());
	}

	@Test
	void jsonSuggestionsAndObjectivesRemainAvailableBeforeNbt() {
		TabCompleteResponsePacket suggestions =
				roundTrip(
						CLIENTBOUND,
						PacketType.Server.TAB_COMPLETE_OUT,
						764,
						b -> {
							b.writeBytes(new byte[] {9, 1, 2, 1});
							ByteBufStreamCodecs.VARINT_STRING.encode(b, "help");
							b.writeBoolean(true);
							ByteBufStreamCodecs.VARINT_STRING.encode(b, "{\"text\":\"Help\"}");
						});
		assertEquals("help", suggestions.suggestions().get(0));
		ScoreboardObjectivePacket objective =
				roundTrip(
						CLIENTBOUND,
						PacketType.Server.SCOREBOARD_OBJECTIVE,
						764,
						b -> {
							ByteBufStreamCodecs.VARINT_STRING.encode(b, "score");
							b.writeByte(0);
							ByteBufStreamCodecs.VARINT_STRING.encode(b, "{\"text\":\"Score\"}");
							b.writeByte(1);
						});
		assertEquals("{\"text\":\"Score\"}", objective.displayNameJson());
	}

	@Test
	void menuAndWindowIdsRetainTheirVersionSpecificWidths() {
		WindowClosePacket oldClose =
				roundTrip(SERVERBOUND, PacketType.Client.CLOSE_WINDOW, 767, b -> b.writeByte(255));
		WindowClosePacket newClose =
				roundTrip(
						SERVERBOUND,
						PacketType.Client.CLOSE_WINDOW,
						768,
						b -> ByteBufStreamCodecs.VARINT.encode(b, 300));
		assertEquals(255, oldClose.containerId());
		assertEquals(300, newClose.containerId());
		MenuButtonPacket oldButton =
				roundTrip(SERVERBOUND, PacketType.Client.ENCHANT_ITEM, 765, b -> b.writeByte(12).writeByte(3));
		MenuButtonPacket newButton =
				roundTrip(
						SERVERBOUND,
						PacketType.Client.ENCHANT_ITEM,
						766,
						b -> {
							ByteBufStreamCodecs.VARINT.encode(b, 300);
							ByteBufStreamCodecs.VARINT.encode(b, 400);
						});
		assertEquals(12, oldButton.containerId());
		assertEquals(400, newButton.button());
	}

	@Test
	void malformedCookieAndCollectionLengthsAreRejectedWithoutConsumingTheFrame() {
		ByteBuf input = Unpooled.buffer();
		try {
			ByteBufStreamCodecs.VARINT.encode(
					input, registry(SERVERBOUND).idOf(PLAY, PacketType.Client.COOKIE_RESPONSE, 768));
			ByteBufStreamCodecs.VARINT_STRING.encode(input, "minecraft:test");
			input.writeBoolean(true);
			ByteBufStreamCodecs.VARINT.encode(input, 5121);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(SERVERBOUND, PLAY, 768, input));
			assertEquals(0, input.readerIndex());
			input.clear();
			ByteBufStreamCodecs.VARINT.encode(
					input, registry(CLIENTBOUND).idOf(PLAY, PacketType.Server.ENTITY_DESTROY, 768));
			ByteBufStreamCodecs.VARINT.encode(input, -1);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(CLIENTBOUND, PLAY, 768, input));
		} finally {
			input.release();
		}
	}
}
