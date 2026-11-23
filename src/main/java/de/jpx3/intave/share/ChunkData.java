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
import java.util.Objects;

/**
 * Sections in ascending mask-bit order, or ascending Y from the dimension's minimum section for 1.18+.
 * The optional 256-entry biome array belongs to full chunks before 1.15.
 */
public final class ChunkData {
	public static final int MAX_BYTES = 2097152;
	public static final int MAX_SECTIONS = 1024;
	public static final ChunkData EMPTY = new ChunkData(Collections.emptyList());
	private final List<ChunkSection> sections;
	private final int[] biomeIds;

	public ChunkData(List<ChunkSection> sections) {
		this(sections, new int[0]);
	}

	public ChunkData(List<ChunkSection> sections, int[] biomeIds) {
		if (sections.size() > MAX_SECTIONS) {
			throw new IllegalArgumentException("Too many chunk sections");
		}
		List<ChunkSection> copy = new ArrayList<>(sections);
		copy.forEach(section -> Objects.requireNonNull(section, "section"));
		if (biomeIds.length != 0 && biomeIds.length != 256) {
			throw new IllegalArgumentException("Expected 256 column biomes");
		}
		for (int id : biomeIds) {
			if (id < 0) {
				throw new IllegalArgumentException("Negative biome ID");
			}
		}
		this.sections = Collections.unmodifiableList(copy);
		this.biomeIds = biomeIds.clone();
	}

	public List<ChunkSection> sections() {
		return sections;
	}

	public int[] biomeIds() {
		return biomeIds.clone();
	}

	public boolean skyLight() {
		return !sections.isEmpty() && sections.get(0).skyLight() != null;
	}

