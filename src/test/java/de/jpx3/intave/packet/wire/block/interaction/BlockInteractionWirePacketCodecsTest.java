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

package de.jpx3.intave.packet.wire.block.interaction;

import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BlockInteractionWirePacketCodecsTest {
	@Test
	void blockInteractionWorksWithoutTheOldItemStackField() {
		MutableBlockInteractionPacket old =
				roundTrip(
						SERVERBOUND,
						"USE_ITEM_ON",
						107,
						b -> {
							b.writeLong(0).writeByte(1).writeByte(0);
							b.writeByte(8).writeByte(4).writeByte(12);
						});
		assertEquals(0.5, old.hitPosition().x());
		MutableBlockInteractionPacket modern =
				roundTrip(
						SERVERBOUND,
						"USE_ITEM_ON",
						768,
						b -> {
							b.writeByte(0).writeLong(0).writeByte(1);
							b.writeFloat(0.5f).writeFloat(0.25f).writeFloat(0.75f);
							b.writeBoolean(false).writeBoolean(true).writeByte(42);
						});
		assertEquals(0.5, modern.hitPosition().x());
		assertEquals(42, modern.sequenceNumber().getAsInt());
	}

	@Test
	void olderVariantsRejectFieldsTheirFormatCannotEncode() {
		assertThrows(
				IllegalStateException.class,
				() -> new BlockInteractionProtocol107WirePacket().setSequenceNumber(1));
	}
}
