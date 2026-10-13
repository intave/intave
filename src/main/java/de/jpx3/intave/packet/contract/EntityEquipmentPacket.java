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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.ItemStack;

import java.util.List;

public interface EntityEquipmentPacket extends Packet, SingleEntityAddressed {

	List<Entry> equipment();

	interface Entry {
		Slot slot();

		@Nullable
		ItemStack item();

		default boolean empty() {
			return item() == null;
		}
	}

	enum Slot {
		MAIN_HAND,
		OFF_HAND,
		FEET,
		LEGS,
		CHEST,
		HEAD,
		BODY,
		SADDLE
	}
}