	/**
	 * sectionCount is -1 for modern packets without a section mask.
	 */
	public static StreamCodec<ByteBuf, ByteBuf, ChunkData> streamCodec(int protocol, int sectionCount, boolean fullChunk) {
		checkCount(sectionCount);
		if (protocol < 47 || protocol < 757 && sectionCount < 0) {
			throw new IllegalArgumentException("This chunk format requires a section count");
		}
		return ByteBufStreamCodecs.lengthDelimited(StreamCodec.of((output, data) -> {
			writeBody(output, data, protocol, sectionCount, fullChunk, data.skyLight());
		}, input -> {
			if (protocol >= 477 || sectionCount == 0) {
				return readBody(input, protocol, sectionCount, fullChunk, false);
			}
			// Old packets omit the dimension's sky-light flag. Only accept a layout that consumes the entire bounded payload.
			ChunkData withoutSky = null, withSky = null;
			RuntimeException firstFailure = null;
			try {
				withoutSky = readBody(input.duplicate(), protocol, sectionCount, fullChunk, false);
			} catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
				firstFailure = failure;
			}
			try {
				withSky = readBody(input.duplicate(), protocol, sectionCount, fullChunk, true);
			} catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
				if (firstFailure != null) {
					failure.addSuppressed(firstFailure);
				}
				if (withoutSky == null) {
					throw failure;
				}
			}
			if (withoutSky != null && withSky != null) {
				throw new IllegalArgumentException("Ambiguous legacy sky-light layout");
			}
			input.skipBytes(input.readableBytes());
			return withoutSky != null ? withoutSky : withSky;
		}), MAX_BYTES);
	}

	/**
	 * Bulk chunks supply the sky-light flag and have no per-chunk byte-length prefix.
	 */
	public static StreamCodec<ByteBuf, ByteBuf, ChunkData> legacyBulkStreamCodec(int sectionCount, boolean skyLight) {
		checkCount(sectionCount);
		if (sectionCount < 0 || sectionCount > 16) {
			throw new IllegalArgumentException("Invalid legacy section count");
		}
		int length = sectionCount * (8192 + 2048 + (skyLight ? 2048 : 0)) + 256;
		return StreamCodec.of((output, data) -> {
			if (data.biomeIds.length != 256) {
				throw new IllegalArgumentException("Bulk chunks require column biomes");
			}
			writeBody(output, data, 47, sectionCount, true, skyLight);
		}, input -> {
			if (length > input.readableBytes()) {
				throw new IllegalArgumentException("Truncated bulk chunk");
			}
			return readBody(input.readSlice(length), 47, sectionCount, true, skyLight);
		});
	}

	private static ChunkData readBody(ByteBuf input, int protocol, int count, boolean full, boolean sky) {
		if (protocol < 107 && count == 0 && full && !input.isReadable()) {
			return EMPTY; // 1.8 unload
		}
		List<ChunkSection> sections = new ArrayList<>();
		if (protocol < 107) {
			List<ChunkPalette> blocks = new ArrayList<>(count);
			List<byte[]> light = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				blocks.add(ChunkPalette.LEGACY_STREAM_CODEC.decode(input));
			}
			for (int i = 0; i < count; i++) {
				light.add(ByteBufStreamCodecs.bytes(input, 2048));
			}
			for (int i = 0; i < count; i++) {
				byte[] skyData = sky ? ByteBufStreamCodecs.bytes(input, 2048) : null;
				sections.add(new ChunkSection(-1, -1, blocks.get(i), null, light.get(i), skyData));
			}
		} else {
			StreamCodec<ByteBuf, ByteBuf, ChunkSection> codec = ChunkSection.streamCodec(protocol, sky);
			while (count < 0 ? input.isReadable() : sections.size() < count) {
				if (sections.size() >= MAX_SECTIONS) {
					throw new IllegalArgumentException("Too many chunk sections");
				}
				sections.add(codec.decode(input));
			}
		}
		int[] biomes = new int[protocol < 573 && full ? 256 : 0];
		for (int i = 0; i < biomes.length; i++) {
			biomes[i] = protocol < 393 ? input.readUnsignedByte() : input.readInt();
		}
		if (input.isReadable()) {
			throw new IllegalArgumentException("Trailing chunk section bytes");
		}
		return new ChunkData(sections, biomes);
	}

	private static void writeBody(ByteBuf output, ChunkData data, int protocol, int count, boolean full, boolean sky) {
		if (count >= 0 && count != data.sections.size()) {
			throw new IllegalArgumentException("Sections do not match the chunk mask");
		}
		if (protocol < 107 && count == 0 && full && data.biomeIds.length == 0) {
			return;
		}
		if (data.biomeIds.length != (protocol < 573 && full ? 256 : 0)) {
			throw new IllegalArgumentException("Column biomes do not match chunk format");
		}
		if (protocol < 107) {
			for (ChunkSection section : data.sections) {
				if (section.biomes() != null) {
					throw new IllegalArgumentException("Legacy sections cannot contain biome palettes");
				}
				ChunkPalette.LEGACY_STREAM_CODEC.encode(output, section.blockStates());
			}
			for (ChunkSection section : data.sections) {
				ChunkSection.writeLight(output, section.blockLight());
			}
			for (ChunkSection section : data.sections) {
				if (sky) {
					ChunkSection.writeLight(output, section.skyLight());
				} else if (section.skyLight() != null) {
					throw new IllegalArgumentException("Unexpected sky light");
				}
			}
		} else {
			StreamCodec<ByteBuf, ByteBuf, ChunkSection> codec = ChunkSection.streamCodec(protocol, sky);
			for (ChunkSection section : data.sections) {
				codec.encode(output, section);
			}
		}
		for (int biome : data.biomeIds) {
			if (protocol < 393) {
				if (biome > 255) {
					throw new IllegalArgumentException("Legacy biome ID exceeds one byte");
				}
				output.writeByte(biome);
			} else {
				output.writeInt(biome);
			}
		}
	}

	private static void checkCount(int count) {
		if (count < -1 || count > MAX_SECTIONS) {
			throw new IllegalArgumentException("Invalid section count");
		}
	}
}

