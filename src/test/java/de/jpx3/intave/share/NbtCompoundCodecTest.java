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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NbtCompoundCodecTest {
	@Test
	void allTagKindsRoundTripWithNamedAndUnnamedRoots() {
		for (boolean named : new boolean[] {false, true}) {
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				input.writeByte(10);
				if (named) input.writeShort(3).writeBytes(new byte[] {'n', 'b', 't'});
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
				StreamCodec<ByteBuf, ByteBuf, NbtCompound> codec = named ? NbtCompound.NAMED_STREAM_CODEC : NbtCompound.STREAM_CODEC;
				NbtCompound tag = codec.decode(input);
				assertEquals(end, input.readerIndex());
				assertEquals(0x12345678, input.readInt());
				codec.encode(output, tag);
				assertEquals(input.slice(0, end), output);
				byte[] contents = tag.contents();
				NbtCompound copy = new NbtCompound(contents);
				contents[0] = 99;
				assertArrayEquals(tag.contents(), copy.contents());
			} finally {
				input.release();
				output.release();
			}
		}
	}

	@Test
	void optionalCompoundsUseTagEndAndDoNotConsumeFollowingFields() {
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeByte(0).writeByte(99);
			assertNull(NbtCompound.STREAM_CODEC.decode(input));
			assertEquals(99, input.readUnsignedByte());
			input.clear();
			NbtCompound.NAMED_STREAM_CODEC.encode(input, null);
			assertEquals(1, input.readableBytes());
			assertEquals(0, input.readUnsignedByte());
		} finally {
			input.release();
		}
	}

	@Test
	void malformedTypesLengthsAndExcessiveNestingAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new NbtCompound(new byte[] {13, 0, 0}));
		assertThrows(IllegalArgumentException.class, () -> new NbtCompound(new byte[] {0, 1}));
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeByte(10).writeByte(7).writeShort(0).writeInt(Integer.MAX_VALUE);
			assertThrows(IllegalArgumentException.class, () -> NbtCompound.STREAM_CODEC.decode(input));
			input.clear().writeByte(10).writeByte(9).writeShort(0).writeByte(0).writeInt(1).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> NbtCompound.STREAM_CODEC.decode(input));
			input.clear().writeByte(10);
			for (int i = 0; i < 514; i++) input.writeByte(10).writeShort(0);
			input.writeZero(515);
			assertThrows(IllegalArgumentException.class, () -> NbtCompound.STREAM_CODEC.decode(input));
		} finally {
			input.release();
		}
	}
}
