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
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public final class EntityPositionFixedPointWirePacket extends EntityPositionDoublePositionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionFixedPointWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityPositionDoublePositionWirePacket::entityId,
			Position.FIXED_POINT_STREAM_CODEC, EntityPositionDoublePositionWirePacket::position,
			Rotation.BYTE_STREAM_CODEC, EntityPositionDoublePositionWirePacket::rotation,
			ByteBufStreamCodecs.BOOLEAN, EntityPositionDoublePositionWirePacket::onGround,
			EntityPositionFixedPointWirePacket::new
		);

	public EntityPositionFixedPointWirePacket() {
		super();
	}

	public EntityPositionFixedPointWirePacket(int entityId, Position position, Rotation rotation, boolean onGround) {
		super(entityId, position, rotation, onGround);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}
}
