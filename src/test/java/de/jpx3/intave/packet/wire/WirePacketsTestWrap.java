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

import de.jpx3.intave.packet.contract.HandshakePacket;
import de.jpx3.intave.packet.contract.KeepAlivePacket;
import de.jpx3.intave.packet.contract.PlayerInputPacket;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WirePacketsTestWrap {
	@Test
	void resolvesCanonicalNamesAndVersionedIdsAcrossEveryBundledProtocol() {
		PacketIdRegistry registry = PacketIdRegistries.serverbound();
		for (int protocol : registry.supportedProtocolVersions()) {
			int handshakeId = registry.idOf(HANDSHAKING, "SET_PROTOCOL", protocol);
			assertInstanceOf(HandshakePacket.class, WirePackets.createPacket(HANDSHAKING, handshakeId, protocol));
			assertInstanceOf(HandshakePacket.class, WirePackets.createPacket(HANDSHAKING, "SET_PROTOCOL", protocol));
			int inputId = registry.idOf(PLAY, "PLAYER_INPUT", protocol);
			assertInstanceOf(PlayerInputPacket.class, WirePackets.createPacket(PLAY, inputId, protocol));
			assertInstanceOf(PlayerInputPacket.class, WirePackets.createPacket(PLAY, "PLAYER_INPUT", protocol));
		}
	}

	@Test
	void rejectsUnimplementedPacketsAndIdsFromOtherStates() {
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(LOGIN, 0, 768));
		assertThrows(IllegalArgumentException.class, () -> WirePackets.createPacket(STATUS, 0, 768));
		assertInstanceOf(KeepAlivePacket.class, WirePackets.createPacket(PLAY, "KEEP_ALIVE", 768));
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
