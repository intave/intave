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

package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Immutable encoded compound contents, including their terminating TAG_End.
 * Preserves nested tags and legacy root names without depending on a server NBT implementation.
 * Both codecs use TAG_End for a null compound.
 */
public final class NbtCompound {
	private static final int MAX_BYTES = 2097152;
	private static final int MAX_DEPTH = 512;
	public static final StreamCodec<ByteBuf, ByteBuf, NbtCompound> STREAM_CODEC = codec(false);
	public static final StreamCodec<ByteBuf, ByteBuf, NbtCompound> NAMED_STREAM_CODEC = codec(true);
	public static final NbtCompound EMPTY = new NbtCompound(new byte[] {0});

	private final byte[] rootName;
	private final byte[] contents;

	public NbtCompound(byte[] contents) {
		this(new byte[0], contents.clone());
		if (contents.length >= MAX_BYTES) throw new IllegalArgumentException("NBT compound is too large");
		ByteBuf input = Unpooled.wrappedBuffer(this.contents);
		try {
			skipTag(input, 10, 0);
			if (input.isReadable()) throw new IllegalArgumentException("Trailing NBT bytes");
		} finally {
			input.release();
		}
	}

	private NbtCompound(byte[] rootName, byte[] contents) {
		this.rootName = rootName;
		this.contents = contents;
	}

	public byte[] contents() {
		return contents.clone();
	}

	private static StreamCodec<ByteBuf, ByteBuf, NbtCompound> codec(boolean named) {
		return StreamCodec.of((output, tag) -> {
			if (tag == null) {
				output.writeByte(0);
				return;
			}
			if ((long) tag.contents.length + (named ? 3 + tag.rootName.length : 1) > MAX_BYTES) {
				throw new IllegalArgumentException("NBT compound is too large");
			}
			output.writeByte(10);
			if (named) output.writeShort(tag.rootName.length).writeBytes(tag.rootName);
			output.writeBytes(tag.contents);
		}, input -> {
			ByteBuf bounded = input.slice(input.readerIndex(), Math.min(input.readableBytes(), MAX_BYTES));
			int type = bounded.readUnsignedByte();
			if (type == 0) {
				input.skipBytes(1);
				return null;
			}
			if (type != 10) throw new IllegalArgumentException("Expected an NBT compound, got type " + type);
			byte[] name = new byte[named ? bounded.readUnsignedShort() : 0];
			bounded.readBytes(name);
			int start = bounded.readerIndex();
			skipTag(bounded, 10, 0);
			byte[] contents = new byte[bounded.readerIndex() - start];
			bounded.getBytes(start, contents);
			input.skipBytes(bounded.readerIndex());
			return new NbtCompound(name, contents);
		});
	}

	private static void skipTag(ByteBuf input, int type, int depth) {
		if (depth > MAX_DEPTH) throw new IllegalArgumentException("NBT nesting is too deep");
		switch (type) {
			case 1: input.skipBytes(1); break;
			case 2: input.skipBytes(2); break;
			case 3: case 5: input.skipBytes(4); break;
			case 4: case 6: input.skipBytes(8); break;
			case 7: skipArray(input, 1); break;
			case 8: input.skipBytes(input.readUnsignedShort()); break;
			case 9:
				int elementType = input.readUnsignedByte();
				int count = input.readInt();
				if (elementType > 12 || count < 0 || count > input.readableBytes() || elementType == 0 && count != 0) {
					throw new IllegalArgumentException("Invalid NBT list");
				}
				for (int i = 0; i < count; i++) skipTag(input, elementType, depth + 1);
				break;
			case 10:
				int childType;
				while ((childType = input.readUnsignedByte()) != 0) {
					input.skipBytes(input.readUnsignedShort());
					skipTag(input, childType, depth + 1);
				}
				break;
			case 11: skipArray(input, 4); break;
			case 12: skipArray(input, 8); break;
			default: throw new IllegalArgumentException("Invalid NBT type: " + type);
		}
	}

	private static void skipArray(ByteBuf input, int width) {
		int count = input.readInt();
		if (count < 0 || count > input.readableBytes() / width) throw new IllegalArgumentException("Invalid NBT array length");
		input.skipBytes(count * width);
	}
}
