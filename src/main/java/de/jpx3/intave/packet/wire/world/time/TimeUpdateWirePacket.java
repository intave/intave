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

package de.jpx3.intave.packet.wire.world.time;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableTimeUpdatePacket;
import io.netty.buffer.ByteBuf;

public class TimeUpdateWirePacket implements MutableTimeUpdatePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TimeUpdateWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.LONG, TimeUpdateWirePacket::gameTime,
			ByteBufStreamCodecs.LONG, TimeUpdateWirePacket::dayTime,
			ByteBufStreamCodecs.BOOLEAN, TimeUpdateWirePacket::ticking,
			TimeUpdateWirePacket::new
		);

	protected long gameTime = 0;

	protected long dayTime;

	protected boolean ticking = true;

	public TimeUpdateWirePacket() {
		this(0, 0, true);
	}

	public TimeUpdateWirePacket(long gameTime, long dayTime, boolean ticking) {
		this.gameTime = gameTime;
		this.dayTime = dayTime;
		this.ticking = ticking;
	}

	@Override
	public long gameTime() {
		return this.gameTime;
	}

	@Override
	public void setGameTime(long value) {
		this.gameTime = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("UPDATE_TIME".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768 && protocolVersion < 775;
	}

	@Override
	public long dayTime() {
		return dayTime;
	}

	@Override
	public void setDayTime(long value) {
		dayTime = value;
	}

	@Override
	public boolean ticking() {
		return ticking;
	}

	@Override
	public void setTicking(boolean value) {
		ticking = value;
	}
}
