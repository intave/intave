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

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Server.MOUNT;

public interface MountEntityPacket extends PacketContract, PrimaryEntityAddressed {
	int[] passengerIds();

	default int passengerCount() {
		return passengerIds().length;
	}

	@Override
	default int entityCount() {
		return passengerIds().length + 1;
	}

	@Override
	default int entityId(int index) {
		if (index == 0) {
			return entityId();
		}
		return passengerIds()[index - 1];
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY && packetType == MOUNT;
	}
}
