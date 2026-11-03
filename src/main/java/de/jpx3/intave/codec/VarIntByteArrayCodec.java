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

final class VarIntByteArrayCodec implements StreamCodec<ByteBuf, ByteBuf, byte[]> {
	private final int maxLength;

	VarIntByteArrayCodec(int maxLength) {
		if (maxLength < 0) throw new IllegalArgumentException("Negative byte array limit");
		this.maxLength = maxLength;
	}

	@Override
	public void encode(ByteBuf output, byte[] value) {
		if (value.length > maxLength) {
			throw new IllegalArgumentException("Byte array exceeds " + maxLength + " bytes");
		}
		VarIntCodec.INSTANCE.encode(output, value.length);
		output.writeBytes(value);
	}

	@Override
	public byte[] decode(ByteBuf input) {
		int length = VarIntCodec.INSTANCE.decode(input);
		if (length < 0 || length > maxLength || length > input.readableBytes()) {
			throw new IllegalArgumentException("Invalid byte array length: " + length);
		}
		byte[] value = new byte[length];
		input.readBytes(value);
		return value;
	}
}
