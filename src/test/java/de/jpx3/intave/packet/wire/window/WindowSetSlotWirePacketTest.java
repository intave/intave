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
import de.jpx3.intave.packet.contract.immutable.WindowSetSlotPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.SET_SLOT;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class WindowSetSlotWirePacketTest {
	private static final MinecraftKey STONE = MinecraftKey.withDefaultNamespace("stone");

	@Test
	void roundTripsSlotsAcrossEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			for (int container : new int[]{-2, -1, 7, protocol < 768 ? 127 : 300}) {
				for (int amount : new int[]{0, 3}) {
					WindowSetSlotPacket packet = roundTrip(CLIENTBOUND, SET_SLOT, protocol, out -> {
						header(out, protocol, container);
						stack(out, protocol, amount);
					});
					assertEquals(container, packet.containerId(), "protocol " + protocol);
					assertEquals(-1, packet.slot());
					assertEquals(protocol < 756 ? OptionalInt.empty() : OptionalInt.of(134), packet.revision());
					assertFalse(packet.full());
					assertTrue(packet.itemMap().containsKey(-1));
					if (amount == 0) {
						assertNull(packet.item());
						assertNull(packet.itemMap().get(-1));
					} else {
						ItemStack item = packet.item();
						assertEquals(STONE, item.type());
						assertEquals(amount, item.amount());
						item.setAmount(8);
						assertEquals(amount, packet.item().amount());
						assertEquals(amount, packet.itemMap().get(-1).amount());
					}
				}
			}
		}
	}

	@Test
	void rejectsTruncatedItemsAndTrailingData() {
		for (int protocol : new int[]{47, 393, 404, 756, 766, 768, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(CLIENTBOUND).idOf(PLAY, SET_SLOT, protocol));
				header(frame, protocol, 0);
				assertThrows(RuntimeException.class, () -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, frame));
				stack(frame, protocol, 0);
				frame.writeByte(1);
				assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, frame));
			} finally {
				frame.release();
			}
		}
	}

	private static void header(ByteBuf out, int protocol, int container) {
		if (protocol < 768) out.writeByte(container);
		else VARINT.encode(out, container);
		if (protocol >= 756) VARINT.encode(out, 134);
		out.writeShort(-1);
	}

	private static void stack(ByteBuf out, int protocol, int amount) {
		int id = ItemRegistry.forProtocol(protocol).idOf(STONE);
		if (protocol >= 766) {
			VARINT.encode(out, amount);
			if (amount != 0) {
				VARINT.encode(out, id);
				out.writeByte(0).writeByte(0);
			}
			return;
		}
		if (protocol < 404) out.writeShort(amount == 0 ? -1 : id);
		else {
			out.writeBoolean(amount != 0);
			if (amount != 0) VARINT.encode(out, id);
		}
		if (amount != 0) {
			out.writeByte(amount);
			if (protocol < 393) out.writeShort(0);
			out.writeByte(0);
		}
	}
}
