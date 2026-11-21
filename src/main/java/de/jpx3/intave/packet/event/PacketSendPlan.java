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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.BundleDelimiterPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PacketSendPlan {
	private final List<TypedPacketContract> packets = new ArrayList<>();
	private Map<Integer, Integer> bundleEnds;

	PacketSendPlan() {}

	List<TypedPacketContract> packets() {
		return packets;
	}

	void bundleFrom(int start) {
		if (start == packets.size()) {
			return;
		}
		if (bundleEnds == null) {
			bundleEnds = new HashMap<>();
		}
		// Children finish first. A parent starting at the same packet covers their range.
		bundleEnds.put(start, packets.size());
	}

	/** Supply the sending library's delimiter, or null if this connection cannot send bundles. */
	public List<TypedPacketContract> outgoingPackets(
		@Nullable BundleDelimiterPacket delimiter, boolean bundleOpen
	) {
		if (delimiter == null || bundleEnds == null) {
			return packets;
		}
		TypedPacketContract typedDelimiter = delimiter.typedTo(PacketType.Server.BUNDLE_DELIMITER);
		List<TypedPacketContract> framed = new ArrayList<>(packets.size() + 2);
		int groupEnd = 0;
		for (int i = 0; i < packets.size();) {
			if (i >= groupEnd) {
				groupEnd = bundleEnds.getOrDefault(i, i);
			}
			TypedPacketContract packet = packets.get(i);
			if (i < groupEnd && !bundleOpen && canBundle(packet)) {
				framed.add(typedDelimiter);
				do {
					framed.add(packets.get(i++));
				} while (i < groupEnd && canBundle(packets.get(i)));
				framed.add(typedDelimiter);
			} else {
				framed.add(packet);
				if (packet.type() == PacketType.Server.BUNDLE_DELIMITER) {
					bundleOpen = !bundleOpen;
				}
				i++;
			}
		}
		return framed;
	}

	private static boolean canBundle(TypedPacketContract packet) {
		return packet.type() != PacketType.Server.BUNDLE_DELIMITER && packet.type() != PacketType.Server.START_CONFIGURATION;
	}
}
