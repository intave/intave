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

package de.jpx3.intave.packet.wire.tabcomplete.request;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;

public class TabCompleteRequestWithTargetWirePacket extends TabCompleteRequestWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TabCompleteRequestWithTargetWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT_STRING, TabCompleteRequestWirePacket::text,
			ByteBufStreamCodecs.optionalCodecOf(BlockPosition.LEGACY_STREAM_CODEC), TabCompleteRequestWithTargetWirePacket::target,
			TabCompleteRequestWithTargetWirePacket::new
		);

	protected BlockPosition target;

	public TabCompleteRequestWithTargetWirePacket() {
		super();
	}

	public TabCompleteRequestWithTargetWirePacket(String text, BlockPosition target) {
		super(0, text);
		this.target = target;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	public BlockPosition target() {
		return target;
	}
}
