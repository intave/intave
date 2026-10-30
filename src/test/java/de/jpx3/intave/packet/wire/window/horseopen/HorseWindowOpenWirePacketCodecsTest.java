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

package de.jpx3.intave.packet.wire.window.horseopen;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class HorseWindowOpenWirePacketCodecsTest {
	@Test
	void horseInventorySizesFollowTheSlotToColumnChanges() {
		for (int protocol : new int[] {766, 767, 768, 770}) {
			HorseWindowOpenWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"OPEN_WINDOW_HORSE",
							protocol,
							b -> b.writeByte(2).writeByte(5).writeInt(42));
			assertEquals(protocol < 767 ? 5 : protocol < 770 ? 16 : 15, packet.slots());
			assertEquals(42, packet.optionalEntityId().getAsInt());
		}
	}
}
