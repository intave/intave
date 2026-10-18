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

import java.util.Objects;

public final class PlayerInputWirePacket implements MutablePlayerInputPacket {
	public final static StreamCodec<ByteBuf, ByteBuf, PlayerInputWirePacket> STREAM_CODEC = Input.STREAM_CODEC.beforeAndAfter(
		PlayerInputWirePacket::new, PlayerInputWirePacket::input
	);

	private Input input;

	public PlayerInputWirePacket() {
		this.input = Input.none();
	}

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
		// 1.21.2 replaced the analog axes and flags with a packed input byte.
		return protocolVersion >= 768;
	}

	@Override
	public String toString() {
		return "PlayerInputWirePacket{" +
			"input=" + input +
			'}';
	}

	@Override
	public int hashCode() {
		int result = input != null ? input.hashCode() : 0;
		result = 31 * result + (input != null ? input.hashCode() : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PlayerInputWirePacket that = (PlayerInputWirePacket) o;
		return Objects.equals(input, that.input);
	}
}
