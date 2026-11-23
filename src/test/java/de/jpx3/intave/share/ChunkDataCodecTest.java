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

package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableMapChunkPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.*;
import static de.jpx3.intave.share.ChunkPalette.Kind.BIOMES;
import static de.jpx3.intave.share.ChunkPalette.Kind.BLOCK_STATES;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkDataCodecTest {
	@Test
	void legacySkyLightAndColumnBiomeLayoutsRoundTripWithAndWithoutSkyLight() {
		for (int protocol : new int[] {47, 107, 110, 340, 393, 404, 477}) {
			for (boolean full : new boolean[] {false, true}) for (boolean sky : new boolean[] {false, true}) {
				MutableMapChunkPacket packet = roundTrip(CLIENTBOUND, PacketType.Server.MAP_CHUNK, protocol, output -> chunk(output, protocol, full, sky));
				ChunkData data = packet.chunkData();
				assertEquals(full ? 256 : 0, data.biomeIds().length);
				if (full) assertEquals(protocol < 393 ? 255 : 511, data.biomeIds()[255]);
				for (int i = 0; i < 2; i++) {
					ChunkSection section = data.sections().get(i);
					assertEquals(256, section.blockStates().value(16));
					if (protocol < 477) {
						assertEquals(0x12 + i, section.blockLight()[0]);
						if (sky) assertEquals(0x34 + i, section.skyLight()[0]);
						else assertNull(section.skyLight());
					} else {
						assertNull(section.blockLight());
						assertNull(section.skyLight());
					}
				}
			}
		}
	}

	@Test
	void paletteEditsAreEncodedThroughTheChunkPacket() {
		MutableMapChunkPacket packet = roundTrip(CLIENTBOUND, PacketType.Server.MAP_CHUNK, 770, output -> chunk(output, 770, true));
		ChunkSection previous = packet.chunkData().sections().get(0);
		ChunkPalette blocks = previous.blockStates().withValue(4095, 30000, 15);
		ChunkPalette biomes = previous.biomes().withValue(63, 300, 9);
		packet.setChunkData(new ChunkData(Collections.singletonList(new ChunkSection(previous.nonAirCount(), blocks, biomes))));
		ByteBuf output = Unpooled.buffer();
		try {
			encodePayload(packet, output);
			MutableMapChunkPacket decoded = decodePayload(packet.getClass(), output);
			assertFalse(output.isReadable());
			assertEquals(30000, decoded.chunkData().sections().get(0).blockStates().value(4095));
			assertEquals(300, decoded.chunkData().sections().get(0).biomes().value(63));
			assertNotEquals(30000, previous.blockStates().value(4095));
			assertTrue(packet.couldHaveBeenMutated());
		} finally { output.release(); }
	}

	@Test
	void zeroSectionChunksAndLegacyUnloadRemainDistinct() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			input.writeByte(0);
			StreamCodec<ByteBuf, ByteBuf, ChunkData> codec = ChunkData.streamCodec(47, 0, true);
			ChunkData unload = codec.decode(input);
			assertTrue(unload.sections().isEmpty());
			assertEquals(0, unload.biomeIds().length);
			codec.encode(output, unload);
			assertEquals(0, output.readUnsignedByte());
			output.clear();
			codec.encode(output, new ChunkData(Collections.emptyList(), new int[256]));
			assertEquals(256, codec.decode(output).biomeIds().length);
			assertFalse(output.isReadable());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void sectionCountsAndBoundedPayloadsRejectMissingTrailingAndTruncatedData() {
		ByteBuf input = Unpooled.buffer(), body = Unpooled.buffer();
		try {
			sectionData(body, 770, true, false);
			int validSize = body.readableBytes();
			varInt(input, validSize + 1);
			input.writeBytes(body, 0, validSize).writeByte(0);
			assertThrows(RuntimeException.class, () -> ChunkData.streamCodec(770, -1, true).decode(input));
			input.clear();
			varInt(input, validSize - 1);
			input.writeBytes(body, 0, validSize - 1);
			assertThrows(RuntimeException.class, () -> ChunkData.streamCodec(770, -1, true).decode(input));
			input.clear();
			varInt(input, validSize + 1);
			input.writeBytes(body, 0, validSize);
			assertThrows(IllegalArgumentException.class, () -> ChunkData.streamCodec(770, -1, true).decode(input));
			input.clear();
			varInt(input, ChunkData.MAX_BYTES + 1);
			assertThrows(IllegalArgumentException.class, () -> ChunkData.streamCodec(770, -1, true).decode(input));
			input.clear().writeByte(0);
			assertThrows(RuntimeException.class, () -> ChunkData.streamCodec(755, 1, true).decode(input));
			input.clear();
			assertThrows(IllegalArgumentException.class, () -> ChunkData.streamCodec(755, 1, true).encode(input, ChunkData.EMPTY));
		} finally {
			input.release();
			body.release();
		}
	}

	@Test
	void modernSectionsValidateCountsAndRequiredPalettes() {
		ChunkPalette blocks = ChunkPalette.single(BLOCK_STATES, 0), biomes = ChunkPalette.single(BIOMES, 1);
		ByteBuf input = Unpooled.buffer();
		try {
			assertThrows(IllegalArgumentException.class, () -> new ChunkSection(4097, blocks, biomes));
			assertThrows(IllegalArgumentException.class, () -> new ChunkSection(0, 4097, blocks, biomes, null, null));
			assertThrows(IllegalArgumentException.class, () -> ChunkSection.streamCodec(775, false).encode(input, new ChunkSection(0, blocks, biomes)));
			input.clear();
			ChunkSection section = new ChunkSection(0, 2, blocks, biomes, null, null);
			ChunkSection.streamCodec(775, false).encode(input, section);
			assertEquals(0, input.getUnsignedShort(0));
			assertEquals(2, input.getUnsignedShort(2));
			ChunkSection decoded = ChunkSection.streamCodec(775, false).decode(input);
			assertEquals(2, decoded.fluidCount());
			assertFalse(input.isReadable());
			input.clear();
			assertThrows(NullPointerException.class, () -> ChunkSection.streamCodec(770, false).encode(input, new ChunkSection(0, blocks, null)));
		} finally { input.release(); }
	}

	@Test
	void sectionAndBiomeCollectionsDoNotExposeMutableState() {
		byte[] light = new byte[2048];
		light[0] = 12;
		ChunkSection section = new ChunkSection(-1, -1, ChunkPalette.single(BLOCK_STATES, 1), null, light, null);
		light[0] = 99;
		section.blockLight()[0] = 99;
		assertEquals(12, section.blockLight()[0]);
		int[] biomes = new int[256];
		biomes[0] = 7;
		ChunkData data = new ChunkData(Collections.singletonList(section), biomes);
		biomes[0] = 99;
		data.biomeIds()[0] = 99;
		assertEquals(7, data.biomeIds()[0]);
		assertThrows(UnsupportedOperationException.class, () -> data.sections().clear());
		assertThrows(IllegalArgumentException.class, () -> new ChunkData(Collections.emptyList(), new int[255]));
	}
}

