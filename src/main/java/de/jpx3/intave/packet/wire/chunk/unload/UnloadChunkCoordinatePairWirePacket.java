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
import io.netty.buffer.ByteBuf;

public final class UnloadChunkCoordinatePairWirePacket extends UnloadChunkWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UnloadChunkCoordinatePairWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, UnloadChunkWirePacket::x,
			ByteBufStreamCodecs.INTEGER, UnloadChunkWirePacket::z,
			UnloadChunkCoordinatePairWirePacket::new
		);

	public UnloadChunkCoordinatePairWirePacket() {
		super();
	}

	public UnloadChunkCoordinatePairWirePacket(int x, int z) {
		super(z, x);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 764;
	}
}
