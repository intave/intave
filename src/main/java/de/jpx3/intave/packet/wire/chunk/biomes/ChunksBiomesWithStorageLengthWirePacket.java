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

package de.jpx3.intave.packet.wire.chunk.biomes;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.ChunkBiomeData;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class ChunksBiomesWithStorageLengthWirePacket extends ChunksBiomesWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunksBiomesWithStorageLengthWirePacket> STREAM_CODEC =
		streamCodec(ChunkBiomeData.LENGTH_PREFIXED_STORAGE_STREAM_CODEC, ChunksBiomesWithStorageLengthWirePacket::new);

	public ChunksBiomesWithStorageLengthWirePacket() {}
	public ChunksBiomesWithStorageLengthWirePacket(List<ChunkBiomeData> chunks) { super(chunks); }

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 762 && protocolVersion < 770; }
}

