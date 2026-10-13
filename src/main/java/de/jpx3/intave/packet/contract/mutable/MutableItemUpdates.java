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
import de.jpx3.intave.packet.contract.ItemUpdates;
import de.jpx3.intave.share.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public interface MutableItemUpdates extends ItemUpdates, MutableWindowAddressed {

	void setItemMap(Map<Integer, ItemStack> items);

	default void setItem(int slot, @Nullable ItemStack item) {
		Map<Integer, ItemStack> items = new LinkedHashMap<>(itemMap());
		items.put(slot, item);
		setItemMap(items);
	}

	default void clearItem(int slot) {
		setItem(slot, null);
	}

	default void removeItemUpdate(int slot) {
		Map<Integer, ItemStack> items = new LinkedHashMap<>(itemMap());
		items.remove(slot);
		setItemMap(items);
	}
}
