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

package de.jpx3.intave.packet.wire.chat.message;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableChatPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

public class ChatUnsignedWirePacket extends MutableWirePacket implements MutableChatPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatUnsignedWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(256), ChatUnsignedWirePacket::text,
			ChatUnsignedWirePacket::new
		);

	private String text;

	public ChatUnsignedWirePacket() {
		this("");
	}

	public ChatUnsignedWirePacket(String text) {
		this.text = text;
	}

	@Override
	public String text() {
		return text;
	}

	@Override
	public void setText(String value) {
		markMutated();
		text = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 315 && protocolVersion < 759;
	}
}
