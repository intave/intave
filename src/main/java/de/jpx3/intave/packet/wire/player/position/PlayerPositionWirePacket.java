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

package de.jpx3.intave.packet.wire.player.position;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerMovePacket;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public class PlayerPositionWirePacket implements MutablePlayerMovePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerPositionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, PlayerPositionWirePacket::position,
			ByteBufStreamCodecs.UNSIGNED_BYTE, PlayerPositionWirePacket::flags,
			PlayerPositionWirePacket::new
		);

	protected Position position = new Position();

	protected int flags;

	public PlayerPositionWirePacket() {
		this(new Position(), 0);
	}

	public PlayerPositionWirePacket(Position position, int flags) {
		this.position = position;
		this.flags = flags;
	}

	@Override
	public Position position() {
		return this.position;
	}

	@Override
	public void setPosition(Position value) {
		this.position = value;
	}

	@Override
	public Rotation rotation() {
		return null;
	}

	@Override
	public void setRotation(Rotation value) {
		throw new UnsupportedOperationException("This packet does not support rotation");
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("POSITION".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}

	@Override
	public boolean isVehicleMove() {
		return false;
	}

	@Override
	public Boolean onGround() {
		return (flags & 1) != 0;
	}

	@Override
	public Boolean horizontalCollision() {
		return (flags & 2) != 0;
	}

	@Override
	public void setOnGround(boolean value) {
		flags = value ? flags | 1 : flags & ~1;
	}

	@Override
	public void setHorizontalCollision(boolean value) {
		flags = value ? flags | 2 : flags & ~2;
	}

	public int flags() {
		return flags;
	}
}
