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

package de.jpx3.intave.packet.wire.chunk.unload;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableUnloadChunkPacket;
import io.netty.buffer.ByteBuf;

public class UnloadChunkWirePacket implements MutableUnloadChunkPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UnloadChunkWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, UnloadChunkWirePacket::z,
			ByteBufStreamCodecs.INTEGER, UnloadChunkWirePacket::x,
			UnloadChunkWirePacket::new
		);

	protected int x, z;

	public UnloadChunkWirePacket() {
		this(0, 0);
	}

	public UnloadChunkWirePacket(int z, int x) {
		this.z = z;
		this.x = x;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("UNLOAD_CHUNK".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 764;
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
		checkIndex(index);
		this.x = x;
		this.z = z;
	}

	protected static void checkIndex(int index) {
		if (index != 0) throw new IndexOutOfBoundsException("Chunk index: " + index);
	}

	public int x() {
		return x;
	}

	public int z() {
		return z;
	}
}
