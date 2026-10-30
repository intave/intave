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
import de.jpx3.intave.codec.VarLongCodec;
import de.jpx3.intave.packet.ConnectionState;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class WorldBorderLerpSizeWirePacket extends WorldBorderWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WorldBorderLerpSizeWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.DOUBLE, WorldBorderWirePacket::rawOldSize,
			ByteBufStreamCodecs.DOUBLE, WorldBorderWirePacket::rawNewSize,
			VarLongCodec.INSTANCE, WorldBorderWirePacket::rawLerpTime,
			WorldBorderLerpSizeWirePacket::new
		);

	public WorldBorderLerpSizeWirePacket() {
		action = Action.LERP_SIZE;
	}

	public WorldBorderLerpSizeWirePacket(double oldSize, double newSize, long lerpTime) {
		this();
		this.oldSize = oldSize;
		this.newSize = newSize;
		this.lerpTime = lerpTime;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "SET_BORDER_LERP_SIZE".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755;
	}

	@Override
	public void setAction(Action value) {
		if (Objects.requireNonNull(value, "action") != Action.LERP_SIZE)
			throw new IllegalArgumentException("Action is fixed for SET_BORDER_LERP_SIZE");
	}
}
