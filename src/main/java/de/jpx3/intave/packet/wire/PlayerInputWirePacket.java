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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerInputPacket;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;

public final class PlayerInputWirePacket implements MutablePlayerInputPacket {
	public final static StreamCodec<ByteBuf, ByteBuf, PlayerInputWirePacket> STREAM_CODEC = Input.STREAM_CODEC.beforeAndAfter(
		PlayerInputWirePacket::new, packet -> packet.input
	);

	private Input input;

	public PlayerInputWirePacket(Input input) {
		this.input = input;
	}

	@Override
	public Input input() {
		return input;
	}

	@Override
	public void setInput(Input input) {
		this.input = input;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion <= 600;
	}
}
