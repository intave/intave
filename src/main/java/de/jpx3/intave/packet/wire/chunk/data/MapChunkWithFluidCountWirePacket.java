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
import io.netty.buffer.ByteBuf;

public final class MapChunkWithFluidCountWirePacket extends MapChunkWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MapChunkWithFluidCountWirePacket> STREAM_CODEC = streamCodec(MapChunkWithFluidCountWirePacket::new);

	public MapChunkWithFluidCountWirePacket() {
		super(MapChunkFormat.FLUID_COUNTS);
	}
}

