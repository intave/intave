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

import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface WindowBulkItemsPacket extends PacketContract, WindowItemsPacket {
	default List<ItemStack> items() {
		Map<Integer, ItemStack> slots = itemMap();
		List<ItemStack> result = new ArrayList<>(slots.size());
		for (int i = 0; i < slots.size(); i++) {
			result.add(slots.get(i));
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType type) {
		return state == ConnectionState.PLAY && type == PacketType.Server.WINDOW_ITEMS;
	}

	@Override
	default boolean full() {
		return true;
	}
}
