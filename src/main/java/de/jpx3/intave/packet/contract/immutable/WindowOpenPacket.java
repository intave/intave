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

import java.util.OptionalInt;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Server.OPEN_WINDOW_HORSE;

public interface WindowOpenPacket extends PacketContract, WindowAddressed, EntityAddressed {
	String menuType();

	int slots();

	OptionalInt optionalEntityId();

	@Override
	default int entityCount() {
		return optionalEntityId().isPresent() ? 1 : 0;
	}

	@Override
	default int entityId(int index) {
		if (index != 0 || !optionalEntityId().isPresent()) {
			throw new IndexOutOfBoundsException("Entity index: " + index);
		}
		return optionalEntityId().getAsInt();
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY && packetType == OPEN_WINDOW_HORSE;
	}
}
