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

package de.jpx3.intave.codec;

import io.netty.buffer.ByteBuf;

final class VarIntLongArrayCodec implements StreamCodec<ByteBuf, ByteBuf, long[]> {
	static final VarIntLongArrayCodec INSTANCE = new VarIntLongArrayCodec();

	private VarIntLongArrayCodec() {}

	@Override
	public long[] decode(ByteBuf input) {
		int count = ByteBufStreamCodecs.count(input, input.readableBytes() / Long.BYTES);
		if (count > input.readableBytes() / Long.BYTES) throw new IllegalArgumentException("Truncated long array");
		long[] values = new long[count];
		for (int i = 0; i < count; i++) values[i] = input.readLong();
		return values;
	}

	@Override
	public void encode(ByteBuf output, long[] values) {
		ByteBufStreamCodecs.VARINT.encode(output, values.length);
		for (long value : values) output.writeLong(value);
	}
}
