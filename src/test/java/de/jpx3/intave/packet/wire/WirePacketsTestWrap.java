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

import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.*;

final class WirePacketsTestWrap {
	@Test
	void resolvesPacketTypesAndVersionedIdsAcrossEveryBundledProtocol() {
		PacketIdRegistry<?> registry = PacketIdRegistries.serverbound();
		for (int protocol : registry.supportedProtocolVersions()) {
			int handshakeId = registry.idOf(HANDSHAKING, PacketType.Client.SET_PROTOCOL, protocol);
			assertInstanceOf(HandshakePacket.class, WirePackets.createPacket(HANDSHAKING, handshakeId, protocol));
			assertInstanceOf(HandshakePacket.class, WirePackets.createPacket(HANDSHAKING, PacketType.Client.SET_PROTOCOL, protocol));
			int inputId = registry.idOf(PLAY, PacketType.Client.PLAYER_INPUT, protocol);
			assertInstanceOf(PlayerInputPacket.class, WirePackets.createPacket(PLAY, inputId, protocol));
			assertInstanceOf(PlayerInputPacket.class, WirePackets.createPacket(PLAY, PacketType.Client.PLAYER_INPUT, protocol));
		}
	}

	@Test
	void infersDirectionFromPacketIdsAndRejectsConflictingDirections() {
		assertInstanceOf(AbilityInPacket.class, WirePackets.createPlayPacket(PacketType.Client.ABILITIES_IN, 47));
		assertInstanceOf(AbilityOutPacket.class, WirePackets.createPlayPacket(PacketType.Server.ABILITIES_OUT, 47));
		assertThrows(IllegalArgumentException.class, () ->
			WirePackets.createPlayPacket(PacketDirection.SERVERBOUND, PacketType.Server.ABILITIES_OUT, 47));
		assertThrows(IllegalArgumentException.class, () ->
			WirePackets.createPlayPacket(PacketDirection.CLIENTBOUND, PacketType.Client.ABILITIES_IN, 47));
		MutableKeepAlivePacket packet = WirePackets.createPlayPacket(PacketType.Server.KEEP_ALIVE_OUT, 107);
		packet.setId(300);
		ByteBuf output = Unpooled.buffer();
		try {
			WirePackets.encodePacket(PLAY, PacketType.Server.KEEP_ALIVE_OUT, 107, packet, output);
			assertEquals(0x1f, output.getUnsignedByte(0));
			KeepAlivePacket decoded = WirePackets.decodePacket(PacketDirection.CLIENTBOUND, PLAY, 107, output);
			assertEquals(300, decoded.id());
		} finally {
			output.release();
		}
	}

	@Test
	void rejectsUnimplementedPacketsAndIdsFromOtherStates() {
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(LOGIN, 0, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(STATUS, 0, 768));
		assertInstanceOf(KeepAlivePacket.class, WirePackets.createPacket(PLAY, PacketType.Client.KEEP_ALIVE_IN, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, PacketType.Client.START, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, PacketType.Client.START, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, PacketType.Client.CONFIGURATION_ACK, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, -1, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(PLAY, Integer.MAX_VALUE, 768));
	}

	@Test
	void rejectsUnsupportedProtocolsAndNullKeys() {
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(HANDSHAKING, PacketType.Client.SET_PROTOCOL, -1));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(null, 0, 768));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(null, PacketType.Client.SET_PROTOCOL, 768));
		assertThrows(NullPointerException.class, () -> WirePackets.createPacket(PLAY, null, 768));
	}
}
