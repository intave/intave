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

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wire registry IDs, with the palette and packed storage retained for lossless serialization.
 */
public final class ChunkPalette {
	/**
	 * 1.8 stores the 4096 block state IDs as little-endian unsigned shorts, without a palette header.
	 */
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkPalette> LEGACY_STREAM_CODEC = StreamCodec.of((output, value) -> {
		if (value.kind != Kind.BLOCK_STATES) throw new IllegalArgumentException("Expected block states");
		for (int i = 0; i < value.size(); i++) {
			int id = value.value(i);
			if (id > 0xffff) throw new IllegalArgumentException("Legacy block state exceeds 16 bits");
			output.writeByte(id).writeByte(id >>> 8);
		}
	}, input -> {
		if (input.readableBytes() < 8192) throw new IllegalArgumentException("Truncated legacy block states");
		int[] ids = new int[4096];
		for (int i = 0; i < ids.length; i++) ids[i] = input.readUnsignedByte() | input.readUnsignedByte() << 8;
		return direct(Kind.BLOCK_STATES, 16, ids);
	});

	public enum Kind {
		BLOCK_STATES(4096, 4, 8), BIOMES(64, 1, 3);
		private final int size, minimumBits, maximumLocalBits;

		Kind(int size, int minimumBits, int maximumLocalBits) {
			this.size = size;
			this.minimumBits = minimumBits;
			this.maximumLocalBits = maximumLocalBits;
		}

		public int size() {
			return size;
		}
	}

	private final Kind kind;
	private final int bits;
	private final int[] palette;
	private final long[] storage;
	private final boolean padded;

	private ChunkPalette(Kind kind, int bits, int[] palette, long[] storage, boolean padded) {
		this.kind = Objects.requireNonNull(kind, "kind");
		if (bits < 0 || bits > 31 || bits != 0 && bits < kind.minimumBits) {
			throw new IllegalArgumentException("Invalid palette bits: " + bits);
		}
		if (bits == 0 ? palette.length != 1
			: bits <= kind.maximumLocalBits ? palette.length == 0 || palette.length > 1 << bits
			: palette.length != 0) throw new IllegalArgumentException("Palette size does not match its bits");
		if (storage.length != storageLength(kind.size, bits, padded))
			throw new IllegalArgumentException("Invalid packed storage length");
		for (int id : palette) if (id < 0) throw new IllegalArgumentException("Negative palette registry ID");
		this.bits = bits;
		this.palette = palette.clone();
		this.storage = storage.clone();
		this.padded = padded;
		if (bits != 0 && palette.length != 0) {
			for (int i = 0; i < kind.size; i++) {
				if (index(i) >= palette.length) throw new IllegalArgumentException("Packed index is outside the palette");
			}
		}
	}

	public static ChunkPalette single(Kind kind, int registryId) {
		return new ChunkPalette(kind, 0, new int[]{registryId}, new long[0], true);
	}

	/**
	 * Indices use X first, then Z, then Y: y * side * side + z * side + x.
	 */
	public static ChunkPalette indirect(Kind kind, int bits, int[] palette, int[] indices) {
		if (bits < kind.minimumBits || bits > kind.maximumLocalBits)
			throw new IllegalArgumentException("Invalid indirect palette bits");
		return new ChunkPalette(kind, bits, palette, pack(kind.size, bits, indices, true), true);
	}

	/**
	 * The bit width must match the protocol's block-state or negotiated biome registry.
	 */
	public static ChunkPalette direct(Kind kind, int bits, int[] registryIds) {
		if (bits <= kind.maximumLocalBits || bits > 31) throw new IllegalArgumentException("Invalid direct palette bits");
		return new ChunkPalette(kind, bits, new int[0], pack(kind.size, bits, registryIds, true), true);
	}

	public Kind kind() {
		return kind;
	}

	public int size() {
		return kind.size;
	}

	public int bitsPerEntry() {
		return bits;
	}

	public int[] paletteIds() {
		return palette.clone();
	}

	public boolean direct() {
		return bits > kind.maximumLocalBits;
	}

	public int value(int index) {
		checkIndex(index, kind.size);
		if (bits == 0) return palette[0];
		int value = index(index);
		return palette.length == 0 ? value : palette[value];
	}

	public int value(int x, int y, int z) {
		int side = kind == Kind.BLOCK_STATES ? 16 : 4;
		checkIndex(x, side);
		checkIndex(y, side);
		checkIndex(z, side);
		return value((y * side + z) * side + x);
	}

	public int[] values() {
		int[] values = new int[kind.size];
		for (int i = 0; i < values.length; i++) values[i] = value(i);
		return values;
	}

