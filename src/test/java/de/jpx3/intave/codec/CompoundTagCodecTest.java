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

import com.viaversion.nbt.tag.ByteArrayTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.LongArrayTag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CompoundTagCodecTest {
	@Test
	void viaNbtCompoundsCanBeReadEditedAndWrittenDirectly() {
		for (boolean named : new boolean[] {false, true}) {
			StreamCodec<ByteBuf, ByteBuf, CompoundTag> codec = codec(named);
			ByteBuf buffer = Unpooled.buffer();
			try {
				CompoundTag original = new CompoundTag();
				original.putString("id", "minecraft:chest");
				CompoundTag nested = new CompoundTag();
				nested.putInt("count", 3);
				original.put("nested", nested);
				original.put("heights", new LongArrayTag(new long[] {1, 2}));
				codec.encode(buffer, original);
				CompoundTag edited = codec.decode(buffer);
				assertEquals(original, edited);
				edited.getCompoundTag("nested").putInt("count", 4);
				edited.getLongArrayTag("heights").getValue()[0] = 99;
				assertEquals(3, nested.getInt("count"));
				assertArrayEquals(new long[] {1, 2}, original.getLongArrayTag("heights").getValue());
				buffer.clear();
				codec.encode(buffer, edited);
				assertEquals(edited, codec.decode(buffer));
				assertFalse(buffer.isReadable());
			} finally {
				buffer.release();
			}
		}
	}

	@Test
	void legacyRootNamesDuplicateKeysAndEmptyListsUseViaNbtNormalization() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer(), expected = Unpooled.buffer();
		try {
			input.writeByte(10).writeShort(1).writeByte('r');
			input.writeByte(3).writeShort(1).writeByte('x').writeInt(1);
			input.writeByte(3).writeShort(1).writeByte('x').writeInt(2);
			input.writeByte(9).writeShort(1).writeByte('l').writeByte(3).writeInt(0);
			input.writeByte(0).writeInt(123);
			CompoundTag tag = ByteBufStreamCodecs.NAMED_COMPOUND_TAG.decode(input);
			assertEquals(2, tag.getInt("x"));
			assertTrue(tag.getListTag("l").isEmpty());
			assertEquals(123, input.readInt());
			ByteBufStreamCodecs.NAMED_COMPOUND_TAG.encode(output, tag);
			expected.writeByte(10).writeShort(0);
			expected.writeByte(3).writeShort(1).writeByte('x').writeInt(2);
			expected.writeByte(9).writeShort(1).writeByte('l').writeByte(0).writeInt(0);
			expected.writeByte(0);
			assertEquals(expected, output);
		} finally {
			input.release();
			output.release();
			expected.release();
		}
	}

	@Test
	void invalidArraysAndTruncatedTagsDoNotAdvanceThePacketReader() {
		ByteBuf input = Unpooled.buffer();
		try {
			for (int type : new int[] {7, 9, 11, 12}) {
				for (int length : new int[] {-1, Integer.MAX_VALUE}) {
					input.clear().writeByte(99).writeByte(10).writeByte(type).writeShort(0);
					if (type == 9) input.writeByte(1);
					input.writeInt(length).readerIndex(1);
					assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
					assertEquals(1, input.readerIndex());
				}
			}
			input.clear().writeByte(10).writeByte(3).writeShort(0).writeShort(1);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
			assertEquals(0, input.readerIndex());
			input.clear().writeByte(10).writeShort(10).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.NAMED_COMPOUND_TAG.decode(input));
			assertEquals(0, input.readerIndex());
			assertEquals(1, input.refCnt());
		} finally {
			input.release();
		}
	}

	@Test
	void allTagKindsRoundTripWithNamedAndUnnamedRoots() {
		for (boolean named : new boolean[] {false, true}) {
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				input.writeByte(10);
				if (named) input.writeShort(0);
				for (int type = 1; type <= 12; type++) {
					input.writeByte(type).writeShort(1).writeByte('A' + type);
					switch (type) {
						case 1: input.writeByte(-12); break;
						case 2: input.writeShort(-1234); break;
						case 3: input.writeInt(-1234567); break;
						case 4: input.writeLong(Long.MIN_VALUE); break;
						case 5: input.writeFloat(1.25F); break;
						case 6: input.writeDouble(-2.5); break;
						case 7: input.writeInt(2).writeByte(1).writeByte(2); break;
						case 8: input.writeShort(2).writeByte('h').writeByte('i'); break;
						case 9: input.writeByte(10).writeInt(2).writeByte(0).writeByte(0); break;
						case 10: input.writeByte(3).writeShort(0).writeInt(77).writeByte(0); break;
						case 11: input.writeInt(2).writeInt(-1).writeInt(2); break;
						case 12: input.writeInt(2).writeLong(-1).writeLong(2); break;
					}
				}
				input.writeByte(0);
				int end = input.writerIndex();
				input.writeInt(0x12345678);
				StreamCodec<ByteBuf, ByteBuf, CompoundTag> codec = codec(named);
				CompoundTag tag = codec.decode(input);
				assertEquals(12, tag.size());
				assertEquals(end, input.readerIndex());
				assertEquals(0x12345678, input.readInt());
				codec.encode(output, tag);
				assertEquals(input.slice(0, end), output);
			} finally {
				input.release();
				output.release();
			}
		}
	}

	@Test
	void nullAndEmptyCompoundsAreDistinctAndDoNotConsumeFollowingFields() {
		for (boolean named : new boolean[] {false, true}) {
			StreamCodec<ByteBuf, ByteBuf, CompoundTag> codec = codec(named);
			ByteBuf input = Unpooled.buffer();
			try {
				codec.encode(input, null);
				assertEquals(1, input.readableBytes());
				assertEquals(0, input.getByte(0));
				input.writeByte(99);
				assertNull(codec.decode(input));
				assertEquals(99, input.readUnsignedByte());
				input.clear();
				codec.encode(input, new CompoundTag());
				input.writeByte(99);
				assertTrue(codec.decode(input).isEmpty());
				assertEquals(99, input.readUnsignedByte());
			} finally {
				input.release();
			}
		}
	}

	@Test
	void malformedTypesAndExcessiveNestingAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			for (int type : new int[] {1, 9, 13, 255}) {
				input.clear().writeByte(type);
				assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
				assertEquals(0, input.readerIndex());
			}
			input.clear().writeByte(10).writeByte(13).writeShort(0);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
			input.clear().writeByte(10).writeByte(9).writeShort(0).writeByte(0).writeInt(1).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
			input.clear().writeByte(10);
			for (int i = 0; i < 514; i++) input.writeByte(10).writeShort(0);
			input.writeZero(515);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.decode(input));
			assertEquals(0, input.readerIndex());
		} finally {
			input.release();
		}
	}

	@Test
	void oversizedCompoundsAreRejectedWithoutPartialOutput() {
		CompoundTag tag = new CompoundTag();
		tag.put("data", new ByteArrayTag(new byte[2097152]));
		ByteBuf output = Unpooled.buffer();
		try {
			output.writeByte(99);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.COMPOUND_TAG.encode(output, tag));
			assertEquals(1, output.writerIndex());
			assertEquals(99, output.readByte());
		} finally {
			output.release();
		}
	}

	private static StreamCodec<ByteBuf, ByteBuf, CompoundTag> codec(boolean named) {
		return named ? ByteBufStreamCodecs.NAMED_COMPOUND_TAG : ByteBufStreamCodecs.COMPOUND_TAG;
	}
}
