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

package de.jpx3.intave.packet.event;

import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerSlot;
import org.jspecify.annotations.NonNull;

public interface PacketSubscription extends Comparable<PacketSubscription> {
	void onPacket(PacketEvent event);

	default ListenerSlot slot() {
		return ListenerSlot.AFTER_IMMUTABLE;
	}

	default ListenerPriority priority() {
		return ListenerPriority.NORMAL;
	}

	default boolean allowForPacketMutations() {
		return slot().allowMutations();
	}

	// do not override this method unless you know what you're doing
	@Override
	default int compareTo(@NonNull PacketSubscription o) {
		int slotComparison = slot().compareTo(o.slot());
		if (slotComparison != 0) {
			return slotComparison;
		}
		int priorityComparison = priority().compareTo(o.priority());
		if (priorityComparison != 0) {
			return priorityComparison;
		}
		return Integer.compare(hashCode(), o.hashCode());
	}
}
