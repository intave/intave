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

package de.jpx3.intave.packet.wire.entity.velocity;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

public class EntityVelocityWirePacket implements MutableEntityVelocityPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityVelocityWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityVelocityWirePacket::entityId,
			Motion.LOW_PRECISION_STREAM_CODEC, EntityVelocityWirePacket::motion,
			EntityVelocityWirePacket::new
		);

	protected int entityId = 0;

	protected Motion motion = new Motion(0, 0, 0);

	public EntityVelocityWirePacket() {
		this(0, new Motion(0, 0, 0));
	}

	public EntityVelocityWirePacket(int entityId, Motion motion) {
		this.entityId = entityId;
		this.motion = motion;
	}

	@Override
	public int entityId() {
		return this.entityId;
	}

	@Override
	public void setEntityId(int value) {
		this.entityId = value;
	}

	@Override
	public Motion motion() {
		return this.motion;
	}

	@Override
	public void setMotion(Motion value) {
		this.motion = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ENTITY_VELOCITY".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 773;
	}
}
