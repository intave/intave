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

final class ByteArrayCodec implements StreamCodec<ByteBuf, ByteBuf, byte[]> {
	static final ByteArrayCodec INSTANCE = new ByteArrayCodec();

	private ByteArrayCodec() {}

	@Override
	public void encode(ByteBuf buf, byte[] value) {
		ByteBufStreamCodecs.INTEGER.encode(buf, value.length);
		buf.writeBytes(value);
	}

	@Override
	public byte[] decode(ByteBuf buf) {
		int length = ByteBufStreamCodecs.INTEGER.decode(buf);
		byte[] value = new byte[length];
		buf.readBytes(value);
		return value;
	}
}
