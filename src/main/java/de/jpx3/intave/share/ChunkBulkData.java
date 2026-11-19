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

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

/**
 * One full legacy chunk in a bulk packet.
 */
public final class ChunkBulkData {
	private final int x, z, sectionMask;
	private final ChunkData data;

	public ChunkBulkData(int x, int z, int sectionMask, ChunkData data) {
		if ((sectionMask & ~0xffff) != 0) throw new IllegalArgumentException("Legacy chunk masks have 16 bits");
		Objects.requireNonNull(data, "data");
		if (Integer.bitCount(sectionMask) != data.sections().size() || data.biomeIds().length != 256) {
			throw new IllegalArgumentException("Bulk chunk sections or biomes do not match its header");
		}
		this.x = x;
		this.z = z;
		this.sectionMask = sectionMask;
		this.data = data;
	}

	public int x() {
		return x;
	}

	public int z() {
		return z;
	}

	public int sectionMask() {
		return sectionMask;
	}

	public ChunkData data() {
		return data;
	}

	public static StreamCodec<ByteBuf, ByteBuf, ChunkBulkData> streamCodec(int x, int z, int sectionMask, boolean skyLight) {
		return ChunkData.legacyBulkStreamCodec(Integer.bitCount(sectionMask), skyLight).beforeAndAfter(
			data -> new ChunkBulkData(x, z, sectionMask, data), ChunkBulkData::data
		);
	}

	public static int dataLength(int sectionMask, boolean skyLight) {
		return Integer.bitCount(sectionMask & 0xffff) * (8192 + 2048 + (skyLight ? 2048 : 0)) + 256;
	}
}
