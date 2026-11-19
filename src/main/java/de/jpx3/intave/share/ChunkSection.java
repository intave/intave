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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

/** One section in wire order. Counts use -1 when the protocol does not transmit them. */
public final class ChunkSection {
	private final int nonAirCount, fluidCount;
	private final ChunkPalette blockStates, biomes;
	private final byte[] blockLight, skyLight;

	public ChunkSection(int nonAirCount, ChunkPalette blockStates, ChunkPalette biomes) {
		this(nonAirCount, -1, blockStates, biomes, null, null);
	}

	public ChunkSection(int nonAirCount, int fluidCount, ChunkPalette blockStates, ChunkPalette biomes, byte[] blockLight, byte[] skyLight) {
		if (nonAirCount < -1 || nonAirCount > 4096 || fluidCount < -1 || fluidCount > 4096) throw new IllegalArgumentException("Invalid section count");
		if (Objects.requireNonNull(blockStates, "blockStates").kind() != ChunkPalette.Kind.BLOCK_STATES) throw new IllegalArgumentException("Expected block state palette");
		if (biomes != null && biomes.kind() != ChunkPalette.Kind.BIOMES) throw new IllegalArgumentException("Expected biome palette");
		this.nonAirCount = nonAirCount;
		this.fluidCount = fluidCount;
		this.blockStates = blockStates;
		this.biomes = biomes;
		this.blockLight = copyLight(blockLight);
		this.skyLight = copyLight(skyLight);
	}

	public int nonAirCount() { return nonAirCount; }
	public int fluidCount() { return fluidCount; }
	public ChunkPalette blockStates() { return blockStates; }
	public ChunkPalette biomes() { return biomes; }
	public byte[] blockLight() { return blockLight == null ? null : blockLight.clone(); }
	public byte[] skyLight() { return skyLight == null ? null : skyLight.clone(); }

	public static StreamCodec<ByteBuf, ByteBuf, ChunkSection> streamCodec(int protocol, boolean skyLight) {
		StreamCodec<ByteBuf, ByteBuf, ChunkPalette> states = ChunkPalette.streamCodec(ChunkPalette.Kind.BLOCK_STATES, protocol);
		StreamCodec<ByteBuf, ByteBuf, ChunkPalette> biomes = protocol >= 757 ? ChunkPalette.streamCodec(ChunkPalette.Kind.BIOMES, protocol) : null;
		return StreamCodec.of((output, section) -> {
			if (protocol >= 477) {
				if (section.nonAirCount < 0) throw new IllegalArgumentException("Missing non-air block count");
				output.writeShort(section.nonAirCount);
			}
			if (protocol >= 775) {
				if (section.fluidCount < 0) throw new IllegalArgumentException("Missing fluid count");
				output.writeShort(section.fluidCount);
			}
			states.encode(output, section.blockStates);
			if (biomes != null) biomes.encode(output, Objects.requireNonNull(section.biomes, "biomes"));
			else if (section.biomes != null) throw new IllegalArgumentException("Section biomes are absent in this protocol");
			if (protocol < 477) {
				writeLight(output, section.blockLight);
				if (skyLight) writeLight(output, section.skyLight);
				else if (section.skyLight != null) throw new IllegalArgumentException("Unexpected sky light");
			} else if (section.blockLight != null || section.skyLight != null) throw new IllegalArgumentException("Section light is absent in this protocol");
		}, input -> {
			int count = protocol >= 477 ? input.readUnsignedShort() : -1;
			int fluids = protocol >= 775 ? input.readUnsignedShort() : -1;
			ChunkPalette blocks = states.decode(input);
			ChunkPalette biomeData = biomes == null ? null : biomes.decode(input);
			byte[] block = protocol < 477 ? ByteBufStreamCodecs.bytes(input, 2048) : null;
			byte[] sky = protocol < 477 && skyLight ? ByteBufStreamCodecs.bytes(input, 2048) : null;
			return new ChunkSection(count, fluids, blocks, biomeData, block, sky);
		});
	}

	static void writeLight(ByteBuf output, byte[] light) {
		if (light == null) throw new IllegalArgumentException("Missing section light");
		output.writeBytes(light);
	}

	private static byte[] copyLight(byte[] light) {
		if (light == null) return null;
		if (light.length != 2048) throw new IllegalArgumentException("Section light requires 2048 bytes");
		return light.clone();
	}
}

