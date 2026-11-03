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

package de.jpx3.intave.packet.contract.immutable;

import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;

import java.util.List;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Server.TAB_COMPLETE_OUT;

public interface TabCompleteResponsePacket extends PacketContract {
	List<String> suggestions();

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY && packetType == TAB_COMPLETE_OUT;
	}
}
