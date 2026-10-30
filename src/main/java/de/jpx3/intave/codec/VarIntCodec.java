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

final class VarIntCodec implements StreamCodec<ByteBuf, ByteBuf, Integer> {
	static final VarIntCodec INSTANCE = new VarIntCodec();

	private VarIntCodec() {}

	@Override
	public void encode(ByteBuf buf, Integer value) {
		while ((value & 0xFFFFFF80) != 0L) {
			buf.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		buf.writeByte(value & 0x7F);
	}

	@Override
	public Integer decode(ByteBuf buf) {
		int numRead = 0;
		int result = 0;
		byte read;
		do {
			read = buf.readByte();
			int value = (read & 0x7F);
			result |= (value << (7 * numRead));

			numRead++;
			if (numRead > 5) {
				throw new RuntimeException("VarInt is too big");
			}
		} while ((read & 0x80) != 0);

		return result;
	}
}
