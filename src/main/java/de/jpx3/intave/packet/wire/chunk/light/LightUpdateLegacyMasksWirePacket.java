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
import de.jpx3.intave.share.ChunkLightData;
import io.netty.buffer.ByteBuf;

public final class LightUpdateLegacyMasksWirePacket extends LightUpdateWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, LightUpdateLegacyMasksWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::x,
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::z,
		ChunkLightData.LEGACY_STREAM_CODEC, LightUpdateWirePacket::lightData,
		LightUpdateLegacyMasksWirePacket::new
	);

	public LightUpdateLegacyMasksWirePacket() { super(); }
	public LightUpdateLegacyMasksWirePacket(int x, int z, ChunkLightData lightData) {
		super(x, z, lightData);
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 477 && protocolVersion < 735; }
}
