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

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMapChunkPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChunkBlockEntity;
import de.jpx3.intave.share.ChunkData;
import de.jpx3.intave.share.ChunkLightData;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

public final class MapChunkWirePacket {
	private MapChunkWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol110.class,
			Protocol393.class,
			Protocol477.class,
			Protocol573.class,
			Protocol735.class,
			Protocol751.class,
			Protocol755.class,
			Protocol757.class,
			Protocol763.class,
			Protocol764.class,
			Protocol770.class,
			Protocol775.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableMapChunkPacket {
		protected final MapChunkFormat format;
		protected int x, z;
		protected boolean fullChunk, ignoreOldData, trustEdges;
		protected long[] sectionMask = new long[0];
		protected int[] biomeIds = new int[0];
		protected ChunkData chunkData = ChunkData.EMPTY;
		protected CompoundTag heightmapTag = new CompoundTag();
		protected Map<Integer, long[]> heightmaps = Collections.emptyMap();
		protected List<CompoundTag> blockEntityTags = Collections.emptyList();
		protected List<ChunkBlockEntity> blockEntities = Collections.emptyList();
		protected ChunkLightData lightData = new ChunkLightData();

		protected Base() {
			this(MapChunkFormat.REGISTRY_HEIGHTMAPS);
		}

		protected Base(MapChunkFormat format) {
			this.format = format;
		}

		protected Base(int x, int z, ChunkData chunkData) {
			this();
			this.x = x;
			this.z = z;
			this.chunkData = Objects.requireNonNull(chunkData, "chunkData");
		}

		protected static <T extends Base> StreamCodec<ByteBuf, ByteBuf, T> streamCodec(Supplier<T> factory) {
			return StreamCodec.of((output, packet) -> packet.write(output), input -> {
				T packet = factory.get();
				packet.read(input);
				return packet;
			});
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= format.minimumProtocol && protocolVersion < format.maximumProtocol;
		}

		@Override
		public int chunkX(int index) {
			checkIndex(index);
			return x;
		}

		@Override
		public int chunkZ(int index) {
			checkIndex(index);
			return z;
		}

		@Override
		public void setChunkCoordinates(int index, int x, int z) {
			markMutated();
			checkIndex(index);
			this.x = x;
			this.z = z;
		}

		/**
		 * Decoded sections, with column biomes for full chunks before 1.15.
		 */
		@Override
		public ChunkData chunkData() {
			return chunkData;
		}

		@Override
		public void setChunkData(ChunkData data) {
			markMutated();
			chunkData = Objects.requireNonNull(data, "chunkData");
		}

		public boolean fullChunk() {
			return !format.fullChunk || fullChunk;
		}

		public boolean ignoreOldData() {
			return ignoreOldData;
		}

		public boolean trustEdges() {
			return trustEdges;
		}

		public long[] sectionMask() {
			return sectionMask.clone();
		}

		public int[] biomeIds() {
			return biomeIds.clone();
		}

		/** Returns a detached tag; apply edits with {@link #setHeightmapTag(CompoundTag)}. */
		public CompoundTag heightmapTag() {
			return heightmapTag.copy();
		}

		public Map<Integer, long[]> heightmaps() {
			return copyHeightmaps(heightmaps);
		}

		/** Returns detached tags; apply edits with {@link #setBlockEntityTags(List)}. */
		public List<CompoundTag> blockEntityTags() {
			return copyTags(blockEntityTags);
		}

		public List<ChunkBlockEntity> blockEntities() {
			return blockEntities;
		}

		public ChunkLightData lightData() {
			return lightData;
		}

		public void setFullChunk(boolean value) {
			markMutated();
			require(format.fullChunk);
			fullChunk = value;
		}

		public void setIgnoreOldData(boolean value) {
			markMutated();
			require(format.ignoreOldData);
			ignoreOldData = value;
		}

		public void setTrustEdges(boolean value) {
			markMutated();
			require(format.trustEdges);
			trustEdges = value;
		}

		public void setSectionMask(long[] value) {
			markMutated();
			require(format.maskWidth != 0);
			if (format.maskWidth > 0) {
				scalarMask(value);
			}
			sectionMask = value.clone();
		}

		public void setBiomeIds(int[] value) {
			markMutated();
			require(format.biomeFormat != 0);
			if (format.biomeFormat == 1024 && value.length != 0 && value.length != 1024) {
				throw new IllegalArgumentException("Expected 1024 biome IDs");
			}
			biomeIds = value.clone();
		}

		public void setHeightmapTag(CompoundTag value) {
			markMutated();
			require(format.heightmapTag);
			heightmapTag = Objects.requireNonNull(value, "heightmapTag").copy();
		}

		public void setHeightmaps(Map<Integer, long[]> value) {
			markMutated();
			require(format.minimumProtocol >= 770);
			heightmaps = copyHeightmaps(value);
		}

		public void setBlockEntityTags(List<CompoundTag> value) {
			markMutated();
			require(format.blockEntities && !format.packedBlockEntities);
			blockEntityTags = copyTags(value);
		}

		public void setBlockEntities(List<ChunkBlockEntity> value) {
			markMutated();
			require(format.packedBlockEntities);
			List<ChunkBlockEntity> copy = new ArrayList<>(value);
			copy.forEach(entity -> Objects.requireNonNull(entity, "blockEntity"));
			blockEntities = Collections.unmodifiableList(copy);
		}

		public void setLightData(ChunkLightData value) {
			markMutated();
			require(format.light);
			lightData = Objects.requireNonNull(value, "lightData");
		}

		protected final void read(ByteBuf input) {
			x = input.readInt();
			z = input.readInt();
			if (format.fullChunk) {
				fullChunk = input.readBoolean();
			}
			if (format.ignoreOldData) {
				ignoreOldData = input.readBoolean();
			}
			if (format.maskWidth == -1) {
				sectionMask = ByteBufStreamCodecs.VARINT_LONG_ARRAY.decode(input);
			} else if (format.maskWidth > 0) {
				int mask = format.maskWidth == 16 ? input.readUnsignedShort() : ByteBufStreamCodecs.VARINT.decode(input);
				sectionMask = mask == 0 ? new long[0] : new long[]{mask};
				scalarMask(sectionMask);
			}
			StreamCodec<ByteBuf, ByteBuf, CompoundTag> tags = tagCodec();
			if (format.heightmapTag) {
				heightmapTag = Objects.requireNonNull(tags.decode(input), "heightmapTag");
			}
			if (format.minimumProtocol >= 770) {
				int count = ByteBufStreamCodecs.count(input, input.readableBytes() / 2);
				Map<Integer, long[]> maps = new LinkedHashMap<>();
				for (int i = 0; i < count; i++) {
					int type = ByteBufStreamCodecs.VARINT.decode(input);
					if (type < 0 || maps.containsKey(type)) {
						throw new IllegalArgumentException("Invalid or duplicate heightmap type");
					}
					maps.put(type, ByteBufStreamCodecs.VARINT_LONG_ARRAY.decode(input));
				}
				heightmaps = Collections.unmodifiableMap(maps);
			}
			if (hasBiomeArray()) {
				int count = format.biomeFormat == 1024 ? 1024 : ByteBufStreamCodecs.count(input, input.readableBytes());
				if (count > input.readableBytes() / (format.biomeFormat == 1024 ? 4 : 1)) {
					throw new IllegalArgumentException("Truncated biome array");
				}
				biomeIds = new int[count];
				for (int i = 0; i < count; i++) {
					biomeIds[i] = format.biomeFormat == 1024 ? input.readInt() : ByteBufStreamCodecs.VARINT.decode(input);
				}
			}
			chunkData = sectionCodec().decode(input);
			if (format.packedBlockEntities) {
				blockEntities = readList(input, entityCodec(), 5);
			} else if (format.blockEntities) {
				blockEntityTags = readList(input, tags, 1);
			}
			if (format.trustEdges) {
				trustEdges = input.readBoolean();
			}
			if (format.light) {
				lightData = ChunkLightData.STREAM_CODEC.decode(input);
			}
		}

		protected final void write(ByteBuf output) {
			if (format.biomeFormat == 1024 && hasBiomeArray() && biomeIds.length != 1024) {
				throw new IllegalArgumentException("Full chunks require 1024 biome IDs");
			}
			if (!hasBiomeArray() && biomeIds.length != 0) {
				throw new IllegalArgumentException("Partial chunks cannot carry biome arrays");
			}
			output.writeInt(x).writeInt(z);
			if (format.fullChunk) {
				output.writeBoolean(fullChunk);
			}
			if (format.ignoreOldData) {
				output.writeBoolean(ignoreOldData);
			}
			if (format.maskWidth == -1) {
				ByteBufStreamCodecs.VARINT_LONG_ARRAY.encode(output, sectionMask);
			} else if (format.maskWidth == 16) {
				output.writeShort(scalarMask(sectionMask));
			} else if (format.maskWidth == 32) {
				ByteBufStreamCodecs.VARINT.encode(output, scalarMask(sectionMask));
			}
			if (format.heightmapTag) {
				tagCodec().encode(output, heightmapTag);
			}
			if (format.minimumProtocol >= 770) {
				ByteBufStreamCodecs.VARINT.encode(output, heightmaps.size());
				heightmaps.forEach((type, values) -> {
					ByteBufStreamCodecs.VARINT.encode(output, type);
					ByteBufStreamCodecs.VARINT_LONG_ARRAY.encode(output, values);
				});
			}
			if (hasBiomeArray()) {
				if (format.biomeFormat != 1024) {
					ByteBufStreamCodecs.VARINT.encode(output, biomeIds.length);
				}
				for (int biome : biomeIds) {
					if (format.biomeFormat == 1024) {
						output.writeInt(biome);
					} else {
						ByteBufStreamCodecs.VARINT.encode(output, biome);
					}
				}
			}
			sectionCodec().encode(output, chunkData);
			if (format.packedBlockEntities) {
				writeList(output, blockEntities, entityCodec());
			} else if (format.blockEntities) {
				writeList(output, blockEntityTags, tagCodec());
			}
			if (format.trustEdges) {
				output.writeBoolean(trustEdges);
			}
			if (format.light) {
				ChunkLightData.STREAM_CODEC.encode(output, lightData);
			}
		}

		protected boolean hasBiomeArray() {
			return format.biomeFormat != 0 && (!format.fullChunk || fullChunk);
		}

		protected StreamCodec<ByteBuf, ByteBuf, CompoundTag> tagCodec() {
			return format.namedNbt ? ByteBufStreamCodecs.NAMED_COMPOUND_TAG : ByteBufStreamCodecs.COMPOUND_TAG;
		}

		protected StreamCodec<ByteBuf, ByteBuf, ChunkBlockEntity> entityCodec() {
			return format.namedNbt ? ChunkBlockEntity.NAMED_NBT_STREAM_CODEC : ChunkBlockEntity.STREAM_CODEC;
		}

		protected static <T> List<T> readList(ByteBuf input, StreamCodec<ByteBuf, ByteBuf, T> codec, int minimumBytes) {
			int count = ByteBufStreamCodecs.count(input, input.readableBytes() / minimumBytes);
			List<T> values = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				values.add(codec.decode(input));
			}
			return Collections.unmodifiableList(values);
		}

