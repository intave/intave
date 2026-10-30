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

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class WorldBorderSetCenterWirePacket extends WorldBorderWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WorldBorderSetCenterWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						output.writeDouble(packet.center.getX());
						output.writeDouble(packet.center.getZ());
					},
					input -> {
						Position center = new Position(input.readDouble(), 0, input.readDouble());
						return new WorldBorderSetCenterWirePacket(center);
					});

	public WorldBorderSetCenterWirePacket() {
		action = Action.SET_CENTER;
	}

	public WorldBorderSetCenterWirePacket(Position center) {
		this();
		this.center = center;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "SET_BORDER_CENTER".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755;
	}

	@Override
	public void setAction(Action value) {
		if (Objects.requireNonNull(value, "action") != Action.SET_CENTER)
			throw new IllegalArgumentException("Action is fixed for SET_BORDER_CENTER");
	}
}
