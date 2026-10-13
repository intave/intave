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

package de.jpx3.intave.packet.contract.mutable;

import de.jpx3.intave.packet.contract.CombatEventPacket;

public interface MutableCombatEventPacket extends CombatEventPacket, MutableEntityAddressed {

	void setFirstEntityId(int entityId);

	void setSecondEntityId(int entityId);

	default void clearSecondEntity() {
		setSecondEntityId(-1);
	}

	@Override
	default void setEntityId(int index, int entityId) {
		if (index == 0) {
			setFirstEntityId(entityId);
			return;
		}
		if (index == 1 && hasSecondEntity()) {
			setSecondEntityId(entityId);
			return;
		}
		throw new IndexOutOfBoundsException("Entity index: " + index);
	}
}
