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
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.Packet;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.function.Consumer;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static org.junit.jupiter.api.Assertions.*;

public final class WirePacketTestSupport {
	private WirePacketTestSupport() {}

	public static void assertUnsupported(PacketDirection direction, String name, int protocol) {
		int id = registry(direction).idOf(PLAY, name, protocol);
		if (id < 0) return;
		assertFalse(
				WirePackets.packetIdKnown(direction, PLAY, id, protocol), name + " in " + protocol);
		ByteBuf input = Unpooled.buffer();
		try {
			ByteBufStreamCodecs.VARINT.encode(input, id);
			assertNull(WirePackets.decodePacket(direction, PLAY, protocol, input));
			assertEquals(0, input.readerIndex());
		} finally {
			input.release();
		}
	}

	public static PacketIdRegistry registry(PacketDirection direction) {
		return direction == SERVERBOUND
				? PacketIdRegistries.serverbound()
				: PacketIdRegistries.clientbound();
	}

	public static <T extends Packet> T roundTrip(
			PacketDirection direction, String name, int protocol, Consumer<ByteBuf> payload) {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			int id = registry(direction).idOf(PLAY, name, protocol);
			assertTrue(id >= 0, name + " in " + protocol);
			ByteBufStreamCodecs.VARINT.encode(input, id);
			int payloadStart = input.writerIndex();
			payload.accept(input);
			T decoded = WirePackets.decodePacket(direction, PLAY, protocol, input);
			assertNotNull(decoded);
			assertEquals(0, input.readerIndex());
			ByteBuf body = input.duplicate().readerIndex(payloadStart);
			WirePacketWrap.from(decoded.getClass()).decode(body);
			assertFalse(body.isReadable(), name + " left unread bytes");
			WirePackets.encodePacket(direction, PLAY, name, protocol, decoded, output);
			assertEquals(input, output, name + " changed during round trip");
			return decoded;
		} finally {
			input.release();
			output.release();
		}
	}

	public static <T extends Packet> T decodePayload(Class<T> packetClass, ByteBuf input) {
		return WirePacketWrap.from(packetClass).decode(input);
	}

	public static void encodePayload(Packet packet, ByteBuf output) {
		WirePacketWrap.from(packet.getClass()).encode(output, packet);
	}
}
