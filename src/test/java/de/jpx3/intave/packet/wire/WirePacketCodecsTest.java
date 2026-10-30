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
	void everyRegistrationMatchesItsDirectionStateNameAndProtocol() {
		for (PacketDirection direction : PacketDirection.values()) {
			PacketIdRegistry registry = registry(direction);
			for (int protocol : registry.supportedProtocolVersions()) {
				for (ConnectionState state : ConnectionState.values()) {
					for (Map.Entry<String, Integer> entry :
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
			for (String name : new String[] {"WINDOW_CLICK", "SET_CREATIVE_SLOT"}) {
				assertUnsupported(SERVERBOUND, name, protocol);
			}
			for (String name :
					new String[] {
						"WINDOW_ITEMS",
						"SET_SLOT",
						"ENTITY_EQUIPMENT",
						"OPEN_WINDOW",
						"ENTITY_METADATA",
						"ENTITY_EFFECT",
						"REMOVE_ENTITY_EFFECT",
						"BLOCK_ACTION",
						"BLOCK_CHANGE",
						"SPAWN_ENTITY"
					}) {
				assertUnsupported(CLIENTBOUND, name, protocol);
			}
			if (protocol < 107) assertUnsupported(SERVERBOUND, "USE_ITEM_ON", protocol);
			if (protocol >= 765) {
				assertUnsupported(CLIENTBOUND, "EXPLOSION", protocol);
				assertUnsupported(CLIENTBOUND, "TAB_COMPLETE", protocol);
				assertUnsupported(CLIENTBOUND, "SCOREBOARD_OBJECTIVE", protocol);
			}
			if (protocol >= 775) assertUnsupported(CLIENTBOUND, "UPDATE_TIME", protocol);
		}
	}

	@Test
	void theSameCanonicalNameUsesDirectionSpecificFields() {
		HeldItemSlotPacket received =
				roundTrip(SERVERBOUND, "HELD_ITEM_SLOT", 768, b -> b.writeShort(7));
		HeldItemSlotPacket sent =
				roundTrip(CLIENTBOUND, "HELD_ITEM_SLOT", 768, b -> b.writeByte(7));
		assertEquals(7, received.slot());
		assertEquals(7, sent.slot());
	}

	@Test
	void jsonSuggestionsAndObjectivesRemainAvailableBeforeNbt() {
		TabCompleteResponsePacket suggestions =
				roundTrip(
						CLIENTBOUND,
						"TAB_COMPLETE",
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
						"SCOREBOARD_OBJECTIVE",
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
				roundTrip(SERVERBOUND, "CLOSE_WINDOW", 767, b -> b.writeByte(255));
		WindowClosePacket newClose =
				roundTrip(
						SERVERBOUND,
						"CLOSE_WINDOW",
						768,
						b -> ByteBufStreamCodecs.VARINT.encode(b, 300));
		assertEquals(255, oldClose.containerId());
		assertEquals(300, newClose.containerId());
		MenuButtonPacket oldButton =
				roundTrip(SERVERBOUND, "ENCHANT_ITEM", 765, b -> b.writeByte(12).writeByte(3));
		MenuButtonPacket newButton =
				roundTrip(
						SERVERBOUND,
						"ENCHANT_ITEM",
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
					input, registry(SERVERBOUND).idOf(PLAY, "COOKIE_RESPONSE", 768));
			ByteBufStreamCodecs.VARINT_STRING.encode(input, "minecraft:test");
			input.writeBoolean(true);
			ByteBufStreamCodecs.VARINT.encode(input, 5121);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(SERVERBOUND, PLAY, 768, input));
			assertEquals(0, input.readerIndex());
			input.clear();
			ByteBufStreamCodecs.VARINT.encode(
					input, registry(CLIENTBOUND).idOf(PLAY, "ENTITY_DESTROY", 768));
			ByteBufStreamCodecs.VARINT.encode(input, -1);
			assertThrows(
					IllegalArgumentException.class,
					() -> WirePackets.decodePacket(CLIENTBOUND, PLAY, 768, input));
		} finally {
			input.release();
		}
	}
}
