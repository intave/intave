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

public final class TabCompleteRequestWithCommandFlagWirePacket
		extends TabCompleteRequestWithTargetWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TabCompleteRequestWithCommandFlagWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT_STRING, TabCompleteRequestWirePacket::text,
			ByteBufStreamCodecs.BOOLEAN, TabCompleteRequestWithCommandFlagWirePacket::assumeCommand,
			ByteBufStreamCodecs.optionalCodecOf(BlockPosition.LEGACY_STREAM_CODEC), TabCompleteRequestWithTargetWirePacket::target,
			TabCompleteRequestWithCommandFlagWirePacket::new
		);

	private boolean assumeCommand;

	public TabCompleteRequestWithCommandFlagWirePacket() {
		super();
	}

	public TabCompleteRequestWithCommandFlagWirePacket(
			String text, boolean assumeCommand, BlockPosition target) {
		super(text, target);
		this.assumeCommand = assumeCommand;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 393;
	}

	public boolean assumeCommand() {
		return assumeCommand;
	}
}
