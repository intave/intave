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
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.registry.PacketTypeRegistry;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.function.Consumer;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static org.junit.jupiter.api.Assertions.*;

public final class WirePacketTestSupport {
	private WirePacketTestSupport() {}

	public static MutableKeepAlivePacket keepAlive(long id) {
		return roundTrip(CLIENTBOUND, PacketType.Server.KEEP_ALIVE_OUT, 777, output -> output.writeLong(id));
	}

	public static MutableEntityVelocityPacket entityVelocity(int entityId, Motion motion) {
		return roundTrip(CLIENTBOUND, PacketType.Server.ENTITY_VELOCITY, 777, output -> {
			ByteBufStreamCodecs.VARINT.encode(output, entityId);
			Motion.LOW_PRECISION_STREAM_CODEC.encode(output, motion);
		});
	}

	public static void assertUnsupported(PacketDirection direction, PacketType packetType, int protocol) {
		int id = registry(direction).idOf(PLAY, packetType, protocol);
		if (id < 0) return;
		assertFalse(
				WirePackets.packetIdKnown(direction, PLAY, id, protocol), packetType + " in " + protocol);
		ByteBuf input = Unpooled.buffer();
		try {
			ByteBufStreamCodecs.VARINT.encode(input, id);
			assertNull(WirePackets.decodePacket(direction, PLAY, protocol, input));
			assertEquals(0, input.readerIndex());
		} finally {
			input.release();
		}
	}

	public static PacketTypeRegistry<?> registry(PacketDirection direction) {
		return direction == SERVERBOUND
				? PacketTypeRegistries.serverbound()
				: PacketTypeRegistries.clientbound();
	}

	public static <T extends PacketContract> T roundTrip(
		PacketDirection direction, PacketType packetType, int protocol, Consumer<ByteBuf> payload) {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			int id = registry(direction).idOf(PLAY, packetType, protocol);
			assertTrue(id >= 0, packetType + " in " + protocol);
			ByteBufStreamCodecs.VARINT.encode(input, id);
			int payloadStart = input.writerIndex();
			payload.accept(input);
			T decoded = WirePackets.decodePacket(direction, PLAY, protocol, input);
			assertNotNull(decoded);
			assertEquals(0, input.readerIndex());
			ByteBuf body = input.duplicate().readerIndex(payloadStart);
			WirePacketDefinition.from(decoded.getClass()).decode(body);
			assertFalse(body.isReadable(), packetType + " left unread bytes");
			WirePackets.encodePacket(direction, PLAY, packetType, protocol, decoded, output);
			assertEquals(input, output, packetType + " changed during round trip");
			return decoded;
		} finally {
			input.release();
			output.release();
		}
	}

	public static <T extends PacketContract> T decodePayload(Class<T> packetClass, ByteBuf input) {
		return WirePacketDefinition.from(packetClass).decode(input);
	}

	public static void encodePayload(PacketContract packet, ByteBuf output) {
		WirePacketDefinition.from(packet.getClass()).encode(output, packet);
	}
}
