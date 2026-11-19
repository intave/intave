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

/** Light masks and their section arrays, in ascending mask-bit order. */
public final class ChunkLightData {
	private static final StreamCodec<ByteBuf, ByteBuf, byte[]> LIGHT_ARRAY = ByteBufStreamCodecs.varIntByteArray(2048);
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkLightData> STREAM_CODEC = codec(false);
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkLightData> LEGACY_STREAM_CODEC = codec(true);

	private final long[] skyMask, blockMask, emptySkyMask, emptyBlockMask;
	private final List<byte[]> skyUpdates, blockUpdates;

	public ChunkLightData() {
		this(new long[0], new long[0], new long[0], new long[0], Collections.emptyList(), Collections.emptyList());
	}

	public ChunkLightData(long[] skyMask, long[] blockMask, long[] emptySkyMask, long[] emptyBlockMask,
		List<byte[]> skyUpdates, List<byte[]> blockUpdates) {
		this.skyMask = skyMask.clone();
		this.blockMask = blockMask.clone();
		this.emptySkyMask = emptySkyMask.clone();
		this.emptyBlockMask = emptyBlockMask.clone();
		this.skyUpdates = copy(skyUpdates);
		this.blockUpdates = copy(blockUpdates);
		if (cardinality(skyMask) != skyUpdates.size() || cardinality(blockMask) != blockUpdates.size()) {
			throw new IllegalArgumentException("Light masks and update counts differ");
		}
	}

	public long[] skyMask() { return skyMask.clone(); }
	public long[] blockMask() { return blockMask.clone(); }
	public long[] emptySkyMask() { return emptySkyMask.clone(); }
	public long[] emptyBlockMask() { return emptyBlockMask.clone(); }
	public List<byte[]> skyUpdates() { return copy(skyUpdates); }
	public List<byte[]> blockUpdates() { return copy(blockUpdates); }

	private static StreamCodec<ByteBuf, ByteBuf, ChunkLightData> codec(boolean legacy) {
		return StreamCodec.of((output, data) -> {
			writeMask(output, data.skyMask, legacy);
			writeMask(output, data.blockMask, legacy);
			writeMask(output, data.emptySkyMask, legacy);
			writeMask(output, data.emptyBlockMask, legacy);
			writeUpdates(output, data.skyUpdates, legacy);
			writeUpdates(output, data.blockUpdates, legacy);
		}, input -> {
			long[] sky = readMask(input, legacy), block = readMask(input, legacy);
			long[] emptySky = readMask(input, legacy), emptyBlock = readMask(input, legacy);
			List<byte[]> skyUpdates = readUpdates(input, legacy ? cardinality(sky) : -1);
			List<byte[]> blockUpdates = readUpdates(input, legacy ? cardinality(block) : -1);
			return new ChunkLightData(sky, block, emptySky, emptyBlock, skyUpdates, blockUpdates);
		});
	}

	private static long[] readMask(ByteBuf input, boolean legacy) {
		if (!legacy) return ByteBufStreamCodecs.VARINT_LONG_ARRAY.decode(input);
		int mask = ByteBufStreamCodecs.VARINT.decode(input);
		if ((mask & ~0x3ffff) != 0) throw new IllegalArgumentException("Legacy light masks have 18 bits");
		return mask == 0 ? new long[0] : new long[] {mask};
	}

	private static void writeMask(ByteBuf output, long[] mask, boolean legacy) {
		if (!legacy) {
			ByteBufStreamCodecs.VARINT_LONG_ARRAY.encode(output, mask);
			return;
		}
		if (mask.length > 1 || mask.length == 1 && (mask[0] & ~0x3ffffL) != 0) {
			throw new IllegalArgumentException("Legacy light masks have 18 bits");
		}
		ByteBufStreamCodecs.VARINT.encode(output, mask.length == 0 ? 0 : (int) mask[0]);
	}

	private static List<byte[]> readUpdates(ByteBuf input, int count) {
		if (count < 0) count = ByteBufStreamCodecs.count(input, input.readableBytes());
		if (count > input.readableBytes()) throw new IllegalArgumentException("Truncated light arrays");
		List<byte[]> result = new ArrayList<>(count);
		for (int i = 0; i < count; i++) result.add(LIGHT_ARRAY.decode(input));
		return result;
	}

	private static void writeUpdates(ByteBuf output, List<byte[]> updates, boolean legacy) {
		if (!legacy) ByteBufStreamCodecs.VARINT.encode(output, updates.size());
		for (byte[] update : updates) LIGHT_ARRAY.encode(output, update);
	}

	private static int cardinality(long[] mask) {
		int count = 0;
		for (long bits : mask) count = Math.addExact(count, Long.bitCount(bits));
		return count;
	}

	private static List<byte[]> copy(List<byte[]> updates) {
		List<byte[]> copy = new ArrayList<>(updates.size());
		for (byte[] data : updates) {
			if (data.length > 2048) throw new IllegalArgumentException("Light array exceeds 2048 bytes");
			copy.add(data.clone());
		}
		return Collections.unmodifiableList(copy);
	}
}
