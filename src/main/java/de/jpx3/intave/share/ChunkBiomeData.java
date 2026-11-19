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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Biome palettes in ascending section Y; packed chunk positions put Z before X on the wire. */
public final class ChunkBiomeData {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBiomeData> STREAM_CODEC = streamCodec(770);
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBiomeData> LENGTH_PREFIXED_STORAGE_STREAM_CODEC = streamCodec(762);

	private final int x, z;
	private final List<ChunkPalette> sections;

	public ChunkBiomeData(int x, int z, List<ChunkPalette> sections) {
		if (sections.size() > ChunkData.MAX_SECTIONS) throw new IllegalArgumentException("Too many biome sections");
		List<ChunkPalette> copy = new ArrayList<>(sections);
		for (ChunkPalette palette : copy) if (palette.kind() != ChunkPalette.Kind.BIOMES) throw new IllegalArgumentException("Expected biome palette");
		this.x = x;
		this.z = z;
		this.sections = Collections.unmodifiableList(copy);
	}

	public int x() { return x; }
	public int z() { return z; }
	public List<ChunkPalette> sections() { return sections; }

	private static StreamCodec<ByteBuf, ByteBuf, ChunkBiomeData> streamCodec(int protocol) {
		StreamCodec<ByteBuf, ByteBuf, ChunkPalette> palette = ChunkPalette.streamCodec(ChunkPalette.Kind.BIOMES, protocol);
		StreamCodec<ByteBuf, ByteBuf, List<ChunkPalette>> sections = ByteBufStreamCodecs.lengthDelimited(StreamCodec.of((output, values) -> {
			for (ChunkPalette value : values) palette.encode(output, value);
		}, input -> {
			List<ChunkPalette> values = new ArrayList<>();
			while (input.isReadable()) {
				if (values.size() >= ChunkData.MAX_SECTIONS) throw new IllegalArgumentException("Too many biome sections");
				values.add(palette.decode(input));
			}
			return values;
		}), ChunkData.MAX_BYTES);
		return StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, ChunkBiomeData::z,
			ByteBufStreamCodecs.INTEGER, ChunkBiomeData::x,
			sections, ChunkBiomeData::sections,
			(z, x, values) -> new ChunkBiomeData(x, z, values)
		);
	}
}
