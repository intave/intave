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

package de.jpx3.intave.packet.wire.window;

import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.immutable.WindowClickPacket;
import de.jpx3.intave.packet.contract.mutable.MutableWindowClickPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.WINDOW_CLICK;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class LegacyWindowClickWirePacketTest {
	private static final MinecraftKey STONE = MinecraftKey.withDefaultNamespace("stone");

	@Test
	void exposesTheClickedStackAndKeepsAbsentPredictionsDistinctFromAnEmptyStack() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			if (protocol >= 755) continue;
			MutableWindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
				header(input, protocol);
				item(input, protocol, ItemRegistry.forProtocol(protocol).idOf(STONE), 3);
			});
			assertEquals(STONE, packet.clickedItem().type());
			assertEquals(3, packet.clickedItem().amount());
			assertTrue(packet.hasClickedItem());
			assertFalse(packet.hasSlotPredictions());
			assertFalse(packet.hasCarriedItem());
			assertNull(packet.carriedItem());
			assertTrue(packet.itemMap().isEmpty());
			assertFalse(packet.couldHaveBeenMutated());
			packet.clickedItem().setAmount(7);
			assertEquals(3, packet.clickedItem().amount());
			assertFalse(packet.couldHaveBeenMutated());
			packet.clearClickedItem();
			assertTrue(packet.couldHaveBeenMutated());
			assertTrue(packet.hasClickedItem());
			assertNull(packet.clickedItem());
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(SERVERBOUND, PLAY, WINDOW_CLICK, protocol, packet, output);
				WindowClickPacket empty = WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, output);
				assertTrue(empty.hasClickedItem());
				assertNull(empty.clickedItem());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void bindsRegistryLookupsToTheActualProtocolEvenWhenTheWireLayoutIsIdentical() {
		for (int protocol : new int[]{393, 401}) {
			WindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
				header(input, protocol);
				item(input, protocol, protocol == 393 ? 764 : 769, 1);
			});
			assertEquals(MinecraftKey.withDefaultNamespace("totem_of_undying"), packet.clickedItem().type());
		}
	}

	@Test
	void versionedCodecsUseTheCorrectItemTableForEveryRegisteredItem() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			if (protocol >= 755) continue;
			ItemRegistry.forProtocol(protocol).itemIds().forEach((type, id) -> {
				if (type.equals(MinecraftKey.withDefaultNamespace("air"))) return;
				WindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
					header(input, protocol);
					item(input, protocol, id, 1);
				});
				assertEquals(type, packet.clickedItem().type(), "item " + id + " in protocol " + protocol);
			});
		}
	}

	@Test
	void versionedPacketsAcceptStacksFromProtocolsSharingTheirRegistriesAndLayout() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			if (protocol >= 755) continue;
			ByteBuf output = Unpooled.buffer();
			try {
				ItemStack stack = new ItemStack(STONE, 1, protocol);
				stack.setAmount(7);
				MutableWindowClickPacket packet = WirePackets.createPlayPacket(WINDOW_CLICK, protocol);
				packet.setClickedItem(stack);
				WirePackets.encodePacket(SERVERBOUND, PLAY, WINDOW_CLICK, protocol, packet, output);
				WindowClickPacket decoded = WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, output);
				assertEquals(STONE, decoded.clickedItem().type());
				assertEquals(7, decoded.clickedItem().amount());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void explicitStackReplacementCopiesInputAndReencodesEdits() {
		for (int protocol : new int[]{47, 393, 401, 404, 754}) {
			MutableWindowClickPacket packet = WirePackets.createPlayPacket(WINDOW_CLICK, protocol);
			ItemStack stack = new ItemStack(STONE, 7, protocol);
			if (protocol < 393) stack.setLegacyData((short) 5);
			packet.setClickedItem(stack);
			stack.setAmount(12);
			assertEquals(7, packet.clickedItem().amount());
			packet.setSlot(-999);
			packet.setRevision(-32768);
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(SERVERBOUND, PLAY, WINDOW_CLICK, protocol, packet, output);
				WindowClickPacket decoded = WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, output);
				assertEquals(7, decoded.clickedItem().amount());
				assertEquals(-999, decoded.slot());
				assertEquals(-32768, decoded.revision().getAsInt());
				assertEquals(protocol < 393 ? Short.valueOf((short) 5) : null, decoded.clickedItem().legacyData());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void zeroCountItemsAreEmptyButTheirOriginalPayloadSurvivesHeaderEdits() {
		for (int protocol : new int[]{47, 393, 404, 754}) {
			ByteBuf frame = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
				int headerStart = frame.writerIndex();
				header(frame, protocol);
				item(frame, protocol, ItemRegistry.forProtocol(protocol).idOf(STONE), 0);
				MutableWindowClickPacket packet = WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame);
				assertNull(packet.clickedItem());
				packet.setContainerId(5);
				WirePackets.encodePacket(SERVERBOUND, PLAY, WINDOW_CLICK, protocol, packet, output);
				frame.setByte(headerStart, 5);
				assertEquals(frame, output);
			} finally {
				frame.release();
				output.release();
			}
		}
	}

	@Test
	void malformedAndTrailingItemDataDoNotConsumeTheFrame() {
		for (int protocol : new int[]{47, 393, 401, 404, 754}) {
			for (int malformed : new int[]{0, 1, 2}) {
				ByteBuf frame = Unpooled.buffer();
				try {
					VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
					header(frame, protocol);
					if (malformed == 0) {
						item(frame, protocol, 32767, 1);
					} else {
						item(frame, protocol, 1, 1);
						if (malformed == 1) frame.writerIndex(frame.writerIndex() - 1);
						else frame.writeByte(1);
					}
					assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
					assertEquals(0, frame.readerIndex());
				} finally {
					frame.release();
				}
			}
		}
	}

	private static void header(ByteBuf input, int protocol) {
		input.writeByte(0).writeShort(12).writeByte(0).writeShort(123);
		if (protocol < 107) input.writeByte(0);
		else VARINT.encode(input, 0);
	}

	private static void item(ByteBuf input, int protocol, int id, int amount) {
		if (protocol < 404) input.writeShort(id);
		else { input.writeBoolean(true); VARINT.encode(input, id); }
		input.writeByte(amount);
		if (protocol < 393) input.writeShort(0);
		input.writeByte(0);
	}
}
