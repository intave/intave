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

package de.jpx3.intave.packet.wire.chunk.light;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableLightUpdatePacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.ChunkLightData;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public class LightUpdateWirePacket extends MutableWirePacket implements MutableLightUpdatePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, LightUpdateWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::x,
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::z,
		ChunkLightData.STREAM_CODEC, LightUpdateWirePacket::lightData,
		LightUpdateWirePacket::new
	);
	protected int x, z;
	protected ChunkLightData lightData;

	public LightUpdateWirePacket() { this(0, 0, new ChunkLightData()); }
	public LightUpdateWirePacket(int x, int z, ChunkLightData lightData) {
		this.x = x;
		this.z = z;
		this.lightData = Objects.requireNonNull(lightData, "lightData");
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 763; }
	public int x() { return x; }
	public int z() { return z; }
	@Override
	public int chunkX(int index) { checkIndex(index); return x; }
	@Override
	public int chunkZ(int index) { checkIndex(index); return z; }
	@Override
	public ChunkLightData lightData() { return lightData; }

	@Override
	public void setChunkCoordinates(int index, int x, int z) {
		markMutated();
		checkIndex(index);
		this.x = x;
		this.z = z;
	}

	@Override
	public void setLightData(ChunkLightData lightData) {
		markMutated();
		this.lightData = Objects.requireNonNull(lightData, "lightData");
	}

	private static void checkIndex(int index) {
		if (index != 0) throw new IndexOutOfBoundsException("Chunk index: " + index);
	}
}
