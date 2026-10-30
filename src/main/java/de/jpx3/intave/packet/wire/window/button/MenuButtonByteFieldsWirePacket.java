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

package de.jpx3.intave.packet.wire.window.button;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class MenuButtonByteFieldsWirePacket extends MenuButtonWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MenuButtonByteFieldsWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.SIGNED_BYTE, MenuButtonWirePacket::containerId,
			ByteBufStreamCodecs.SIGNED_BYTE, MenuButtonWirePacket::button,
			MenuButtonByteFieldsWirePacket::new
		);

	public MenuButtonByteFieldsWirePacket() {
		super();
	}

	public MenuButtonByteFieldsWirePacket(int containerId, int button) {
		super(containerId, button);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 766;
	}
}
