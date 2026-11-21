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
import de.jpx3.intave.packet.contract.mutable.MutableChunkBatchFinishedPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

public final class ChunkBatchFinishedWirePacket extends AbstractMutable implements MutableChunkBatchFinishedPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBatchFinishedWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.VARINT, ChunkBatchFinishedWirePacket::batchSize, ChunkBatchFinishedWirePacket::new
	);
	private int batchSize;

	public ChunkBatchFinishedWirePacket() { this(0); }

	public ChunkBatchFinishedWirePacket(int batchSize) {
		this.batchSize = batchSize;
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 764; }

	@Override
	public int batchSize() { return batchSize; }

	@Override
	public void setBatchSize(int batchSize) {
		markMutated();
		this.batchSize = batchSize;
	}
}
