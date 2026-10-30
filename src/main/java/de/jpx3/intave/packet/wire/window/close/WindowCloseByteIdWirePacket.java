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

package de.jpx3.intave.packet.wire.window.close;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class WindowCloseByteIdWirePacket extends WindowCloseWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WindowCloseByteIdWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, WindowCloseWirePacket::containerId,
			WindowCloseByteIdWirePacket::new
		);

	public WindowCloseByteIdWirePacket() {
		super();
	}

	public WindowCloseByteIdWirePacket(int containerId) {
		super(containerId);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 768;
	}
}