	/**
	 * Returns a replacement, using directBits if growing the local palette requires the global registry.
	 */
	public ChunkPalette withValue(int index, int registryId, int directBits) {
		checkIndex(index, kind.size);
		if (registryId < 0) throw new IllegalArgumentException("Negative registry ID");
		if (value(index) == registryId) return this;
		if (direct()) {
			int[] values = values();
			values[index] = registryId;
			return direct(kind, bits, values);
		}
		int entry = -1;
		for (int i = 0; i < palette.length; i++)
			if (palette[i] == registryId) {
				entry = i;
				break;
			}
		int[] newPalette = palette;
		if (entry < 0) {
			entry = palette.length;
			newPalette = Arrays.copyOf(palette, entry + 1);
			newPalette[entry] = registryId;
		}
		int newBits = Math.max(kind.minimumBits, 32 - Integer.numberOfLeadingZeros(newPalette.length - 1));
		if (newBits > kind.maximumLocalBits) {
			int[] values = values();
			values[index] = registryId;
			return direct(kind, directBits, values);
		}
		int[] indices = indices();
		indices[index] = entry;
		return indirect(kind, Math.max(bits, newBits), newPalette, indices);
	}

	public static StreamCodec<ByteBuf, ByteBuf, ChunkPalette> streamCodec(Kind kind, int protocol) {
		if (protocol < 107 || kind == Kind.BIOMES && protocol < 757)
			throw new IllegalArgumentException("Palettes are absent in this protocol");
		boolean padded = protocol >= 735;
		boolean lengthPrefixed = protocol < 770;
		boolean globalLength = protocol < 393;
		return StreamCodec.of((output, value) -> {
			if (value.kind != kind) throw new IllegalArgumentException("Wrong palette kind");
			ChunkPalette encoded = value;
			if (value.bits == 0 && protocol < 757) {
				encoded = indirect(kind, kind.minimumBits, value.palette, new int[kind.size]);
			}
			output.writeByte(encoded.bits);
			if (encoded.bits == 0) ByteBufStreamCodecs.VARINT.encode(output, encoded.palette[0]);
			else if (encoded.palette.length != 0) {
				ByteBufStreamCodecs.VARINT.encode(output, encoded.palette.length);
				for (int id : encoded.palette) ByteBufStreamCodecs.VARINT.encode(output, id);
			} else if (globalLength) ByteBufStreamCodecs.VARINT.encode(output, 0);
			long[] data = encoded.padded == padded ? encoded.storage : pack(kind.size, encoded.bits, encoded.indices(), padded);
			if (lengthPrefixed) ByteBufStreamCodecs.VARINT.encode(output, data.length);
			for (long word : data) output.writeLong(word);
		}, input -> {
			int bits = input.readUnsignedByte();
			if (bits > 31 || bits == 0 && protocol < 757 || bits != 0 && bits < kind.minimumBits) {
				throw new IllegalArgumentException("Invalid palette bits: " + bits);
			}
			int[] palette;
			if (bits == 0) palette = new int[]{ByteBufStreamCodecs.VARINT.decode(input)};
			else if (bits <= kind.maximumLocalBits) {
				int count = ByteBufStreamCodecs.count(input, Math.min(1 << bits, input.readableBytes()));
				palette = new int[count];
				for (int i = 0; i < count; i++) palette[i] = ByteBufStreamCodecs.VARINT.decode(input);
			} else {
				palette = new int[0];
				if (globalLength && ByteBufStreamCodecs.VARINT.decode(input) != 0)
					throw new IllegalArgumentException("Nonempty global palette");
			}
			int length = storageLength(kind.size, bits, padded);
			if (lengthPrefixed && ByteBufStreamCodecs.VARINT.decode(input) != length)
				throw new IllegalArgumentException("Invalid packed storage length");
			if (length > input.readableBytes() / Long.BYTES) throw new IllegalArgumentException("Truncated packed storage");
			long[] storage = new long[length];
			for (int i = 0; i < length; i++) storage[i] = input.readLong();
			return new ChunkPalette(kind, bits, palette, storage, padded);
		});
	}

	private int[] indices() {
		int[] indices = new int[kind.size];
		if (bits != 0) for (int i = 0; i < indices.length; i++) indices[i] = index(i);
		return indices;
	}

	private int index(int index) {
		int bitIndex = padded ? index / (64 / bits) * 64 + index % (64 / bits) * bits : index * bits;
		int word = bitIndex >>> 6, offset = bitIndex & 63;
		long value = storage[word] >>> offset;
		if (offset + bits > 64) value |= storage[word + 1] << (64 - offset);
		return (int) (value & (1L << bits) - 1);
	}

	private static int storageLength(int size, int bits, boolean padded) {
		if (bits == 0) return 0;
		return padded ? (size + 64 / bits - 1) / (64 / bits) : (size * bits + 63) / 64;
	}

	private static void checkIndex(int index, int size) {
		if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Palette index: " + index);
	}

	private static long[] pack(int size, int bits, int[] values, boolean padded) {
		if (values.length != size || bits < 1 || bits > 31) throw new IllegalArgumentException("Invalid packed values");
		long[] storage = new long[storageLength(size, bits, padded)];
		long mask = (1L << bits) - 1;
		for (int i = 0; i < size; i++) {
			long value = values[i];
			if (value < 0 || value > mask) throw new IllegalArgumentException("Value does not fit packed bits");
			int bitIndex = padded ? i / (64 / bits) * 64 + i % (64 / bits) * bits : i * bits;
			int word = bitIndex >>> 6, offset = bitIndex & 63;
			storage[word] |= value << offset;
			if (offset + bits > 64) storage[word + 1] |= value >>> (64 - offset);
		}
		return storage;
	}
}

