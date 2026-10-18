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

import de.jpx3.intave.codec.VarIntCodec;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.*;

final class WirePacketsTest {
	@Test
	void resolvesCanonicalNamesAndVersionedIdsAcrossEveryBundledProtocol() {
		PacketIdRegistry registry = PacketIdRegistries.serverbound();
		for (int protocol : registry.supportedProtocolVersions()) {
			int handshakeId = registry.idOf(HANDSHAKING, "SET_PROTOCOL", protocol);
			assertInstanceOf(HandshakeWirePacket.class, WirePackets.createPacket(HANDSHAKING, handshakeId, protocol));
			assertInstanceOf(HandshakeWirePacket.class, WirePackets.createPacket(HANDSHAKING, "SET_PROTOCOL", protocol));

			int inputId = registry.idOf(PLAY, "PLAYER_INPUT", protocol);
			if (protocol >= 768) {
				assertInstanceOf(PlayerInputWirePacket.class, WirePackets.createPacket(PLAY, inputId, protocol));
				assertInstanceOf(PlayerInputWirePacket.class, WirePackets.createPacket(PLAY, "PLAYER_INPUT", protocol));
			} else {
				assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, inputId, protocol));
				assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, "PLAYER_INPUT", protocol));
			}
		}
	}

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
		VarIntCodec.INSTANCE.encode(packet, 0);
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
		VarIntCodec.INSTANCE.encode(packet, 0);
		HandshakeWirePacket.STREAM_CODEC.encode(packet, expected);
		int readerIndex = packet.readerIndex();

		HandshakeWirePacket decoded = WirePackets.decodePacket(HANDSHAKING, packet);

		assertEquals(expected, decoded);
		assertEquals(readerIndex, packet.readerIndex());
		assertTrue(packet.release());
	}

	@Test
	void consumesThePacketIdBeforeDecodingPlayerInputFlags() {
		int protocolVersion = 776;
		int packetId = PacketIdRegistries.serverbound().idOf(PLAY, "PLAYER_INPUT", protocolVersion);
		Input expected = new Input(false, false, true, false, true, false, true);
		ByteBuf packet = Unpooled.buffer();
		try {
			VarIntCodec.INSTANCE.encode(packet, packetId);
			PlayerInputWirePacket.STREAM_CODEC.encode(packet, new PlayerInputWirePacket(expected));

			PlayerInputWirePacket decoded = WirePackets.decodePacket(PLAY, protocolVersion, packet);

			assertEquals(expected, decoded.input());
			assertEquals(0, packet.readerIndex());
		} finally {
			packet.release();
		}
	}

	@Test
	void rejectsUnimplementedPacketsAndIdsFromOtherStates() {
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(LOGIN, 0, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(STATUS, 0, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, "KEEP_ALIVE", 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, "START", 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, "PlayerInput", 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, "UNKNOWN", 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, -1, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, Integer.MAX_VALUE, 768));
	}

	@Test
	void rejectsUnsupportedProtocolsAndNullKeys() {
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, "SET_PROTOCOL", -1));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(null, 0, 768));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(null, "SET_PROTOCOL", 768));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(PLAY, null, 768));
	}
}
