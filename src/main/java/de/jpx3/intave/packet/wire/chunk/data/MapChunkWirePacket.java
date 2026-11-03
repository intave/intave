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

package de.jpx3.intave.packet.wire.chunk.data;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMapChunkPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.bytes;

public class MapChunkWirePacket extends MutableWirePacket implements MutableMapChunkPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MapChunkWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {
				output.writeInt(packet.x);
				output.writeInt(packet.z);
				output.writeBytes(packet.chunkData);
			},
			input -> {
				int x = input.readInt();
				int z = input.readInt();
				byte[] chunkData = bytes(input, input.readableBytes());
				return new MapChunkWirePacket(x, z, chunkData);
			});

	private int x, z;

	private byte[] chunkData = new byte[0];

	public MapChunkWirePacket() {
		this(0, 0, new byte[0]);
	}

	public MapChunkWirePacket(int x, int z, byte[] chunkData) {
		this.x = x;
		this.z = z;
		this.chunkData = chunkData;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}

	@Override
	public int chunkX(int index) {
		checkIndex(index);
		return x;
	}

	@Override
	public int chunkZ(int index) {
		checkIndex(index);
		return z;
	}

	@Override
	public void setChunkCoordinates(int index, int x, int z) {
		markMutated();
		checkIndex(index);
		this.x = x;
		this.z = z;
	}

	public byte[] chunkData() {
		markMutated();
		return chunkData.clone();
	}

	public void setChunkData(byte[] data) {
		markMutated();
		chunkData = data.clone();
	}

	private static void checkIndex(int index) {
		if (index != 0) throw new IndexOutOfBoundsException("Chunk index: " + index);
	}
}
