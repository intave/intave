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

package de.jpx3.intave.packet.wire.window.click;

import de.jpx3.intave.item.HashedItemStack;
import de.jpx3.intave.item.ItemComponentTypeRegistry;
import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.packet.contract.immutable.HashedWindowClickPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickHeaderPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.WINDOW_CLICK;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class ModernWindowClickPayloadTest {
	private static final MinecraftKey STONE = MinecraftKey.withDefaultNamespace("stone");
	private static final MinecraftKey PROFILE = MinecraftKey.withDefaultNamespace("profile");

	@Test
	void decodesChangedSlotsAndCursorForEveryFullStackProtocol() {
		for (int protocol : IntStream.rangeClosed(755, 769).toArray()) {
			WindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
				header(input, protocol);
				input.writeByte(2).writeShort(12);
				stack(input, protocol, 3);
				input.writeShort(13).writeByte(0);
				stack(input, protocol, 7);
			});
			assertFalse(packet.hasClickedItem());
			assertNull(packet.clickedItem());
			assertTrue(packet.hasSlotPredictions());
			assertTrue(packet.hasCarriedItem());
			assertEquals(STONE, packet.itemMap().get(12).type());
			assertEquals(3, packet.itemMap().get(12).amount());
			assertTrue(packet.itemMap().containsKey(13));
			assertNull(packet.itemMap().get(13));
			assertEquals(7, packet.carriedItem().amount());
			if (protocol < 766) assertEquals("Alex", packet.carriedItem().skullOwnerName().get());
			packet.itemMap().get(12).setAmount(99);
			packet.carriedItem().setAmount(99);
			assertEquals(3, packet.itemMap().get(12).amount());
			assertEquals(7, packet.carriedItem().amount());
			assertThrows(UnsupportedOperationException.class, () -> packet.itemMap().clear());
		}
	}

	@Test
	void exposesEveryTransmittedHashAndRemovalWithTheExactVersionRegistry() {
		for (int protocol : IntStream.rangeClosed(770, 777).toArray()) {
			HashedWindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
				header(input, protocol);
				input.writeByte(2).writeShort(12);
				hashedStack(input, protocol, 3);
				input.writeShort(13).writeBoolean(false);
				hashedStack(input, protocol, -1);
			});
			HashedItemStack stack = packet.hashedItemMap().get(12);
			assertEquals(STONE, stack.type());
			assertEquals(3, stack.amount());
			assertEquals(Integer.valueOf(0x89abcdef), stack.componentHashes().get(PROFILE));
			assertTrue(stack.removedComponents().contains(MinecraftKey.withDefaultNamespace("damage")));
			assertEquals(-1, packet.hashedCarriedItem().amount());
			assertTrue(packet.hashedItemMap().containsKey(13));
			assertNull(packet.hashedItemMap().get(13));
			assertThrows(UnsupportedOperationException.class, () -> stack.componentHashes().clear());
		}
	}

	@Test
	void duplicateSlotEntriesUseTheLastPredictionAndRoundTripTheOriginalBytes() {
		for (int protocol : new int[]{755, 764, 766, 769}) {
			WindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
				header(input, protocol);
				input.writeByte(2).writeShort(12);
				stack(input, protocol, 3);
				input.writeShort(12);
				stack(input, protocol, 7);
				input.writeByte(0);
			});
			assertEquals(1, packet.itemMap().size());
			assertEquals(7, packet.itemMap().get(12).amount());
		}
	}

	@Test
	void rejectsEveryTruncatedPayloadAndTrailingBytesWithoutConsumingTheFrame() {
		for (int protocol : new int[]{755, 756, 763, 764, 765, 766, 767, 768, 769, 770, 774, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
				header(frame, protocol);
				int payloadStart = frame.writerIndex();
				frame.writeByte(1).writeShort(12);
				if (protocol < 770) stack(frame, protocol, 3);
				else hashedStack(frame, protocol, 3);
				frame.writeByte(0);
				int end = frame.writerIndex();
				for (int length = payloadStart; length < end; length++) {
					frame.writerIndex(length);
					assertThrows(RuntimeException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
					assertEquals(0, frame.readerIndex());
				}
				frame.writerIndex(end).writeByte(99);
				assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
				assertEquals(0, frame.readerIndex());
			} finally { frame.release(); }
		}
	}

	@Test
	void enforcesVanillaMapAndHashCollectionLimits() {
		for (int protocol : new int[]{756, 769, 770, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
				header(frame, protocol);
				VARINT.encode(frame, 129);
				assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
			} finally { frame.release(); }
		}
		for (boolean removals : new boolean[]{false, true}) {
			ByteBuf input = Unpooled.buffer();
			try {
				input.writeBoolean(true);
				VARINT.encode(input, ItemRegistry.forProtocol(770).idOf(STONE));
				input.writeByte(1);
				if (removals) input.writeByte(0);
				VARINT.encode(input, 257);
				assertThrows(IllegalArgumentException.class, () -> HashedItemStack.codecFor(770).decode(input));
			} finally { input.release(); }
		}
	}

	@Test
	void theInitial117FormatHasNo128SlotLimit() {
		WindowClickPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, 755, input -> {
			header(input, 755);
			VARINT.encode(input, 129);
			for (int slot = 0; slot < 129; slot++) input.writeShort(slot).writeBoolean(false);
			input.writeBoolean(false);
		});
		assertEquals(129, packet.itemMap().size());
	}

	@Test
	void everyVersionSelectsItsOwnItemRegistryForPredictions() {
		for (int protocol : IntStream.rangeClosed(755, 777).toArray()) {
			ItemRegistry.forProtocol(protocol).itemIds().forEach((type, id) -> {
				if (type.equals(MinecraftKey.withDefaultNamespace("air"))) return;
				WindowClickHeaderPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
					header(input, protocol);
					input.writeByte(0); // No changed slots.
					input.writeByte(1); // Presence boolean or component stack count.
					VARINT.encode(input, id);
					if (protocol < 766) input.writeByte(1).writeByte(0);
					else if (protocol < 770) input.writeByte(0).writeByte(0);
					else input.writeByte(1).writeByte(0).writeByte(0);
				});
				MinecraftKey decoded = protocol < 770 ? ((WindowClickPacket) packet).carriedItem().type()
					: ((HashedWindowClickPacket) packet).hashedCarriedItem().type();
				assertEquals(type, decoded, "item " + id + " in protocol " + protocol);
			});
		}
	}

	private static void header(ByteBuf input, int protocol) {
		if (protocol >= 768) VARINT.encode(input, 300); else input.writeByte(3);
		if (protocol != 755) input.writeByte(5);
		input.writeShort(12).writeByte(0).writeByte(0);
	}

	private static void stack(ByteBuf input, int protocol, int amount) {
		if (protocol < 766) input.writeBoolean(true); else VARINT.encode(input, amount);
		VARINT.encode(input, ItemRegistry.forProtocol(protocol).idOf(STONE));
		if (protocol < 766) {
			input.writeByte(amount).writeByte(10);
			if (protocol < 764) input.writeShort(0);
			input.writeByte(8).writeShort(10).writeBytes(new byte[]{'S','k','u','l','l','O','w','n','e','r'});
			input.writeShort(4).writeBytes(new byte[]{'A','l','e','x'}).writeByte(0);
		} else input.writeByte(0).writeByte(0);
	}

	private static void hashedStack(ByteBuf input, int protocol, int amount) {
		input.writeBoolean(true);
		VARINT.encode(input, ItemRegistry.forProtocol(protocol).idOf(STONE));
		VARINT.encode(input, amount);
		input.writeByte(1);
		VARINT.encode(input, ItemComponentTypeRegistry.forProtocol(protocol).idOf(PROFILE));
		input.writeInt(0x89abcdef).writeByte(1);
		VARINT.encode(input, ItemComponentTypeRegistry.forProtocol(protocol).idOf(MinecraftKey.withDefaultNamespace("damage")));
	}
}
