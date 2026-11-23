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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.LongArrayTag;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.chunk;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.varInt;
import static org.junit.jupiter.api.Assertions.*;

final class MapChunkWirePacketCodecsTest {
	@Test
	void viaNbtEditsUseSettersAndCannotMutatePacketStateThroughAliases() {
		for (int protocol : new int[] {110, 477, 755, 757, 764, 770}) {
			MapChunkWirePacket.Base packet = roundTrip(CLIENTBOUND, PacketType.Server.MAP_CHUNK, protocol,
				output -> chunk(output, protocol, true));
			if (protocol >= 477 && protocol < 770) {
				CompoundTag heights = packet.heightmapTag();
				heights.getLongArrayTag("H").getValue()[0] = 42;
				assertFalse(packet.couldHaveBeenMutated());
				assertEquals(0xfedcba9876543210L, packet.heightmapTag().getLongArrayTag("H").getValue()[0]);
				packet.setHeightmapTag(heights);
				heights.getLongArrayTag("H").getValue()[0] = 99;
				assertEquals(42, packet.heightmapTag().getLongArrayTag("H").getValue()[0]);
			}

			CompoundTag tag = new CompoundTag();
			CompoundTag nested = new CompoundTag();
			nested.put("values", new LongArrayTag(new long[] {7}));
			tag.put("nested", nested);
			if (protocol < 757) {
				packet.setBlockEntityTags(Arrays.asList(tag, null));
				packet.blockEntityTags().get(0).getCompoundTag("nested").getLongArrayTag("values").getValue()[0] = 99;
				assertThrows(UnsupportedOperationException.class, () -> packet.blockEntityTags().clear());
			} else {
				ChunkBlockEntity entity = new ChunkBlockEntity(0x12, -32, 300, tag);
				packet.setBlockEntities(Collections.singletonList(entity));
				entity.tag().getCompoundTag("nested").getLongArrayTag("values").getValue()[0] = 99;
			}
			nested.getLongArrayTag("values").getValue()[0] = 100;
			assertTrue(packet.couldHaveBeenMutated());

			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(CLIENTBOUND, PLAY, PacketType.Server.MAP_CHUNK, protocol, packet, output);
				MapChunkWirePacket.Base decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, output);
				if (protocol >= 477 && protocol < 770) {
					assertEquals(42, decoded.heightmapTag().getLongArrayTag("H").getValue()[0]);
				}
				CompoundTag decodedTag;
				if (protocol < 757) {
					assertNull(decoded.blockEntityTags().get(1));
					decodedTag = decoded.blockEntityTags().get(0);
				} else {
					decodedTag = decoded.blockEntities().get(0).tag();
				}
				assertArrayEquals(new long[] {7}, decodedTag.getCompoundTag("nested").getLongArrayTag("values").getValue());
				assertFalse(decoded.couldHaveBeenMutated());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void completeChunkEnvelopesRoundTripAcrossEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 47) continue;
			for (boolean full : new boolean[] {false, true}) {
				MapChunkWirePacket.Base packet = roundTrip(CLIENTBOUND, PacketType.Server.MAP_CHUNK, protocol, output -> chunk(output, protocol, full));
				assertEquals(-12, packet.chunkX(0));
				assertEquals(27, packet.chunkZ(0));
				assertEquals(protocol >= 755 || full, packet.fullChunk());
				assertEquals(2, packet.chunkData().sections().size());
				ChunkSection section = packet.chunkData().sections().get(1);
				assertEquals(16, section.blockStates().value(18));
				assertEquals(256, section.blockStates().value(16));
				if (protocol >= 477) assertEquals(3855, section.nonAirCount());
				if (protocol >= 775) assertEquals(37, section.fluidCount());
				if (protocol >= 757) assertEquals(206, section.biomes().value(6));
				if (protocol < 477) assertEquals(0x35, section.skyLight()[0]);
				if (protocol < 755) assertArrayEquals(new long[] {0x8001}, packet.sectionMask());
				else if (protocol < 757) assertArrayEquals(new long[] {1, 2}, packet.sectionMask());
				if (protocol >= 573 && protocol < 751 && full) {
					assertEquals(1024, packet.biomeIds().length);
					assertEquals(1023, packet.biomeIds()[1023]);
				}
				if (protocol >= 751 && protocol < 757 && (full || protocol >= 755)) {
					assertArrayEquals(new int[] {0, 127, 300}, packet.biomeIds());
				}
				if (protocol >= 770) {
					assertArrayEquals(new long[] {-1, 1234}, packet.heightmaps().get(1));
					assertArrayEquals(new long[] {Long.MIN_VALUE}, packet.heightmaps().get(4));
				}
				if (protocol >= 757) {
					assertEquals(2, packet.blockEntities().size());
					assertEquals(0xf1, packet.blockEntities().get(0).packedXZ());
					assertEquals(-64, packet.blockEntities().get(0).y());
					assertEquals(300, packet.blockEntities().get(0).typeId());
					assertNull(packet.blockEntities().get(0).tag());
					assertNotNull(packet.blockEntities().get(1).tag());
					assertArrayEquals(new long[] {1, 2}, packet.lightData().skyMask());
				} else if (protocol >= 110) {
					assertEquals(2, packet.blockEntityTags().size());
					assertNotNull(packet.blockEntityTags().get(0));
					assertNull(packet.blockEntityTags().get(1));
				}
				assertEquals(protocol >= 735 && protocol < 751, packet.ignoreOldData());
				assertEquals(protocol >= 757 && protocol < 763, packet.trustEdges());
				assertFalse(packet.couldHaveBeenMutated());
			}
		}
	}

	@Test
	void newlyCreatedPacketsEncodeTheirFieldsAndDefensivelyCopyArrays() {
		for (int protocol : new int[] {47, 107, 110, 477, 573, 735, 751, 755, 757, 763, 764, 770, 777}) {
			MapChunkWirePacket.Base packet = WirePackets.createPlayPacket(PacketType.Server.MAP_CHUNK, protocol);
			packet.setChunkCoordinates(0, -30, 40);
			ChunkPalette blocks = ChunkPalette.single(ChunkPalette.Kind.BLOCK_STATES, 16);
			ChunkPalette biomes = protocol >= 757 ? ChunkPalette.single(ChunkPalette.Kind.BIOMES, 7) : null;
			ChunkSection section = new ChunkSection(4096, protocol >= 775 ? 0 : -1, blocks, biomes,
				protocol < 477 ? new byte[2048] : null, null);
			packet.setChunkData(new ChunkData(Arrays.asList(section, section), protocol < 573 ? new int[256] : new int[0]));
			assertThrows(UnsupportedOperationException.class, () -> packet.chunkData().sections().clear());
			if (protocol < 755) packet.setFullChunk(true);
			if (protocol < 757) packet.setSectionMask(new long[] {0x8001});
			if (protocol >= 573 && protocol < 751) packet.setBiomeIds(new int[1024]);
			if (protocol >= 751 && protocol < 757) packet.setBiomeIds(new int[] {1, 300});
			if (protocol >= 477 && protocol < 770) packet.setHeightmapTag(new CompoundTag());
			if (protocol >= 770) {
				long[] heights = {31};
				packet.setHeightmaps(Collections.singletonMap(1, heights));
				heights[0] = 99;
				packet.heightmaps().get(1)[0] = 99;
			}
			if (protocol >= 110 && protocol < 757) packet.setBlockEntityTags(Collections.singletonList(new CompoundTag()));
			if (protocol >= 757) {
				packet.setBlockEntities(Collections.singletonList(new ChunkBlockEntity(0x12, -32, 300, new CompoundTag())));
				packet.setLightData(new ChunkLightData());
			}
			if (protocol >= 735 && protocol < 751) packet.setIgnoreOldData(true);
			if (protocol >= 757 && protocol < 763) packet.setTrustEdges(true);
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(CLIENTBOUND, PLAY, PacketType.Server.MAP_CHUNK, protocol, packet, output);
				MapChunkWirePacket.Base decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, output);
				assertEquals(-30, decoded.chunkX(0));
				assertEquals(40, decoded.chunkZ(0));
				assertEquals(2, decoded.chunkData().sections().size());
				assertEquals(16, decoded.chunkData().sections().get(0).blockStates().value(4095));
				if (protocol >= 770) assertArrayEquals(new long[] {31}, decoded.heightmaps().get(1));
				if (protocol >= 757) assertEquals(-32, decoded.blockEntities().get(0).y());
				assertTrue(packet.couldHaveBeenMutated());
				assertFalse(decoded.couldHaveBeenMutated());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void unavailableFieldsAndInvalidLengthsAreRejected() {
		MapChunkWirePacket.Base modern = new MapChunkWirePacket.Protocol770();
		assertThrows(UnsupportedOperationException.class, () -> modern.setFullChunk(true));
		assertThrows(UnsupportedOperationException.class, () -> modern.setHeightmapTag(new CompoundTag()));
		assertThrows(NullPointerException.class, () -> modern.setChunkData(null));
		MapChunkWirePacket.Protocol573 fixed = new MapChunkWirePacket.Protocol573();
		assertThrows(IllegalArgumentException.class, () -> fixed.setBiomeIds(new int[3]));
		fixed.setFullChunk(true);
		ByteBuf output = Unpooled.buffer();
		try {
			assertThrows(IllegalArgumentException.class, () -> encodePayload(fixed, output));
			output.clear().writeInt(0).writeInt(0).writeBoolean(false).writeShort(0);
			varInt(output, Integer.MAX_VALUE);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MapChunkWirePacket.Protocol47.class, output));
			output.clear().writeInt(0).writeInt(0).writeByte(0);
			varInt(output, -1);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MapChunkWirePacket.Protocol770.class, output));
		} finally {
			output.release();
		}
	}
}
