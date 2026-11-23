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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;

import java.util.Collections;
import java.util.Map;

public interface WindowSetSlotPacket extends PacketContract, ItemUpdates, WindowRevision, SlotAddressed {
	@Nullable
	ItemStack item();

	@Override
	default Map<Integer, ItemStack> itemMap() {
		return Collections.singletonMap(slot(), item());
	}

	@Override
	default boolean full() {
		return false;
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType type) {
		return state == ConnectionState.PLAY && type == PacketType.Server.SET_SLOT;
	}
}
