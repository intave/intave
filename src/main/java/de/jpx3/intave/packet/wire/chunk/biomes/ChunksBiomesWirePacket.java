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

package de.jpx3.intave.packet.wire.chunk.biomes;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableChunksBiomesPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChunkBiomeData;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ChunksBiomesWirePacket extends AbstractMutable implements MutableChunksBiomesPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunksBiomesWirePacket> STREAM_CODEC = streamCodec(ChunkBiomeData.STREAM_CODEC, ChunksBiomesWirePacket::new);

	protected static <T extends ChunksBiomesWirePacket> StreamCodec<ByteBuf, ByteBuf, T> streamCodec(
		StreamCodec<ByteBuf, ByteBuf, ChunkBiomeData> chunkCodec, Function<List<ChunkBiomeData>, T> factory
	) {
		return StreamCodec.of(
			(output, packet) -> {
				ByteBufStreamCodecs.VARINT.encode(output, packet.chunks().size());
				for (ChunkBiomeData chunk : packet.chunks()) chunkCodec.encode(output, chunk);
			}, input -> {
				int count = ByteBufStreamCodecs.count(input, input.readableBytes() / 9);
				List<ChunkBiomeData> chunks = new ArrayList<>(count);
				for (int i = 0; i < count; i++) chunks.add(chunkCodec.decode(input));
				return factory.apply(chunks);
			}
		);
	}

	private List<ChunkBiomeData> chunks;

	public ChunksBiomesWirePacket() { this(Collections.emptyList()); }
	public ChunksBiomesWirePacket(List<ChunkBiomeData> chunks) { this.chunks = copy(chunks); }

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 770; }
	@Override
	public int chunkCount() { return chunks.size(); }
	@Override
	public int chunkX(int index) { return chunks.get(index).x(); }
	@Override
	public int chunkZ(int index) { return chunks.get(index).z(); }
	@Override
	public List<ChunkBiomeData> chunks() { return chunks; }

	@Override
	public void setChunks(List<ChunkBiomeData> chunks) {
		markMutated();
		this.chunks = copy(chunks);
	}

	@Override
	public void setChunkCoordinates(int index, int x, int z) {
		markMutated();
		List<ChunkBiomeData> copy = new ArrayList<>(chunks);
		copy.set(index, new ChunkBiomeData(x, z, chunks.get(index).sections()));
		chunks = Collections.unmodifiableList(copy);
	}

	private static List<ChunkBiomeData> copy(List<ChunkBiomeData> chunks) {
		List<ChunkBiomeData> copy = new ArrayList<>(chunks);
		copy.forEach(chunk -> Objects.requireNonNull(chunk, "chunk"));
		return Collections.unmodifiableList(copy);
	}
}
