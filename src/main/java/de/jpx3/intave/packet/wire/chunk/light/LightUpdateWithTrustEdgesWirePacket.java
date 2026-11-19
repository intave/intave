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

public class LightUpdateWithTrustEdgesWirePacket extends LightUpdateWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, LightUpdateWithTrustEdgesWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::x,
		ByteBufStreamCodecs.VARINT, LightUpdateWirePacket::z,
		ByteBufStreamCodecs.BOOLEAN, LightUpdateWithTrustEdgesWirePacket::trustEdges,
		ChunkLightData.STREAM_CODEC, LightUpdateWirePacket::lightData,
		LightUpdateWithTrustEdgesWirePacket::new
	);
	private boolean trustEdges;

	public LightUpdateWithTrustEdgesWirePacket() { super(); }
	public LightUpdateWithTrustEdgesWirePacket(int x, int z, boolean trustEdges, ChunkLightData lightData) {
		super(x, z, lightData);
		this.trustEdges = trustEdges;
	}

	@Override
	public boolean appliesTo(int protocolVersion) { return protocolVersion >= 755 && protocolVersion < 763; }

	public boolean trustEdges() { return trustEdges; }
	public void setTrustEdges(boolean trustEdges) {
		markMutated();
		this.trustEdges = trustEdges;
	}
}
