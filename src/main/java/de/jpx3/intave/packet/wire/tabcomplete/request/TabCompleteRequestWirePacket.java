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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableTabCompleteRequestPacket;
import io.netty.buffer.ByteBuf;

public class TabCompleteRequestWirePacket implements MutableTabCompleteRequestPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TabCompleteRequestWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, TabCompleteRequestWirePacket::transactionId,
			ByteBufStreamCodecs.VARINT_STRING, TabCompleteRequestWirePacket::text,
			TabCompleteRequestWirePacket::new
		);

	protected String text = "";

	protected int transactionId;

	public TabCompleteRequestWirePacket() {
		this(0, "");
	}

	public TabCompleteRequestWirePacket(int transactionId, String text) {
		this.transactionId = transactionId;
		this.text = text;
	}

	@Override
	public String text() {
		return this.text;
	}

	@Override
	public void setText(String value) {
		this.text = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("TAB_COMPLETE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 393;
	}

	public int transactionId() {
		return transactionId;
	}
}
