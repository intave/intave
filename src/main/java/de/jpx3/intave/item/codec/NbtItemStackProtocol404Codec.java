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

package de.jpx3.intave.item.codec;

import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;

public final class NbtItemStackProtocol404Codec extends NbtItemStackCodec {
	public NbtItemStackProtocol404Codec(int protocol) {
		super(protocol);
	}

	@Override
	protected int readItemId(ByteBuf input) {
		if (!input.readBoolean()) {
			return -1;
		}
		int id = VARINT.decode(input);
		if (id < 0) {
			throw new IllegalArgumentException("Negative item ID in a present stack");
		}
		return id;
	}

	@Override
	protected void writeItemId(ByteBuf output, int id) {
		output.writeBoolean(id >= 0);
		if (id >= 0) {
			VARINT.encode(output, id);
		}
	}
}
