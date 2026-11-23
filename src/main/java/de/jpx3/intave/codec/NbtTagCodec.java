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

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.io.TagRegistry;
import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;

final class NbtTagCodec<T extends Tag> implements StreamCodec<ByteBuf, ByteBuf, T> {
	private static final int MAX_PACKET_BYTES = 8 * 1024 * 1024;
	private final Class<T> rootType;
	private final boolean named;
	private final int maxWireBytes;

	private NbtTagCodec(Class<T> rootType, boolean named, int maxWireBytes) {
		this.rootType = rootType;
		this.named = named;
		this.maxWireBytes = maxWireBytes;
	}

	static NbtTagCodec<CompoundTag> nullableCompound(boolean named) {
		return new NbtTagCodec<>(CompoundTag.class, named, TagLimiter.DEFAULT_MAX_BYTES);
	}

	static NbtTagCodec<Tag> trustedTag() {
		return new NbtTagCodec<>(Tag.class, false, MAX_PACKET_BYTES);
	}

	static NbtTagCodec<Tag> tag() {
		return new NbtTagCodec<>(Tag.class, false, TagLimiter.DEFAULT_MAX_BYTES);
	}

	@Override
	public void encode(ByteBuf output, T tag) {
		if (tag == null) {
			if (rootType != CompoundTag.class) {
				throw new IllegalArgumentException("Expected an NBT root tag");
			}
			output.writeByte(0);
			return;
		}
		ByteBuf encoded = output.alloc().buffer(256, maxWireBytes);
		try {
			NBTIO.writeTag(new ByteBufOutputStream(encoded), tag, named);
			output.writeBytes(encoded);
		} catch (IOException | IndexOutOfBoundsException exception) {
			throw new IllegalArgumentException("Unable to encode NBT tag", exception);
		} finally {
			encoded.release();
		}
	}

	@Override
	public T decode(ByteBuf input) {
		int length = Math.min(input.readableBytes(), maxWireBytes);
		// Unlike vanilla's direct read, this slice has an independent reader index.
		// Advance input explicitly below: one byte for null, or the full tag after successful decoding.
		ByteBuf bounded = input.slice(input.readerIndex(), length);
		try {
			int type = bounded.readUnsignedByte();
			if (type == 0 && rootType == CompoundTag.class) {
				input.skipBytes(1);
				return null;
			}
			if (type == 0 || (rootType == CompoundTag.class && type != CompoundTag.ID)) {
				throw new IllegalArgumentException("Unexpected NBT root type " + type);
			}
			if (named) {
				bounded.skipBytes(bounded.readUnsignedShort());
			}
			T tag = rootType.cast(TagRegistry.read(type, new ByteBufInputStream(bounded), readLimiter(length), 0));
			input.skipBytes(bounded.readerIndex());
			return tag;
		} catch (IOException | IndexOutOfBoundsException | NegativeArraySizeException exception) {
			throw new IllegalArgumentException("Invalid NBT tag", exception);
		}
	}

	private TagLimiter readLimiter(int wireBytes) {
		if (maxWireBytes == TagLimiter.DEFAULT_MAX_BYTES) {
			return TagLimiter.create(TagLimiter.DEFAULT_MAX_BYTES, TagLimiter.DEFAULT_MAX_NESTING_LEVEL);
		}
		// Trusted components can fill a packet. ViaNBT also charges four bytes per list entry
		// and counts UTF-16 string storage. Even a one-byte entry fits in this budget.
		return TagLimiter.create(5 * wireBytes + 1, TagLimiter.DEFAULT_MAX_NESTING_LEVEL, wireBytes + 1);
	}
}
