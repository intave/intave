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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.contract.WindowClickPacket;
import de.jpx3.intave.share.ItemStack;
import de.jpx3.intave.share.WindowClickType;

public interface MutableWindowClickPacket extends WindowClickPacket, MutableItemUpdates, MutableWindowRevision, MutableCarriedItem, MutableSlotAddressed {

	void setClickType(WindowClickType clickType);

	void setButton(int button);

	void setClickedItem(@Nullable ItemStack item);

	default void clearClickedItem() {
		setClickedItem(null);
	}
}
