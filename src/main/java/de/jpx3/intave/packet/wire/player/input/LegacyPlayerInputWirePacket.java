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

package de.jpx3.intave.packet.wire.player.input;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerInputPacket;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;

/** The two analog axes must survive a round trip without being quantized to keys. */
public final class LegacyPlayerInputWirePacket implements MutablePlayerInputPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, LegacyPlayerInputWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.FLOAT, LegacyPlayerInputWirePacket::strafe,
			ByteBufStreamCodecs.FLOAT, LegacyPlayerInputWirePacket::forward,
			ByteBufStreamCodecs.UNSIGNED_BYTE, LegacyPlayerInputWirePacket::flags,
			LegacyPlayerInputWirePacket::new
		);

	private float forward, strafe;

	private int flags;

	public LegacyPlayerInputWirePacket() {
		this(0, 0, 0);
	}

	public LegacyPlayerInputWirePacket(float strafe, float forward, int flags) {
		this.strafe = strafe;
		this.forward = forward;
		this.flags = flags;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "PLAYER_INPUT".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 768;
	}

	@Override
	public Input input() {
		return null;
	}

	@Override
	public Float forward() {
		return forward;
	}

	@Override
	public Float strafe() {
		return strafe;
	}

	@Override
	public boolean jumping() {
		return (flags & 1) != 0;
	}

	public boolean dismounting() {
		return (flags & 2) != 0;
	}

	@Override
	public void setAxes(float forward, float strafe) {
		this.forward = forward;
		this.strafe = strafe;
	}

	@Override
	public void setJumping(boolean jumping) {
		flags = jumping ? flags | 1 : flags & ~1;
	}

	public void setDismounting(boolean dismounting) {
		flags = dismounting ? flags | 2 : flags & ~2;
	}

	@Override
	public void setInput(Input input) {
		setAxes(input.forward(), input.strafe());
		setJumping(input.jumpKey());
		setDismounting(input.sneakKey());
	}

	public int flags() {
		return flags;
	}
}
