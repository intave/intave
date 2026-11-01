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
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.*;
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
	void everyRegistrationMatchesItsDirectionStateTypeAndProtocol() {
		for (PacketDirection direction : PacketDirection.values()) {
			PacketIdRegistry<?> registry = registry(direction);
			for (int protocol : registry.supportedProtocolVersions()) {
				for (ConnectionState state : ConnectionState.values()) {
					for (Map.Entry<? extends PacketId, Integer> entry :
							registry.packetIds(state, protocol).entrySet()) {
						if (!WirePackets.packetIdKnown(
								direction, state, entry.getValue(), protocol)) continue;
						Packet packet =
								WirePackets.createPacket(
										direction, state, entry.getValue(), protocol);
						assertTrue(Modifier.isPublic(packet.getClass().getModifiers()));
						assertTrue(
								packet.getClass().getPackage().getName()
										.startsWith("de.jpx3.intave.packet.wire."));
						assertTrue(
								packet.appliesTo(state, entry.getKey(), protocol),
								entry.toString());
					}
				}
			}
		}
	}

	@Test
	void nbtAndRegistryDependentPacketsAreNotRegistered() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			for (PacketId packetType : new PacketId[] {PacketId.Client.WINDOW_CLICK, PacketId.Client.SET_CREATIVE_SLOT}) {
				assertUnsupported(SERVERBOUND, packetType, protocol);
			}
			for (PacketId packetType : new PacketId[] {
						PacketId.Server.WINDOW_ITEMS,
						PacketId.Server.SET_SLOT,
						PacketId.Server.ENTITY_EQUIPMENT,
						PacketId.Server.OPEN_WINDOW,
						PacketId.Server.ENTITY_METADATA,
						PacketId.Server.ENTITY_EFFECT,
						PacketId.Server.REMOVE_ENTITY_EFFECT,
						PacketId.Server.BLOCK_ACTION,
						PacketId.Server.BLOCK_CHANGE,
						PacketId.Server.SPAWN_ENTITY
					}) {
				assertUnsupported(CLIENTBOUND, packetType, protocol);
			}
			if (protocol < 107) assertUnsupported(SERVERBOUND, PacketId.Client.USE_ITEM_ON, protocol);
			if (protocol >= 765) {
				assertUnsupported(CLIENTBOUND, PacketId.Server.EXPLOSION, protocol);
				assertUnsupported(CLIENTBOUND, PacketId.Server.TAB_COMPLETE_OUT, protocol);
				assertUnsupported(CLIENTBOUND, PacketId.Server.SCOREBOARD_OBJECTIVE, protocol);
			}
			if (protocol >= 775) assertUnsupported(CLIENTBOUND, PacketId.Server.UPDATE_TIME, protocol);
		}
	}

	@Test
	void theSameCanonicalNameUsesDirectionSpecificFields() {
		HeldItemSlotPacket received =
				roundTrip(SERVERBOUND, PacketId.Client.HELD_ITEM_SLOT_IN, 768, b -> b.writeShort(7));
		HeldItemSlotPacket sent =
				roundTrip(CLIENTBOUND, PacketId.Server.HELD_ITEM_SLOT_OUT, 768, b -> b.writeByte(7));
		assertEquals(7, received.slot());
		assertEquals(7, sent.slot());
	}

	@Test
	void jsonSuggestionsAndObjectivesRemainAvailableBeforeNbt() {
		TabCompleteResponsePacket suggestions =
				roundTrip(
						CLIENTBOUND,
						PacketId.Server.TAB_COMPLETE_OUT,
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
						PacketId.Server.SCOREBOARD_OBJECTIVE,
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
				roundTrip(SERVERBOUND, PacketId.Client.CLOSE_WINDOW, 767, b -> b.writeByte(255));
		WindowClosePacket newClose =
				roundTrip(
						SERVERBOUND,
						PacketId.Client.CLOSE_WINDOW,
						768,
						b -> ByteBufStreamCodecs.VARINT.encode(b, 300));
		assertEquals(255, oldClose.containerId());
		assertEquals(300, newClose.containerId());
		MenuButtonPacket oldButton =
				roundTrip(SERVERBOUND, PacketId.Client.ENCHANT_ITEM, 765, b -> b.writeByte(12).writeByte(3));
		MenuButtonPacket newButton =
				roundTrip(
						SERVERBOUND,
						PacketId.Client.ENCHANT_ITEM,
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
					input, registry(SERVERBOUND).idOf(PLAY, PacketId.Client.COOKIE_RESPONSE, 768));
			ByteBufStreamCodecs.VARINT_STRING.encode(input, "minecraft:test");
			input.writeBoolean(true);
			ByteBufStreamCodecs.VARINT.encode(input, 5121);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(SERVERBOUND, PLAY, 768, input));
			assertEquals(0, input.readerIndex());
			input.clear();
			ByteBufStreamCodecs.VARINT.encode(
					input, registry(CLIENTBOUND).idOf(PLAY, PacketId.Server.ENTITY_DESTROY, 768));
			ByteBufStreamCodecs.VARINT.encode(input, -1);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(CLIENTBOUND, PLAY, 768, input));
		} finally {
			input.release();
		}
	}
}
