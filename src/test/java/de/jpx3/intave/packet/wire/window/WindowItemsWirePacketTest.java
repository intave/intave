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

import de.jpx3.intave.item.ItemComponentTypeRegistry;
import de.jpx3.intave.item.ItemComponentValue;
import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.immutable.WindowBulkItemsPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.WINDOW_ITEMS;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class WindowItemsWirePacketTest {
	private static final MinecraftKey STONE = MinecraftKey.withDefaultNamespace("stone");
	private static final MinecraftKey DAMAGE = MinecraftKey.withDefaultNamespace("damage");
	private static final MinecraftKey ENCHANTMENTS = MinecraftKey.withDefaultNamespace("enchantments");

	@Test
	void preservesEverySlotAndKeepsTheCursorSeparateAcrossAllRegisteredProtocols() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			WindowBulkItemsPacket packet = roundTrip(CLIENTBOUND, WINDOW_ITEMS, protocol, out -> {
				header(out, protocol, 3);
				stack(out, protocol, 2);
				stack(out, protocol, 0);
				stack(out, protocol, 4);
				if (protocol >= 756) stack(out, protocol, 7);
			});
			assertEquals(protocol < 768 ? 211 : 300, packet.containerId());
			assertEquals(protocol < 756 ? OptionalInt.empty() : OptionalInt.of(134), packet.revision());
			assertTrue(packet.full());
			List<ItemStack> items = packet.items();
			assertEquals(3, items.size());
			assertEquals(STONE, items.get(0).type());
			assertEquals(2, items.get(0).amount());
			assertNull(items.get(1));
			assertEquals(4, items.get(2).amount());
			items.get(2).setAmount(5);
			assertTrue(packet.itemMap().containsKey(1));
			assertEquals(4, packet.itemMap().get(2).amount());
			assertEquals(protocol >= 756, packet.hasCarriedItem());
			if (protocol >= 756) assertEquals(7, packet.carriedItem().amount());
			else assertNull(packet.carriedItem());
		}
	}

	@Test
	void emptyCursorIsKnownFrom1171AndEnchantmentsEndAtTheNextSlot() {
		for (int protocol = 766; protocol <= 777; protocol++) {
			final int version = protocol;
			WindowBulkItemsPacket packet = roundTrip(CLIENTBOUND, WINDOW_ITEMS, protocol, out -> {
				header(out, version, 2);
				VARINT.encode(out, 1);
				VARINT.encode(out, ItemRegistry.forProtocol(version).idOf(STONE));
				out.writeByte(1).writeByte(1);
				VARINT.encode(out, ItemComponentTypeRegistry.forProtocol(version).idOf(ENCHANTMENTS));
				out.writeByte(1).writeByte(0).writeByte(5);
				if (version < 770) out.writeBoolean(false);
				VARINT.encode(out, ItemComponentTypeRegistry.forProtocol(version).idOf(DAMAGE));
				stack(out, version, 9);
				stack(out, version, 0);
			});
			assertTrue(packet.items().get(0).components().removed().contains(DAMAGE));
			assertEquals(9, packet.items().get(1).amount());
			assertTrue(packet.hasCarriedItem());
			assertNull(packet.carriedItem());
		}
	}

	@Test
	void profilesAndNestedTemplatesPreserveTheFollowingSlotAndCursor() {
		for (int protocol : new int[]{772, 773, 774, 775, 776, 777}) {
			WindowBulkItemsPacket packet = roundTrip(CLIENTBOUND, WINDOW_ITEMS, protocol, out -> {
				header(out, protocol, 2);
				out.writeByte(1);
				VARINT.encode(out, ItemRegistry.forProtocol(protocol).idOf(STONE));
				out.writeByte(2).writeByte(0);
				component(out, protocol, "profile");
				out.writeBoolean(true);
				if (protocol >= 773) out.writeLong(1).writeLong(2);
				VARINT_STRING.encode(out, "Alex");
				if (protocol < 773) out.writeBoolean(true).writeLong(1).writeLong(2);
				out.writeByte(0);
				if (protocol >= 773) {
					out.writeBoolean(true);
					VARINT_STRING.encode(out, "example:skin");
					out.writeBoolean(false).writeBoolean(false).writeBoolean(true).writeBoolean(true);
				}
				component(out, protocol, "bundle_contents");
				out.writeByte(1);
				int stone = ItemRegistry.forProtocol(protocol).idOf(STONE);
				VARINT.encode(out, protocol < 775 ? 3 : stone);
				VARINT.encode(out, protocol < 775 ? stone : 3);
				out.writeByte(1).writeByte(0);
				component(out, protocol, "damage");
				out.writeByte(11);
				stack(out, protocol, 8);
				stack(out, protocol, 4);
			});
			ItemStack first = packet.items().get(0);
			assertEquals(Optional.of("Alex"), first.skullOwnerName());
			ItemComponentValue nested = first.components().get(MinecraftKey.withDefaultNamespace("bundle_contents")).elements().get(0);
			if (protocol >= 775) {
				assertEquals(3, nested.field("count").number().intValue());
				assertEquals(11, nested.field("components").patch().get(DAMAGE).number().intValue());
			} else {
				assertEquals(3, nested.item().amount());
				assertEquals(11, nested.item().components().get(DAMAGE).number().intValue());
			}
			first.setSkullOwnerName(null);
			ByteBuf encoded = Unpooled.buffer();
			try {
				ItemStack.codecFor(protocol).encode(encoded, first);
				ItemStack edited = ItemStack.codecFor(protocol).decode(encoded);
				assertFalse(edited.hasSkullOwnerName());
				ItemComponentValue profile = edited.components().get(MinecraftKey.withDefaultNamespace("profile"));
				assertEquals(2, profile.field("id").uuid().getLeastSignificantBits());
				if (protocol >= 773) assertEquals("example:skin", profile.field("skin").field("body").string());
			} finally {
				encoded.release();
			}
			assertEquals(8, packet.items().get(1).amount());
			assertEquals(4, packet.carriedItem().amount());
		}
	}

	@Test
	void rejectsImpossibleCountsTruncatedStacksAndTrailingData() {
		for (int protocol : new int[]{47, 755, 756, 768, 770, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(CLIENTBOUND).idOf(PLAY, WINDOW_ITEMS, protocol));
				int start = frame.writerIndex();
				header(frame, protocol, -1);
				assertThrows(RuntimeException.class, () -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, frame));
				frame.writerIndex(start);
				header(frame, protocol, 1);
				stack(frame, protocol, 2);
				if (protocol >= 756) stack(frame, protocol, 4);
				int end = frame.writerIndex();
				for (int length = start; length < end; length++) {
					frame.writerIndex(length);
					assertThrows(RuntimeException.class, () -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, frame));
					assertEquals(0, frame.readerIndex());
				}
				frame.writerIndex(end).writeByte(77);
				assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, frame));
			} finally {
				frame.release();
			}
		}
	}

	private static void component(ByteBuf out, int protocol, String name) {
		VARINT.encode(out, ItemComponentTypeRegistry.forProtocol(protocol).idOf(MinecraftKey.withDefaultNamespace(name)));
	}

	private static void header(ByteBuf out, int version, int size) {
		if (version < 768) out.writeByte(211);
		else VARINT.encode(out, 300);
		if (version < 756) out.writeShort(size);
		else {
			VARINT.encode(out, 134);
			VARINT.encode(out, size);
		}
	}

	private static void stack(ByteBuf out, int version, int amount) {
		int id = ItemRegistry.forProtocol(version).idOf(STONE);
		if (version >= 766) {
			VARINT.encode(out, amount);
			if (amount != 0) {
				VARINT.encode(out, id);
				out.writeByte(0).writeByte(0);
			}
			return;
		}
		if (version < 404) out.writeShort(amount == 0 ? -1 : id);
		else {
			out.writeBoolean(amount != 0);
			if (amount != 0) VARINT.encode(out, id);
		}
		if (amount != 0) {
			out.writeByte(amount);
			if (version < 393) out.writeShort(0);
			out.writeByte(0);
		}
	}
}
