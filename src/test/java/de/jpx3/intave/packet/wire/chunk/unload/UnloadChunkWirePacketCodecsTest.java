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

package de.jpx3.intave.packet.wire.chunk.unload;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class UnloadChunkWirePacketCodecsTest {
	@Test
	void unloadingChunksUsesTheCorrectCoordinateOrder() {
		UnloadChunkWirePacket old =
				roundTrip(CLIENTBOUND, "UNLOAD_CHUNK", 763, b -> b.writeInt(12).writeInt(34));
		UnloadChunkWirePacket modern =
				roundTrip(CLIENTBOUND, "UNLOAD_CHUNK", 764, b -> b.writeInt(34).writeInt(12));
		assertEquals(12, old.chunkX(0));
		assertEquals(12, modern.chunkX(0));
		assertEquals(34, modern.chunkZ(0));
	}
}
