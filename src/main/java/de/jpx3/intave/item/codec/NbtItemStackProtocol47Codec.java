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

import de.jpx3.intave.item.ItemStack;
import io.netty.buffer.ByteBuf;

public final class NbtItemStackProtocol47Codec extends NbtItemStackCodec {
	public NbtItemStackProtocol47Codec(int protocol) {
		super(protocol);
	}

	@Override
	protected int readItemId(ByteBuf input) {
		return input.readShort();
	}

	@Override
	protected void writeItemId(ByteBuf output, int id) {
		output.writeShort(id);
	}

	@Override
	protected Short readLegacyData(ByteBuf input) {
		return input.readShort();
	}

	@Override
	protected void writeLegacyData(ByteBuf output, ItemStack stack) {
		output.writeShort(stack.hasLegacyData() ? stack.legacyData() : 0);
	}
}
