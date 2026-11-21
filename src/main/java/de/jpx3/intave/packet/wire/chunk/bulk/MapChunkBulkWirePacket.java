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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMapChunkBulkPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChunkBulkData;
import de.jpx3.intave.share.ChunkSection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapChunkBulkWirePacket extends AbstractMutable implements MutableMapChunkBulkPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MapChunkBulkWirePacket> STREAM_CODEC = StreamCodec.of(
		(output, packet) -> {
			output.writeBoolean(packet.skyLight);
			ByteBufStreamCodecs.VARINT.encode(output, packet.chunks.size());
			for (ChunkBulkData chunk : packet.chunks) {
				output.writeInt(chunk.x()).writeInt(chunk.z()).writeShort(chunk.sectionMask());
			}
			for (ChunkBulkData chunk : packet.chunks) ChunkBulkData.streamCodec(chunk.x(), chunk.z(), chunk.sectionMask(), packet.skyLight).encode(output, chunk);
		}, input -> {
			boolean skyLight = input.readBoolean();
			int count = ByteBufStreamCodecs.count(input, input.readableBytes() / 10);
			int[] x = new int[count], z = new int[count], masks = new int[count];
			for (int i = 0; i < count; i++) {
				x[i] = input.readInt();
				z[i] = input.readInt();
				masks[i] = input.readUnsignedShort();
			}
			return new MapChunkBulkWirePacket(skyLight, readChunks(input, skyLight, x, z, masks));
		}
	);

	private boolean skyLight;
	private List<ChunkBulkData> chunks;

	public MapChunkBulkWirePacket() { this(false, Collections.emptyList()); }

	public MapChunkBulkWirePacket(boolean skyLight, List<ChunkBulkData> chunks) {
		this.chunks = checkedChunks(skyLight, chunks);
		this.skyLight = skyLight;
	}

	public MapChunkBulkWirePacket(boolean skyLight, int[] x, int[] z, int[] masks, byte[] data) {
		ByteBuf input = Unpooled.wrappedBuffer(data);
		try {
			this.chunks = readChunks(input, skyLight, x, z, masks);
			if (input.isReadable()) throw new IllegalArgumentException("Trailing bulk chunk data");
			this.skyLight = skyLight;
		} finally {
			input.release();
		}
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 47 && protocolVersion < 107; }
	@Override
	public int chunkCount() { return chunks.size(); }
	@Override
	public int chunkX(int index) { return chunks.get(index).x(); }
	@Override
	public int chunkZ(int index) { return chunks.get(index).z(); }
	@Override
	public boolean skyLight() { return skyLight; }
	@Override
	public List<ChunkBulkData> chunks() { return chunks; }

	@Override
	public void setChunks(boolean skyLight, List<ChunkBulkData> chunks) {
		markMutated();
		this.chunks = checkedChunks(skyLight, chunks);
		this.skyLight = skyLight;
	}

	@Override
	public void setChunkCoordinates(int index, int x, int z) {
		markMutated();
		ChunkBulkData previous = chunks.get(index);
		List<ChunkBulkData> copy = new ArrayList<>(chunks);
		copy.set(index, new ChunkBulkData(x, z, previous.sectionMask(), previous.data()));
		chunks = Collections.unmodifiableList(copy);
	}

	private static List<ChunkBulkData> readChunks(ByteBuf input, boolean skyLight, int[] x, int[] z, int[] masks) {
		if (x.length != z.length || x.length != masks.length) throw new IllegalArgumentException("Chunk header counts differ");
		List<ChunkBulkData> chunks = new ArrayList<>(x.length);
		for (int i = 0; i < x.length; i++) {
			chunks.add(ChunkBulkData.streamCodec(x[i], z[i], masks[i], skyLight).decode(input));
		}
		return Collections.unmodifiableList(chunks);
	}

	private static List<ChunkBulkData> checkedChunks(boolean skyLight, List<ChunkBulkData> chunks) {
		List<ChunkBulkData> copy = new ArrayList<>(chunks);
		for (ChunkBulkData chunk : copy) {
			for (ChunkSection section : chunk.data().sections()) {
				if (section.blockLight() == null || (section.skyLight() != null) != skyLight || section.biomes() != null) {
					throw new IllegalArgumentException("Chunk sections do not match the legacy light format");
				}
			}
		}
		return Collections.unmodifiableList(copy);
	}
}
