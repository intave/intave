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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableChunksBiomesPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChunkBiomeData;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Function;

public final class ChunksBiomesWirePacket {
	private ChunksBiomesWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol762.class,
			Protocol770.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableChunksBiomesPacket {
		protected static <T extends Base> StreamCodec<ByteBuf, ByteBuf, T> streamCodec(
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

		protected List<ChunkBiomeData> chunks;
		protected Base() { this(Collections.emptyList()); }

		protected Base(List<ChunkBiomeData> chunks) { this.chunks = copy(chunks); }

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

		protected static List<ChunkBiomeData> copy(List<ChunkBiomeData> chunks) {
			List<ChunkBiomeData> copy = new ArrayList<>(chunks);
			copy.forEach(chunk -> Objects.requireNonNull(chunk, "chunk"));
			return Collections.unmodifiableList(copy);
		}
	}

	static final class Protocol762 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol762> STREAM_CODEC =
			streamCodec(ChunkBiomeData.LENGTH_PREFIXED_STORAGE_STREAM_CODEC, Protocol762::new);

		public Protocol762() {}
		public Protocol762(List<ChunkBiomeData> chunks) { super(chunks); }

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 762 && protocolVersion < 770; }
	}

	static final class Protocol770 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = streamCodec(ChunkBiomeData.STREAM_CODEC, Protocol770::new);

		public Protocol770() {
			super();
		}

		public Protocol770(List<ChunkBiomeData> chunks) {
			super(chunks);
		}

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 770; }
	}
}
