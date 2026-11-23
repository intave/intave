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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import de.jpx3.intave.share.PlayerHand;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class BlockInteractionWirePacketCodecsTest {
	@Test
	void legacyPlacementKeepsItsItemAndOnlyTheAirSentinelStartsItemUse() {
		for (int face : new int[]{0, 1, 5, 6, 254, 255}) {
			for (boolean empty : new boolean[]{false, true}) {
				MutableBlockInteractionPacket packet = roundTrip(SERVERBOUND, PacketType.Client.USE_ITEM_ON, 47, out -> {
					out.writeLong(-1).writeByte(face);
					if (empty) out.writeShort(-1);
					else {
						out.writeShort(276).writeByte(1).writeShort(7);
						// Named compound with one string tag: the following hit coordinates must stay aligned.
						out.writeByte(10).writeShort(0).writeByte(8).writeShort(1).writeByte('x');
						out.writeShort(1).writeByte('y').writeByte(0);
					}
					out.writeByte(8).writeByte(4).writeByte(255);
				});
				assertEquals(PlayerHand.MAIN_HAND, packet.hand());
				assertEquals(face == 255, packet.isItemUseRequest());
				assertEquals(face == 255, packet.isDirectItemUseRequest());
				assertEquals(0.5, packet.hitPosition().x());
				assertEquals(0.25, packet.hitPosition().y());
				assertEquals(255.0 / 16, packet.hitPosition().z());
				assertThrows(IllegalArgumentException.class, () -> packet.setHand(PlayerHand.OFF_HAND));
				assertTrue(packet.sequenceNumber().isEmpty());
			}
		}
	}

	@Test
	void blockInteractionWorksWithoutTheOldItemStackField() {
		MutableBlockInteractionPacket old =
				roundTrip(
						SERVERBOUND,
						PacketType.Client.USE_ITEM_ON,
						107,
						b -> {
							b.writeLong(0).writeByte(1).writeByte(0);
							b.writeByte(8).writeByte(4).writeByte(12);
						});
		assertEquals(0.5, old.hitPosition().x());
		MutableBlockInteractionPacket modern =
				roundTrip(
						SERVERBOUND,
						PacketType.Client.USE_ITEM_ON,
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
				() -> new BlockInteractionWirePacket.Protocol107().setSequenceNumber(1));
	}
}
