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

package de.jpx3.intave.packet.wire.recipe.place;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaceRecipeWirePacketCodecsTest {
	@Test
	void recipeReferencesKeepNumericAndKeyFormats() {
		for (int protocol : new int[] {340, 393, 768}) {
			PlaceRecipeWirePacket packet =
					roundTrip(
							SERVERBOUND,
							"AUTO_RECIPE",
							protocol,
							b -> {
								if (protocol == 768) ByteBufStreamCodecs.VARINT.encode(b, 300);
								else b.writeByte(3);
								if (protocol == 393)
									ByteBufStreamCodecs.VARINT_STRING.encode(b, "minecraft:test");
								else ByteBufStreamCodecs.VARINT.encode(b, 500);
								b.writeBoolean(true);
							});
			assertEquals(protocol == 768 ? 300 : 3, packet.containerId());
			assertTrue(packet.placeAll());
		}
	}
}
