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

import de.jpx3.intave.packet.contract.WindowOpenPacket;

public interface MutableWindowOpenPacket extends WindowOpenPacket, MutableWindowAddressed, MutableEntityAddressed {

	void setMenuType(String menuType);

	void setSlots(int slots);

	void setEntityId(int entityId);

	@Override
	default void setEntityId(int index, int entityId) {
		if (index != 0 || !optionalEntityId().isPresent()) {
			throw new IndexOutOfBoundsException("Entity index: " + index);
		}
		setEntityId(entityId);
	}
}
