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

package de.jpx3.intave.packet.wire.chunk.data;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MapChunkWirePacketCodecsTest {
	@Test
	void chunkCoordinatesDoNotRequireParsingTheOpaqueChunkPayload() {
		byte[] data = new byte[] {10, 0, 3, 7, 1, 0, 0};
		MapChunkWirePacket chunk =
				roundTrip(
						CLIENTBOUND,
						"MAP_CHUNK",
						768,
						b -> b.writeInt(-12).writeInt(27).writeBytes(data));
		assertEquals(-12, chunk.chunkX(0));
		assertEquals(27, chunk.chunkZ(0));
		assertArrayEquals(data, chunk.chunkData());
	}
}
