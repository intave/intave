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

import de.jpx3.intave.packet.contract.immutable.HashedWindowClickPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickHeaderPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.WindowClickType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.WINDOW_CLICK;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WindowClickWirePacketCodecsTest {
	@Test
	void decodesClickHeadersAcrossAllProtocols() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			for (WindowClickType click : WindowClickType.values()) {
				WindowClickHeaderPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
					writeHeader(input, protocol, click.ordinal());
					if (protocol < 404) input.writeShort(-1);
					else if (protocol < 755) input.writeBoolean(false);
					else input.writeByte(0).writeByte(0); // Empty changed-slot map and empty carried stack.
				});
				assertEquals(protocol >= 768 ? 300 : -56, packet.containerId());
				assertEquals(-999, packet.slot());
				assertEquals(40, packet.button());
				assertEquals(click, packet.clickType());
				assertEquals(protocol == 755 ? OptionalInt.empty() : OptionalInt.of(protocol < 755 ? -1234 : 123456), packet.revision());
				assertEquals(protocol < 770, packet instanceof WindowClickPacket);
				assertEquals(protocol >= 770, packet instanceof HashedWindowClickPacket);
			}
		}
	}

	@Test
	void modernUnknownClickTypesUsePickupAndRetainTheirWireId() {
		for (int protocol : new int[]{770, 777}) {
			for (int clickId : new int[]{-1, 7, 128}) {
				WindowClickHeaderPacket packet = roundTrip(SERVERBOUND, WINDOW_CLICK, protocol, input -> {
					writeHeader(input, protocol, clickId);
					input.writeByte(0).writeBoolean(false);
				});
				assertEquals(WindowClickType.PICKUP, packet.clickType());
			}
		}
	}

	@Test
	void rejectsTruncatedHeadersWithoutConsumingTheFrame() {
		for (int protocol : new int[]{47, 107, 754, 755, 756, 767, 768, 770, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
				int headerStart = frame.writerIndex();
				writeHeader(frame, protocol, 0);
				int headerEnd = frame.writerIndex();
				for (int end = headerStart; end < headerEnd; end++) {
					frame.writerIndex(end);
					assertThrows(IndexOutOfBoundsException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
					assertEquals(0, frame.readerIndex());
				}
			} finally {
				frame.release();
			}
		}
	}

	@Test
	void rejectsInvalidClickTypesWithoutConsumingTheFrame() {
		for (int protocol : new int[]{47, 107, 755, 756, 768}) {
			for (int clickId : new int[]{-1, 7}) {
				ByteBuf frame = Unpooled.buffer();
				try {
					VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, WINDOW_CLICK, protocol));
					writeHeader(frame, protocol, clickId);
					assertThrows(IllegalArgumentException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
					assertEquals(0, frame.readerIndex());
				} finally {
					frame.release();
				}
			}
		}
	}

	private static void writeHeader(ByteBuf input, int protocol, int clickId) {
		if (protocol >= 768) VARINT.encode(input, 300);
		else input.writeByte(200);
		if (protocol >= 756) VARINT.encode(input, 123456);
		input.writeShort(-999);
		input.writeByte(40);
		if (protocol < 755) input.writeShort(-1234);
		if (protocol < 107) input.writeByte(clickId);
		else VARINT.encode(input, clickId);
	}
}
