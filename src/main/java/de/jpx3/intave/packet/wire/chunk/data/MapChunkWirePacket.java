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

public class MapChunkWirePacket extends AbstractMutable implements MutableMapChunkPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MapChunkWirePacket> STREAM_CODEC = streamCodec(MapChunkWirePacket::new);

	private final MapChunkFormat format;
	private int x, z;
	private boolean fullChunk, ignoreOldData, trustEdges;
	private long[] sectionMask = new long[0];
	private int[] biomeIds = new int[0];
	private ChunkData chunkData = ChunkData.EMPTY;
	private CompoundTag heightmapTag = new CompoundTag();
	private Map<Integer, long[]> heightmaps = Collections.emptyMap();
	private List<CompoundTag> blockEntityTags = Collections.emptyList();
	private List<ChunkBlockEntity> blockEntities = Collections.emptyList();
	private ChunkLightData lightData = new ChunkLightData();

	public MapChunkWirePacket() {
		this(MapChunkFormat.REGISTRY_HEIGHTMAPS);
	}

	protected MapChunkWirePacket(MapChunkFormat format) {
		this.format = format;
	}

	public MapChunkWirePacket(int x, int z, ChunkData chunkData) {
		this();
		this.x = x;
		this.z = z;
		this.chunkData = Objects.requireNonNull(chunkData, "chunkData");
	}

	protected static <T extends MapChunkWirePacket> StreamCodec<ByteBuf, ByteBuf, T> streamCodec(Supplier<T> factory) {
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

	private boolean hasBiomeArray() {
		return format.biomeFormat != 0 && (!format.fullChunk || fullChunk);
	}

	private StreamCodec<ByteBuf, ByteBuf, CompoundTag> tagCodec() {
		return format.namedNbt ? ByteBufStreamCodecs.NAMED_COMPOUND_TAG : ByteBufStreamCodecs.COMPOUND_TAG;
	}

	private StreamCodec<ByteBuf, ByteBuf, ChunkBlockEntity> entityCodec() {
		return format.namedNbt ? ChunkBlockEntity.NAMED_NBT_STREAM_CODEC : ChunkBlockEntity.STREAM_CODEC;
	}

	private static <T> List<T> readList(ByteBuf input, StreamCodec<ByteBuf, ByteBuf, T> codec, int minimumBytes) {
		int count = ByteBufStreamCodecs.count(input, input.readableBytes() / minimumBytes);
		List<T> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(codec.decode(input));
		}
		return Collections.unmodifiableList(values);
	}

	private static <T> void writeList(ByteBuf output, List<T> values, StreamCodec<ByteBuf, ByteBuf, T> codec) {
		ByteBufStreamCodecs.VARINT.encode(output, values.size());
		for (T value : values) {
			codec.encode(output, value);
		}
	}

	private static List<CompoundTag> copyTags(List<CompoundTag> tags) {
		List<CompoundTag> copy = new ArrayList<>(tags.size());
		for (CompoundTag tag : tags) copy.add(tag == null ? null : tag.copy());
		return Collections.unmodifiableList(copy);
	}

	private static Map<Integer, long[]> copyHeightmaps(Map<Integer, long[]> values) {
		Map<Integer, long[]> copy = new LinkedHashMap<>();
		values.forEach((type, data) -> {
			if (type < 0) {
				throw new IllegalArgumentException("Negative heightmap type");
			}
			copy.put(type, data.clone());
		});
		return Collections.unmodifiableMap(copy);
	}

	private StreamCodec<ByteBuf, ByteBuf, ChunkData> sectionCodec() {
		int count = 0;
		for (long word : sectionMask) {
			count += Long.bitCount(word);
		}
		return ChunkData.streamCodec(format.minimumProtocol, format.maskWidth == 0 ? -1 : count, fullChunk());
	}

	private static int scalarMask(long[] mask) {
		if (mask.length > 1 || mask.length == 1 && (mask[0] & ~0xffffL) != 0) {
			throw new IllegalArgumentException("Legacy chunk masks have 16 bits");
		}
		return mask.length == 0 ? 0 : (int) mask[0];
	}

	private static void require(boolean supported) {
		if (!supported) {
			throw new UnsupportedOperationException("Field is absent in this chunk packet format");
		}
	}

	private static void checkIndex(int index) {
		if (index != 0) {
			throw new IndexOutOfBoundsException("Chunk index: " + index);
		}
	}
}
