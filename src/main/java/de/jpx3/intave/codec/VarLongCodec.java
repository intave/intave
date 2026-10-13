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

public final class VarLongCodec implements StreamCodec<ByteBuf, ByteBuf, Long> {
	public static final VarLongCodec INSTANCE = new VarLongCodec();

	@Override
	public void encode(ByteBuf buf, Long value) {
		while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
			buf.writeByte((int) (value & 0x7F) | 0x80);
			value >>>= 7;
		}
		buf.writeByte((int) (value & 0x7F));
	}

	@Override
	public Long decode(ByteBuf buf) {
		long value = 0L;
		int position = 0;
		int currentByte;
		do {
			currentByte = buf.readByte();
			value |= (long) (currentByte & 0x7F) << position;
			if (position > 63) {
				throw new RuntimeException("VarLong is too big");
			}
			position += 7;
		} while ((currentByte & 0x80) == 0x80);
		return value;
	}
}
