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

public interface CombatEventPacket extends Packet, EntityAddressed {

	int firstEntityId();

	int secondEntityId();

	default boolean hasSecondEntity() {
		return secondEntityId() != -1;
	}

	@Override
	default int entityCount() {
		return hasSecondEntity() ? 2 : 1;
	}

	@Override
	default int entityId(int index) {
		if (index == 0) {
			return firstEntityId();
		}
		if (index == 1 && hasSecondEntity()) {
			return secondEntityId();
		}
		throw new IndexOutOfBoundsException("Entity index: " + index);
	}
}
