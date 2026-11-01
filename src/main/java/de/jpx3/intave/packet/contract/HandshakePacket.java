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

package de.jpx3.intave.packet.contract;

import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.packet.ConnectionState;

public interface HandshakePacket extends Packet {
	int protocolVersion();

	String serverAddress();

	int serverPort();

	ConnectionState nextConnectionState();

	@Override
	default boolean appliesTo(ConnectionState state, PacketId packetId) {
		return state == ConnectionState.HANDSHAKING && packetId == PacketId.Client.SET_PROTOCOL;
	}
}
