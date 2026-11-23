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

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.EnchantmentRegistry;
import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.COMPOUND_TAG;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.NAMED_COMPOUND_TAG;

/**
 * Optional item stacks in inventory packets through 1.20.4. Null represents an empty stack.
 * Before 1.13 the short ID is followed by count and damage; 1.13 removes damage;
 * 1.13.2 replaces the short ID/sentinel with a presence flag and VarInt ID.
 * NBT is uncompressed; 1.20.2 removes the root name from the network format.
 */
abstract class NbtItemStackCodec implements StreamCodec<ByteBuf, ByteBuf, ItemStack> {
	private final int protocol;
	private final ItemRegistry items;
	private final StreamCodec<ByteBuf, ByteBuf, CompoundTag> tags;

	private final static MinecraftKey AIR = MinecraftKey.withDefaultNamespace("air");

	NbtItemStackCodec(int protocol) {
		if (protocol < 47 || protocol >= 766) {
			throw new IllegalArgumentException("Unsupported NBT item protocol " + protocol);
		}
		this.protocol = protocol;
		this.items = ItemRegistry.forProtocol(protocol);
		this.tags = protocol < 764 ? NAMED_COMPOUND_TAG : COMPOUND_TAG;
	}

	@Override
	public final ItemStack decode(ByteBuf input) {
		int start = input.readerIndex();
		try {
			int id = readItemId(input);
			if (id < 0) {
				return null;
			}
			int amount = input.readByte();
			Short data = readLegacyData(input);
			CompoundTag tag = tags.decode(input);
			MinecraftKey type = items.keyById(id);
			if (type == null){
				throw new IllegalArgumentException("Unknown item ID " + id + " in protocol " + protocol);
			}
			if (amount <= 0 || type.equals(AIR)) {
				return null;
			}
			ItemStack stack = new ItemStack(type, amount, protocol);
			stack.setLegacyData(data);
			stack.setTag(tag);
			return stack;
		} catch (RuntimeException failure) {
			input.readerIndex(start);
			throw failure;
		}
	}

	@Override
	public final void encode(ByteBuf output, ItemStack stack) {
		int start = output.writerIndex();
		try {
			if (stack == null) {
				writeItemId(output, -1);
				return;
			}
			if (!acceptsProtocol(stack.protocol())) {
				throw new IllegalArgumentException("NBT item conversion between protocols is not supported");
			}
			int id = items.idOf(stack.type());
			if (id < 0) throw new IllegalArgumentException("Unknown item " + stack.type() + " in protocol " + protocol);
			if (stack.amount() > Byte.MAX_VALUE) throw new IllegalArgumentException("Item count does not fit a signed byte");
			writeItemId(output, id);
			output.writeByte(stack.amount());
			writeLegacyData(output, stack);
			tags.encode(output, stack.nbt());
		} catch (RuntimeException failure) {
			output.writerIndex(start);
			throw failure;
		}
	}

	private boolean acceptsProtocol(int sourceProtocol) {
		if (sourceProtocol == protocol) {
			return true;
		}
		if ((sourceProtocol < 393) != (protocol < 393) || (sourceProtocol < 404) != (protocol < 404)) {
			return false;
		}
		return ItemRegistry.forProtocol(sourceProtocol) == items
			&& EnchantmentRegistry.forProtocol(sourceProtocol) == EnchantmentRegistry.forProtocol(protocol);
	}

	/** Returns a negative ID for an empty stack. */
	protected abstract int readItemId(ByteBuf input);

	protected abstract void writeItemId(ByteBuf output, int id);

	protected Short readLegacyData(ByteBuf input) {
		return null;
	}

	protected void writeLegacyData(ByteBuf output, ItemStack stack) {
		if (stack.hasLegacyData()) throw new IllegalArgumentException("Flattened stacks cannot carry legacy damage data");
	}
}
