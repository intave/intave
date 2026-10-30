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

package de.jpx3.intave.packet.wire.item.use;

import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UseItemWirePacketCodecsTest {
	@Test
	void itemUseAddsSequenceAndRotationWithoutChangingHand() {
		for (int protocol : new int[] {107, 759, 767}) {
			MutableBlockInteractionPacket packet =
					roundTrip(
							SERVERBOUND,
							"USE_ITEM",
							protocol,
							b -> {
								b.writeByte(1);
								if (protocol >= 759) b.writeByte(42);
								if (protocol >= 767) b.writeFloat(90).writeFloat(45);
							});
			assertEquals(1, packet.hand().ordinal());
			assertEquals(protocol >= 759, packet.sequenceNumber().isPresent());
		}
	}

	@Test
	void olderVariantsRejectFieldsTheirFormatCannotEncode() {
		assertThrows(
				IllegalStateException.class,
				() -> new UseItemProtocol107WirePacket().setSequenceNumber(1));
	}
}
