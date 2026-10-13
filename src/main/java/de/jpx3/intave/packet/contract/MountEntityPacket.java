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

public interface MountEntityPacket extends Packet, PrimaryEntityAddressed {

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
}
