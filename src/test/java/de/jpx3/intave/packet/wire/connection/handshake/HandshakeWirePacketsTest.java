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

package de.jpx3.intave.packet.wire.connection.handshake;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.wire.WirePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.*;

final class HandshakeWirePacketsTest {
	@Test
	void keepsPacketMutationsOutOfSubsequentInstances() {
		HandshakeWirePacket first = WirePackets.createPacket(HANDSHAKING, "SET_PROTOCOL", 768);
		first.setServerAddress("modified.example");
		first.setNextConnectionState(LOGIN);
		HandshakeWirePacket second = WirePackets.createPacket(HANDSHAKING, 0, 768);
		assertNotSame(first, second);
		assertEquals("", second.serverAddress());
		assertEquals(STATUS, second.nextConnectionState());
	}

	@Test
	void decodesCompletePacketsWithTheirRegisteredCodec() {
		int protocolVersion = 768;
		HandshakeWirePacket expected = new HandshakeWirePacket(
			protocolVersion,
			"localhost",
			25565,
			LOGIN
		);
		ByteBuf packet = Unpooled.buffer();
		ByteBufStreamCodecs.VARINT.encode(packet, 0);
		HandshakeWirePacket.STREAM_CODEC.encode(packet, expected);
		int readerIndex = packet.readerIndex();

		HandshakeWirePacket decoded = WirePackets.decodePacket(HANDSHAKING, protocolVersion, packet);

		assertEquals(protocolVersion, decoded.protocolVersion());
		assertEquals("localhost", decoded.serverAddress());
		assertEquals(25565, decoded.serverPort());
		assertEquals(LOGIN, decoded.nextConnectionState());
		assertEquals(readerIndex, packet.readerIndex());
		assertTrue(packet.release());
	}

	@Test
	void decodesTheHandshakeBeforeItsProtocolVersionIsKnown() {
		HandshakeWirePacket expected = new HandshakeWirePacket(
			Integer.MAX_VALUE,
			"localhost",
			25565,
			LOGIN
		);
		ByteBuf packet = Unpooled.buffer();
		ByteBufStreamCodecs.VARINT.encode(packet, 0);
		HandshakeWirePacket.STREAM_CODEC.encode(packet, expected);
		int readerIndex = packet.readerIndex();

		HandshakeWirePacket decoded = WirePackets.createVersionIndependentPacket(HANDSHAKING, packet);

		assertEquals(expected, decoded);
		assertEquals(readerIndex, packet.readerIndex());
		assertTrue(packet.release());
	}
}
