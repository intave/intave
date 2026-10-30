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

package de.jpx3.intave.packet.wire.player.positionrotation;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public final class PlayerPositionRotationOnGroundOnlyWirePacket extends PlayerPositionRotationWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerPositionRotationOnGroundOnlyWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, PlayerPositionRotationWirePacket::position,
			Rotation.STREAM_CODEC, PlayerPositionRotationWirePacket::rotation,
			ByteBufStreamCodecs.UNSIGNED_BYTE, PlayerPositionRotationWirePacket::flags,
			PlayerPositionRotationOnGroundOnlyWirePacket::new
		);

	public PlayerPositionRotationOnGroundOnlyWirePacket() {
		super();
	}

	public PlayerPositionRotationOnGroundOnlyWirePacket(
			Position position, Rotation rotation, int flags) {
		super(position, rotation, flags);
	}

	@Override
	public Boolean horizontalCollision() {
		return null;
	}

	@Override
	public void setHorizontalCollision(boolean value) {
		throw new IllegalStateException("No horizontal collision flag before 1.21.2");
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 768;
	}
}
