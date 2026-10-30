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

package de.jpx3.intave.packet.wire.entity.position;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public final class EntityPositionSyncPositionMoveRotationWirePacket extends EntityPositionDoublePositionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionSyncPositionMoveRotationWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityPositionDoublePositionWirePacket::entityId,
			Position.STREAM_CODEC, EntityPositionDoublePositionWirePacket::position,
			Motion.STREAM_CODEC, EntityPositionSyncPositionMoveRotationWirePacket::motion,
			Rotation.STREAM_CODEC, EntityPositionDoublePositionWirePacket::rotation,
			ByteBufStreamCodecs.BOOLEAN, EntityPositionDoublePositionWirePacket::onGround,
			EntityPositionSyncPositionMoveRotationWirePacket::new
		);

	private Motion motion;

	public EntityPositionSyncPositionMoveRotationWirePacket() {
		this(0, new Position(), Motion.newEmpty(), Rotation.zero(), false);
	}

	public EntityPositionSyncPositionMoveRotationWirePacket(
		int entityId, Position position, Motion motion, Rotation rotation, boolean onGround
	) {
		super(entityId, position, rotation, onGround);
		this.motion = motion;
	}

	public Motion motion() {
		return motion;
	}

	public void setMotion(Motion value) {
		motion = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "ENTITY_POSITION_SYNC".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768 && protocolVersion < 777;
	}
}
