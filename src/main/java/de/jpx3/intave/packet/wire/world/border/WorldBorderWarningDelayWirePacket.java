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

package de.jpx3.intave.packet.wire.world.border;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class WorldBorderWarningDelayWirePacket extends WorldBorderWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WorldBorderWarningDelayWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, WorldBorderWirePacket::warningDelay,
			WorldBorderWarningDelayWirePacket::new
		);

	public WorldBorderWarningDelayWirePacket() {
		action = Action.WARNING_DELAY;
	}

	public WorldBorderWarningDelayWirePacket(int warningDelay) {
		this();
		this.warningDelay = warningDelay;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "SET_BORDER_WARNING_DELAY".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755;
	}

	@Override
	public void setAction(Action value) {
		if (Objects.requireNonNull(value, "action") != Action.WARNING_DELAY)
			throw new IllegalArgumentException("Action is fixed for SET_BORDER_WARNING_DELAY");
	}
}