		protected static <T> void writeList(ByteBuf output, List<T> values, StreamCodec<ByteBuf, ByteBuf, T> codec) {
			ByteBufStreamCodecs.VARINT.encode(output, values.size());
			for (T value : values) {
				codec.encode(output, value);
			}
		}

		protected static List<CompoundTag> copyTags(List<CompoundTag> tags) {
			List<CompoundTag> copy = new ArrayList<>(tags.size());
			for (CompoundTag tag : tags) copy.add(tag == null ? null : tag.copy());
			return Collections.unmodifiableList(copy);
		}

		protected static Map<Integer, long[]> copyHeightmaps(Map<Integer, long[]> values) {
			Map<Integer, long[]> copy = new LinkedHashMap<>();
			values.forEach((type, data) -> {
				if (type < 0) {
					throw new IllegalArgumentException("Negative heightmap type");
				}
				copy.put(type, data.clone());
			});
			return Collections.unmodifiableMap(copy);
		}

		protected StreamCodec<ByteBuf, ByteBuf, ChunkData> sectionCodec() {
			int count = 0;
			for (long word : sectionMask) {
				count += Long.bitCount(word);
			}
			return ChunkData.streamCodec(format.minimumProtocol, format.maskWidth == 0 ? -1 : count, fullChunk());
		}

