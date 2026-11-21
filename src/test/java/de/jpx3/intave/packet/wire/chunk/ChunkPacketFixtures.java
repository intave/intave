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

package de.jpx3.intave.packet.wire.chunk;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class ChunkPacketFixtures {
	private ChunkPacketFixtures() {}

	public static void nbt(ByteBuf output, boolean named) {
		output.writeByte(10);
		if (named) output.writeShort(0);
		output.writeByte(12).writeShort(1).writeByte('H').writeInt(2);
		output.writeLong(0xfedcba9876543210L).writeLong(1234).writeByte(0);
	}

	public static void light(ByteBuf output, boolean legacy) {
		if (legacy) {
			varInt(output, 0x20001);
			varInt(output, 2);
			varInt(output, 4);
			varInt(output, 8);
		} else {
			output.writeByte(2).writeLong(1).writeLong(2);
			output.writeByte(1).writeLong(2);
			output.writeByte(1).writeLong(4);
			output.writeByte(1).writeLong(8);
			output.writeByte(2);
		}
		lightArray(output, 0x12);
		lightArray(output, 0x34);
		if (!legacy) output.writeByte(1);
		lightArray(output, 0x56);
	}

	private static void lightArray(ByteBuf output, int value) {
		varInt(output, 2048);
		for (int i = 0; i < 2048; i++) output.writeByte(value);
	}

	public static void chunk(ByteBuf output, int protocol, boolean full) {
		chunk(output, protocol, full, true);
	}

	public static void chunk(ByteBuf output, int protocol, boolean full, boolean sky) {
		output.writeInt(-12).writeInt(27);
		if (protocol < 755) output.writeBoolean(full);
		if (protocol >= 735 && protocol < 751) output.writeBoolean(true);
		if (protocol < 107) output.writeShort(0x8001);
		else if (protocol < 755) varInt(output, 0x8001);
		else if (protocol < 757) output.writeByte(2).writeLong(1).writeLong(2);
		if (protocol >= 477 && protocol < 770) nbt(output, protocol < 764);
		if (protocol >= 770) {
			output.writeByte(2).writeByte(1).writeByte(2).writeLong(-1).writeLong(1234);
			output.writeByte(4).writeByte(1).writeLong(Long.MIN_VALUE);
		}
		if (protocol >= 573 && protocol < 751 && full) {
			for (int i = 0; i < 1024; i++) output.writeInt(i);
		} else if (protocol >= 751 && protocol < 757 && (full || protocol >= 755)) {
			output.writeByte(3).writeByte(0).writeByte(127);
			varInt(output, 300);
		}
		ByteBuf sections = Unpooled.buffer();
		try {
			sectionData(sections, protocol, full, sky);
			varInt(output, sections.readableBytes());
			output.writeBytes(sections);
		} finally {
			sections.release();
		}
		if (protocol >= 757) {
			output.writeByte(2).writeByte(0xf1).writeShort(-64);
			varInt(output, 300);
			output.writeByte(0); // absent block entity tag
			output.writeByte(0x2f).writeShort(320).writeByte(3);
			nbt(output, protocol < 764);
		} else if (protocol >= 110) {
			output.writeByte(2);
			nbt(output, true);
			output.writeByte(0);
		}
		if (protocol >= 757 && protocol < 763) output.writeBoolean(true);
		if (protocol >= 757) light(output, false);
	}

	public static void sectionData(ByteBuf output, int protocol, boolean full, boolean sky) {
		if (protocol < 107) {
			for (int section = 0; section < 2; section++) {
				for (int i = 0; i < 4096; i++) {
					int state = (i % 17) * 16;
					output.writeByte(state).writeByte(state >>> 8);
				}
			}
			for (int section = 0; section < 2; section++) lightBytes(output, 0x12 + section);
			if (sky) for (int section = 0; section < 2; section++) lightBytes(output, 0x34 + section);
		} else {
			for (int section = 0; section < 2; section++) {
				if (protocol >= 477) output.writeShort(3855);
				if (protocol >= 775) output.writeShort(37);
				palette(output, protocol, false);
				if (protocol >= 757) palette(output, protocol, true);
				if (protocol < 477) {
					lightBytes(output, 0x12 + section);
					if (sky) lightBytes(output, 0x34 + section);
				}
			}
		}
		if (protocol < 573 && full) {
			for (int i = 0; i < 256; i++) {
				if (protocol < 393) output.writeByte(i);
				else output.writeInt(i + 256);
			}
		}
	}

	public static void palette(ByteBuf output, int protocol, boolean biome) {
		int bits = biome ? 3 : 5, size = biome ? 64 : 4096, paletteSize = biome ? 7 : 17;
		output.writeByte(bits);
		varInt(output, paletteSize);
		for (int i = 0; i < paletteSize; i++) varInt(output, biome ? i + 200 : i * 16);
		boolean padded = protocol >= 735;
		int length = padded ? (size + 64 / bits - 1) / (64 / bits) : (size * bits + 63) / 64;
		long[] words = new long[length];
		// Set individual bits independently of the production packing implementation.
		for (int i = 0; i < size; i++) {
			int start = padded ? (i / (64 / bits)) * 64 + (i % (64 / bits)) * bits : i * bits;
			for (int bit = 0; bit < bits; bit++) if (((i % paletteSize) & 1 << bit) != 0) {
				words[(start + bit) / 64] |= 1L << ((start + bit) % 64);
			}
		}
		if (protocol < 770) varInt(output, length);
		for (long word : words) output.writeLong(word);
	}

	private static void lightBytes(ByteBuf output, int value) {
		for (int i = 0; i < 2048; i++) output.writeByte(value);
	}

	public static void varInt(ByteBuf output, int value) {
		ByteBufStreamCodecs.VARINT.encode(output, value);
	}
}
