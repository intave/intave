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
import de.jpx3.intave.packet.contract.mutable.MutableChunksBiomesPacket;
import de.jpx3.intave.packet.contract.mutable.MutableLightUpdatePacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchFinishedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchReceivedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchStartWirePacket;
import de.jpx3.intave.share.ChunkBiomeData;
import de.jpx3.intave.share.ChunkLightData;
import de.jpx3.intave.share.ChunkPalette;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.palette;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.varInt;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkControlWirePacketCodecsTest {
	@Test
	void chunkBatchesUseEmptyVarIntAndFloatPayloads() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 764) {
				assertUnsupported(CLIENTBOUND, PacketType.Server.CHUNK_BATCH_START, protocol);
				assertUnsupported(CLIENTBOUND, PacketType.Server.CHUNK_BATCH_FINISHED, protocol);
				assertUnsupported(SERVERBOUND, PacketType.Client.CHUNK_BATCH_RECEIVED, protocol);
				continue;
			}
			ChunkBatchStartWirePacket start = roundTrip(CLIENTBOUND, PacketType.Server.CHUNK_BATCH_START, protocol, output -> {});
			ChunkBatchFinishedWirePacket end = roundTrip(CLIENTBOUND, PacketType.Server.CHUNK_BATCH_FINISHED, protocol, output -> varInt(output, 300));
			ChunkBatchReceivedWirePacket received = roundTrip(SERVERBOUND, PacketType.Client.CHUNK_BATCH_RECEIVED, protocol, output -> output.writeFloat(12.5F));
			assertEquals(300, end.batchSize());
			assertEquals(12.5F, received.desiredChunksPerTick());
			assertFalse(start.couldHaveBeenMutated());
		}
		assertNotSame(WirePackets.createPlayPacket(PacketType.Server.CHUNK_BATCH_START, 764),
			WirePackets.createPlayPacket(PacketType.Server.CHUNK_BATCH_START, 764));
	}

	@Test
	void biomeUpdatesUsePackedChunkCoordinatesAndLengthDelimitedData() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 762) {
				assertUnsupported(CLIENTBOUND, PacketType.Server.CHUNKS_BIOMES, protocol);
				continue;
			}
			MutableChunksBiomesPacket packet = roundTrip(CLIENTBOUND, PacketType.Server.CHUNKS_BIOMES, protocol, output -> {
				output.writeByte(2).writeInt(27).writeInt(-12);
				ByteBuf data = Unpooled.buffer();
				try {
					palette(data, protocol, true);
					varInt(output, data.readableBytes());
					output.writeBytes(data);
				} finally {
					data.release();
				}
				output.writeInt(-2).writeInt(99).writeByte(0);
			});
			assertEquals(2, packet.chunkCount());
			assertEquals(-12, packet.chunkX(0));
			assertEquals(27, packet.chunkZ(0));
			assertEquals(99, packet.chunkX(1));
			assertEquals(-2, packet.chunkZ(1));
			assertEquals(1, packet.chunks().get(0).sections().size());
			assertEquals(206, packet.chunks().get(0).sections().get(0).value(6));
			assertFalse(packet.couldHaveBeenMutated());
		}
	}

	@Test
	void biomeAndLightValuesDoNotLeakMutableStorage() {
		byte[] bytes = {1, 2};
		List<ChunkBiomeData> chunks = new ArrayList<>();
		List<ChunkPalette> sections = new ArrayList<>();
		sections.add(ChunkPalette.single(ChunkPalette.Kind.BIOMES, 7));
		chunks.add(new ChunkBiomeData(1, 2, sections));
		MutableChunksBiomesPacket packet = WirePackets.createPlayPacket(PacketType.Server.CHUNKS_BIOMES, 770);
		packet.setChunks(chunks);
		chunks.clear();
		bytes[0] = 9;
		sections.clear();
		assertEquals(7, packet.chunks().get(0).sections().get(0).value(63));
		assertThrows(UnsupportedOperationException.class, () -> packet.chunks().get(0).sections().clear());
		assertThrows(UnsupportedOperationException.class, () -> packet.chunks().clear());
		packet.setChunkCoordinates(0, -3, 4);
		ByteBuf output = Unpooled.buffer();
		try {
			encodePayload(packet, output);
			MutableChunksBiomesPacket decoded = decodePayload(packet.getClass(), output);
			assertEquals(-3, decoded.chunkX(0));
			assertEquals(4, decoded.chunkZ(0));
			long[] sky = {1};
			ChunkLightData data = new ChunkLightData(sky, new long[0], new long[0], new long[0], Collections.singletonList(bytes), Collections.emptyList());
			sky[0] = 0;
			data.skyMask()[0] = 0;
			data.skyUpdates().get(0)[1] = 99;
			assertArrayEquals(new long[] {1}, data.skyMask());
			assertEquals(2, data.skyUpdates().get(0)[1]);
			MutableLightUpdatePacket light = WirePackets.createPlayPacket(PacketType.Server.LIGHT_UPDATE, 763);
			light.setLightData(data);
			light.setChunkCoordinates(0, -100, 200);
			output.clear();
			encodePayload(light, output);
			MutableLightUpdatePacket reread = decodePayload(light.getClass(), output);
			assertEquals(-100, reread.chunkX(0));
			assertArrayEquals(new long[] {1}, reread.lightData().skyMask());
			assertTrue(light.couldHaveBeenMutated());
		} finally {
			output.release();
		}
	}

	@Test
	void malformedBiomeLengthsAndLightCountsAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeByte(1).writeLong(0);
			varInt(input, 2097153);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(WirePackets.createPlayPacket(PacketType.Server.CHUNKS_BIOMES, 770).getClass(), input));
			input.clear();
			input.writeByte(1).writeLong(1); // sky mask requires one array
			input.writeByte(0).writeByte(0).writeByte(0).writeByte(0).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> ChunkLightData.STREAM_CODEC.decode(input));
			input.clear();
			varInt(input, 1 << 18);
			assertThrows(IllegalArgumentException.class, () -> ChunkLightData.LEGACY_STREAM_CODEC.decode(input));
		} finally {
			input.release();
		}
	}
}
