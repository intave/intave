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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import io.netty.buffer.ByteBuf;

public final class WireCodecs {
	private WireCodecs() {}

	public static int count(ByteBuf input, int maximum) {
		int count = ByteBufStreamCodecs.VARINT.decode(input);
		if (count < 0 || count > maximum) {
			throw new IllegalArgumentException("Invalid wire collection length: " + count);
		}
		return count;
	}

	public static byte[] bytes(ByteBuf input, int length) {
		if (length < 0 || length > input.readableBytes()) {
			throw new IllegalArgumentException("Invalid wire byte length: " + length);
		}
		byte[] bytes = new byte[length];
		input.readBytes(bytes);
		return bytes;
	}

	public static <E> E enumValue(E[] values, int index) {
		if (index < 0 || index >= values.length) {
			throw new IllegalArgumentException("Invalid wire enum index: " + index);
		}
		return values[index];
	}
}
