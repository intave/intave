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

package de.jpx3.intave.packet.wire.chunk.batch;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableChunkBatchReceivedPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

public final class ChunkBatchReceivedWirePacket extends AbstractMutable implements MutableChunkBatchReceivedPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBatchReceivedWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.FLOAT, ChunkBatchReceivedWirePacket::desiredChunksPerTick, ChunkBatchReceivedWirePacket::new
	);
	private float desiredChunksPerTick;

	public ChunkBatchReceivedWirePacket() { this(0F); }

	public ChunkBatchReceivedWirePacket(float desiredChunksPerTick) {
		this.desiredChunksPerTick = desiredChunksPerTick;
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 764; }

	@Override
	public float desiredChunksPerTick() { return desiredChunksPerTick; }

	@Override
	public void setDesiredChunksPerTick(float desiredChunksPerTick) {
		markMutated();
		this.desiredChunksPerTick = desiredChunksPerTick;
	}
}