		protected static int scalarMask(long[] mask) {
			if (mask.length > 1 || mask.length == 1 && (mask[0] & ~0xffffL) != 0) {
				throw new IllegalArgumentException("Legacy chunk masks have 16 bits");
			}
			return mask.length == 0 ? 0 : (int) mask[0];
		}

		protected static void require(boolean supported) {
			if (!supported) {
				throw new UnsupportedOperationException("Field is absent in this chunk packet format");
			}
		}

		protected static void checkIndex(int index) {
			if (index != 0) {
				throw new IndexOutOfBoundsException("Chunk index: " + index);
			}
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = streamCodec(Protocol47::new);

		public Protocol47() {
			super(MapChunkFormat.LEGACY_MASK);
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC = streamCodec(Protocol107::new);

		public Protocol107() {
			super(MapChunkFormat.VARINT_MASK);
		}
	}

	static final class Protocol110 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol110> STREAM_CODEC = streamCodec(Protocol110::new);

		public Protocol110() {
			super(MapChunkFormat.BLOCK_ENTITIES);
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC = streamCodec(Protocol393::new);

		public Protocol393() {
			super(MapChunkFormat.FLATTENED);
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = streamCodec(Protocol477::new);

		public Protocol477() {
			super(MapChunkFormat.HEIGHTMAPS);
		}
	}

	static final class Protocol573 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol573> STREAM_CODEC = streamCodec(Protocol573::new);

		public Protocol573() {
			super(MapChunkFormat.BIOMES);
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = streamCodec(Protocol735::new);

		public Protocol735() {
			super(MapChunkFormat.IGNORE_OLD_DATA);
		}
	}

	static final class Protocol751 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC = streamCodec(Protocol751::new);

		public Protocol751() {
			super(MapChunkFormat.VARINT_BIOMES);
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = streamCodec(Protocol755::new);

		public Protocol755() {
			super(MapChunkFormat.BITSET_MASK);
		}
	}

	static final class Protocol757 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol757> STREAM_CODEC = streamCodec(Protocol757::new);

		public Protocol757() {
			super(MapChunkFormat.LIGHT);
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = streamCodec(Protocol763::new);

		public Protocol763() {
			super(MapChunkFormat.WITHOUT_TRUST_EDGES);
		}
	}

	static final class Protocol764 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = streamCodec(Protocol764::new);

		public Protocol764() {
			super(MapChunkFormat.UNNAMED_NBT);
		}
	}

	static final class Protocol770 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = streamCodec(Protocol770::new);

		public Protocol770() {
			super();
		}

		protected Protocol770(MapChunkFormat format) {
			super(format);
		}

		public Protocol770(int x, int z, ChunkData chunkData) {
			super(x, z, chunkData);
		}
	}

	static final class Protocol775 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol775> STREAM_CODEC = streamCodec(Protocol775::new);

		public Protocol775() {
			super(MapChunkFormat.FLUID_COUNTS);
		}
	}
}
