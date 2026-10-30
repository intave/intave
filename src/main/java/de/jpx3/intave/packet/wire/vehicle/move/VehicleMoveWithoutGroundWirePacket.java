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

package de.jpx3.intave.packet.wire.vehicle.move;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public final class VehicleMoveWithoutGroundWirePacket extends VehicleMoveWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, VehicleMoveWithoutGroundWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, VehicleMoveWirePacket::position,
			Rotation.STREAM_CODEC, VehicleMoveWirePacket::rotation,
			VehicleMoveWithoutGroundWirePacket::new
		);

	public VehicleMoveWithoutGroundWirePacket() {
		super();
	}

	public VehicleMoveWithoutGroundWirePacket(Position position, Rotation rotation) {
		super(position, rotation, false);
	}

	@Override
	public Boolean onGround() {
		return null;
	}

	@Override
	public void setOnGround(boolean value) {
		throw new IllegalStateException("No ground flag before 1.21.4");
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 769;
	}
}
