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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerMovePacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public class VehicleMoveWirePacket extends MutableWirePacket implements MutablePlayerMovePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, VehicleMoveWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, VehicleMoveWirePacket::position,
			Rotation.STREAM_CODEC, VehicleMoveWirePacket::rotation,
			ByteBufStreamCodecs.BOOLEAN, VehicleMoveWirePacket::onGround,
			VehicleMoveWirePacket::new
		);

	protected Position position;
	protected Rotation rotation;
	protected boolean onGround;

	public VehicleMoveWirePacket() {
		this(new Position(), new Rotation(0, 0), false);
	}

	public VehicleMoveWirePacket(Position position, Rotation rotation, boolean onGround) {
		this.position = position;
		this.rotation = rotation;
		this.onGround = onGround;
	}

	@Override
	public Position position() {
		if (position != null && !position.isImmutable()) markMutated();
		return this.position;
	}

	@Override
	public void setPosition(Position value) {
		markMutated();
		this.position = value;
	}

	@Override
	public Rotation rotation() {
		markMutated();
		return this.rotation;
	}

	@Override
	public void setRotation(Rotation value) {
		markMutated();
		this.rotation = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 769;
	}

	@Override
	public boolean isVehicleMove() {
		return true;
	}

	@Override
	public Boolean onGround() {
		return onGround;
	}

	@Override
	public Boolean horizontalCollision() {
		return null;
	}

	@Override
	public void setOnGround(boolean value) {
		markMutated();
		onGround = value;
	}

	@Override
	public void setHorizontalCollision(boolean value) {
		markMutated();
		throw new IllegalStateException("Vehicle packets have no horizontal collision flag");
	}
}
