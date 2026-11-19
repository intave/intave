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

package de.jpx3.intave.packet.wire.chunk.bulk;

import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.share.ChunkBulkData;
import de.jpx3.intave.share.ChunkData;
import de.jpx3.intave.share.ChunkPalette;
import de.jpx3.intave.share.ChunkSection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class MapChunkBulkWirePacketCodecsTest {
	@Test
	void bulkChunksSeparateAllHeadersFromTheirExactPayloads() {
		for (boolean skyLight : new boolean[] {false, true}) {
			byte[] first = new byte[2 * (8192 + 2048 + (skyLight ? 2048 : 0)) + 256];
			byte[] second = new byte[256];
			Arrays.fill(first, (byte) 0x12);
			Arrays.fill(second, (byte) 0x34);
			MapChunkBulkWirePacket packet = roundTrip(CLIENTBOUND, PacketType.Server.MAP_CHUNK_BULK, 47, output -> {
				output.writeBoolean(skyLight).writeByte(2);
				output.writeInt(-12).writeInt(27).writeShort(0x8001);
				output.writeInt(99).writeInt(-100).writeShort(0);
				output.writeBytes(first).writeBytes(second);
			});
			assertEquals(2, packet.chunkCount());
			assertEquals(skyLight, packet.skyLight());
			assertEquals(0x8001, packet.chunks().get(0).sectionMask());
			assertEquals(2, packet.chunks().get(0).data().sections().size());
			assertEquals(0x1212, packet.chunks().get(0).data().sections().get(1).blockStates().value(4095));
			assertEquals(0x34, packet.chunks().get(1).data().biomeIds()[255]);
			assertFalse(packet.couldHaveBeenMutated());
		}
	}

	@Test
	void mutationsPreservePayloadsAndMalformedLengthsAreRejected() {
		int[] biomes = new int[256];
		MapChunkBulkWirePacket packet = new MapChunkBulkWirePacket(false,
			Collections.singletonList(new ChunkBulkData(0, 0, 0, new ChunkData(Collections.emptyList(), biomes))));
		biomes[0] = 99;
		packet.chunks().get(0).data().biomeIds()[0] = 99;
		packet.setChunkCoordinates(0, -12, 27);
		ByteBuf input = Unpooled.buffer();
		try {
			encodePayload(packet, input);
			MapChunkBulkWirePacket decoded = decodePayload(MapChunkBulkWirePacket.class, input);
			assertEquals(-12, decoded.chunkX(0));
			assertEquals(27, decoded.chunkZ(0));
			assertEquals(0, decoded.chunks().get(0).data().biomeIds()[0]);
			input.clear().writeBoolean(false).writeByte(1).writeInt(0).writeInt(0).writeShort(0).writeZero(255);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MapChunkBulkWirePacket.class, input));
			assertThrows(IllegalArgumentException.class, () -> packet.setChunks(true,
				Collections.singletonList(new ChunkBulkData(0, 0, 1, new ChunkData(Collections.singletonList(
					new ChunkSection(-1, -1, ChunkPalette.single(ChunkPalette.Kind.BLOCK_STATES, 0), null, new byte[2048], null)
				), new int[256])))));
			assertEquals(-12, packet.chunkX(0));
			assertThrows(IllegalArgumentException.class, () -> new MapChunkBulkWirePacket(false, new int[0], new int[1], new int[0], new byte[0]));
		} finally {
			input.release();
		}
	}
}
