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

package de.jpx3.intave.packet.wire.chunk;

import de.jpx3.intave.packet.contract.PacketType;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.light;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.varInt;
import static org.junit.jupiter.api.Assertions.*;

final class LightUpdateWirePacketCodecsTest {
	@Test
	void lightMasksFlagsAndArrayCountsMatchEveryVersion() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 477) {
				assertUnsupported(CLIENTBOUND, PacketType.Server.LIGHT_UPDATE, protocol);
				continue;
			}
			LightUpdateWirePacket.Base packet = roundTrip(CLIENTBOUND, PacketType.Server.LIGHT_UPDATE, protocol, output -> {
				varInt(output, -12);
				varInt(output, 27);
				if (protocol >= 735 && protocol < 763) output.writeBoolean(true);
				light(output, protocol < 755);
			});
			assertEquals(-12, packet.chunkX(0));
			assertEquals(27, packet.chunkZ(0));
			assertArrayEquals(protocol < 755 ? new long[] {0x20001} : new long[] {1, 2}, packet.lightData().skyMask());
			assertArrayEquals(new long[] {2}, packet.lightData().blockMask());
			assertEquals(2, packet.lightData().skyUpdates().size());
			assertEquals(2048, packet.lightData().blockUpdates().get(0).length);
			assertEquals(0x56, packet.lightData().blockUpdates().get(0)[2047]);
			if (protocol >= 735 && protocol < 763) {
				assertTrue(((LightUpdateWirePacket.Protocol755) packet).trustEdges());
			}
			assertFalse(packet.couldHaveBeenMutated());
		}
	}
}
