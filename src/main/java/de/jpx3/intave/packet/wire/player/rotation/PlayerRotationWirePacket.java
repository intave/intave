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

package de.jpx3.intave.packet.wire.player.rotation;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerMovePacket;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public class PlayerRotationWirePacket implements MutablePlayerMovePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerRotationWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Rotation.STREAM_CODEC, PlayerRotationWirePacket::rotation,
			ByteBufStreamCodecs.UNSIGNED_BYTE, PlayerRotationWirePacket::flags,
			PlayerRotationWirePacket::new
		);

	protected Rotation rotation = new Rotation(0, 0);

	protected int flags;

	public PlayerRotationWirePacket() {
		this(new Rotation(0, 0), 0);
	}

	public PlayerRotationWirePacket(Rotation rotation, int flags) {
		this.rotation = rotation;
		this.flags = flags;
	}

	@Override
	public Position position() {
		return null;
	}

	@Override
	public void setPosition(Position value) {
		throw new UnsupportedOperationException("This packet does not support position");
	}

	@Override
	public Rotation rotation() {
		return this.rotation;
	}

	@Override
	public void setRotation(Rotation value) {
		this.rotation = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ROTATION".equals(name));
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
