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

import java.nio.charset.StandardCharsets;

final class StringCodec implements StreamCodec<ByteBuf, ByteBuf, String> {
	private final int maximum;

	StringCodec(int maximum) {
		if (maximum < 0 || maximum > Integer.MAX_VALUE / 3) {
			throw new IllegalArgumentException("Invalid string character limit: " + maximum);
		}
		this.maximum = maximum;
	}

	@Override
	public void encode(ByteBuf output, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (value.length() > maximum || bytes.length > maximum * 3) {
			throw new IllegalArgumentException("Wire string exceeds " + maximum + " characters");
		}
		VarIntCodec.INSTANCE.encode(output, bytes.length);
		output.writeBytes(bytes);
	}

	@Override
	public String decode(ByteBuf input) {
		int length = VarIntCodec.INSTANCE.decode(input);
		if (length < 0 || length > maximum * 3 || length > input.readableBytes()) {
			throw new IllegalArgumentException("Invalid wire string byte length: " + length);
		}
		byte[] bytes = new byte[length];
		input.readBytes(bytes);
		String value = new String(bytes, StandardCharsets.UTF_8);
		if (value.length() > maximum) {
			throw new IllegalArgumentException("Wire string exceeds " + maximum + " characters");
		}
		return value;
	}
}
