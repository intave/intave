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

import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.palette;
import static de.jpx3.intave.packet.wire.chunk.ChunkPacketFixtures.varInt;
import static de.jpx3.intave.share.ChunkPalette.Kind.BIOMES;
import static de.jpx3.intave.share.ChunkPalette.Kind.BLOCK_STATES;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkPaletteCodecTest {
	@Test
	void indirectPalettesResolveEveryEntryAndPreservePaddingAcrossFormats() {
		for (int protocol : new int[] {107, 340, 393, 477, 573, 735, 755, 757, 769, 770, 775, 777}) {
			for (ChunkPalette.Kind kind : ChunkPalette.Kind.values()) {
				if (kind == BIOMES && protocol < 757) continue;
				ByteBuf input = Unpooled.buffer(), encoded = Unpooled.buffer();
				try {
					palette(input, protocol, kind == BIOMES);
					if (protocol >= 735) {
						// The final padded word has unused high bits that must survive an unmodified round trip.
						input.setByte(input.writerIndex() - 8, input.getByte(input.writerIndex() - 8) | 0x80);
					}
					StreamCodec<ByteBuf, ByteBuf, ChunkPalette> codec = ChunkPalette.streamCodec(kind, protocol);
					ChunkPalette decoded = codec.decode(input.duplicate());
					for (int i = 0; i < kind.size(); i++) assertEquals(kind == BIOMES ? i % 7 + 200 : i % 17 * 16, decoded.value(i));
					codec.encode(encoded, decoded);
					assertEquals(input, encoded, "Protocol " + protocol + " " + kind);
					assertEquals(decoded.value(kind == BIOMES ? 45 : 561), decoded.value(1, 2, 3));
				} finally {
					input.release();
					encoded.release();
				}
			}
		}
	}

	@Test
	void singleValuePalettesOmitStorageAndOnlyOlderProtocolsCarryItsZeroLength() {
		for (int protocol : new int[] {757, 769, 770, 775}) {
			for (ChunkPalette.Kind kind : ChunkPalette.Kind.values()) {
				ByteBuf input = Unpooled.buffer(), encoded = Unpooled.buffer();
				try {
					input.writeByte(0);
					varInt(input, 300);
					if (protocol < 770) input.writeByte(0);
					StreamCodec<ByteBuf, ByteBuf, ChunkPalette> codec = ChunkPalette.streamCodec(kind, protocol);
					ChunkPalette value = codec.decode(input.duplicate());
					assertEquals(0, value.bitsPerEntry());
					assertEquals(300, value.value(kind.size() - 1));
					codec.encode(encoded, ChunkPalette.single(kind, 300));
					assertEquals(input, encoded);
				} finally {
					input.release();
					encoded.release();
				}
			}
		}
	}

	@Test
	void directPalettesHaveNoLocalIdsAndUseTheCorrectLegacyDummyLength() {
		for (int protocol : new int[] {107, 340, 393, 573, 735, 757, 769, 770, 775}) {
			for (ChunkPalette.Kind kind : ChunkPalette.Kind.values()) {
				if (kind == BIOMES && protocol < 757) continue;
				int bits = kind == BLOCK_STATES ? 13 : 6;
				int[] values = new int[kind.size()];
				for (int i = 0; i < values.length; i++) values[i] = i & ((1 << bits) - 1);
				ByteBuf expected = Unpooled.buffer(), encoded = Unpooled.buffer();
				try {
					expected.writeByte(bits);
					if (protocol < 393) expected.writeByte(0);
					int words = protocol < 735 ? (kind.size() * bits + 63) / 64 : (kind.size() + 64 / bits - 1) / (64 / bits);
					if (protocol < 770) varInt(expected, words);
					long[] storage = new long[words];
					for (int i = 0; i < values.length; i++) {
						int start = protocol < 735 ? i * bits : i / (64 / bits) * 64 + i % (64 / bits) * bits;
						for (int bit = 0; bit < bits; bit++) if ((values[i] & 1 << bit) != 0) storage[(start + bit) / 64] |= 1L << ((start + bit) % 64);
					}
					for (long word : storage) expected.writeLong(word);
					StreamCodec<ByteBuf, ByteBuf, ChunkPalette> codec = ChunkPalette.streamCodec(kind, protocol);
					ChunkPalette decoded = codec.decode(expected.duplicate());
					assertTrue(decoded.direct());
					assertArrayEquals(values, decoded.values());
					codec.encode(encoded, ChunkPalette.direct(kind, bits, values));
					assertEquals(expected, encoded, "Protocol " + protocol + " " + kind);
				} finally {
					expected.release();
					encoded.release();
				}
			}
		}
	}

	@Test
	void editedPalettesGrowFromSingleThroughIndirectToDirectWithoutChangingTheOriginal() {
		ChunkPalette original = ChunkPalette.single(BIOMES, 0);
		ChunkPalette edited = original;
		for (int i = 1; i <= 8; i++) edited = edited.withValue(i, 200 + i, 8);
		assertTrue(edited.direct());
		assertEquals(8, edited.bitsPerEntry());
		assertEquals(0, original.value(8));
		for (int i = 1; i <= 8; i++) assertEquals(200 + i, edited.value(i));
		for (int protocol : new int[] {757, 770}) {
			ByteBuf output = Unpooled.buffer();
			try {
				StreamCodec<ByteBuf, ByteBuf, ChunkPalette> codec = ChunkPalette.streamCodec(BIOMES, protocol);
				codec.encode(output, edited);
				assertArrayEquals(edited.values(), codec.decode(output).values());
				assertFalse(output.isReadable());
			} finally { output.release(); }
		}
	}

	@Test
	void valuesAndPaletteArraysAreDefensiveCopies() {
		int[] paletteIds = {5, 7};
		int[] indices = new int[4096];
		indices[15] = 1;
		ChunkPalette palette = ChunkPalette.indirect(BLOCK_STATES, 4, paletteIds, indices);
		paletteIds[1] = 99;
		indices[15] = 0;
		palette.paletteIds()[1] = 99;
		palette.values()[15] = 99;
		assertEquals(7, palette.value(15));
		assertThrows(IndexOutOfBoundsException.class, () -> palette.value(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> palette.value(16, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> palette.withValue(0, -1, 15));
		assertThrows(IllegalArgumentException.class, () -> ChunkPalette.direct(BIOMES, 4, new int[63]));
	}

	@Test
	void malformedBitsCountsIndicesRegistryIdsAndTruncatedStorageAreRejected() {
		StreamCodec<ByteBuf, ByteBuf, ChunkPalette> codec = ChunkPalette.streamCodec(BLOCK_STATES, 769);
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeByte(32);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(3);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(0);
			varInt(input, -1);
			input.writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(4);
			varInt(input, 17);
			input.writeZero(17);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(4).writeByte(1).writeByte(0);
			varInt(input, 255);
			input.writeZero(2048);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(4).writeByte(1).writeByte(0);
			varInt(input, 256);
			input.writeLong(1).writeZero(2040); // index 1 does not exist in a one-entry palette
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(4).writeByte(1).writeByte(0);
			varInt(input, 256);
			input.writeZero(2047);
			assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
			input.clear().writeByte(13).writeByte(1);
			assertThrows(IllegalArgumentException.class, () -> ChunkPalette.streamCodec(BLOCK_STATES, 340).decode(input));
			input.clear().writeByte(0).writeByte(1).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> ChunkPalette.streamCodec(BLOCK_STATES, 755).decode(input));
		} finally { input.release(); }
	}

	@Test
	void denseAndPaddedCodecsCanSerializeTheSameStructuredPalette() {
		ByteBuf dense = Unpooled.buffer(), padded = Unpooled.buffer();
		try {
			palette(dense, 573, false);
			ChunkPalette value = ChunkPalette.streamCodec(BLOCK_STATES, 573).decode(dense);
			ChunkPalette.streamCodec(BLOCK_STATES, 735).encode(padded, value);
			assertArrayEquals(value.values(), ChunkPalette.streamCodec(BLOCK_STATES, 735).decode(padded).values());
			assertFalse(padded.isReadable());
		} finally {
			dense.release();
			padded.release();
		}
	}

	@Test
	void legacyStatesUseLittleEndianShortsAndRejectUnrepresentableIds() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			input.writeByte(0x34).writeByte(0x12).writeZero(8190);
			ChunkPalette value = ChunkPalette.LEGACY_STREAM_CODEC.decode(input.duplicate());
			assertEquals(0x1234, value.value(0));
			ChunkPalette.LEGACY_STREAM_CODEC.encode(output, value);
			assertEquals(input, output);
			output.clear();
			assertThrows(IllegalArgumentException.class, () -> ChunkPalette.LEGACY_STREAM_CODEC.encode(output, ChunkPalette.single(BLOCK_STATES, 65536)));
		} finally {
			input.release();
			output.release();
		}
	}
}

