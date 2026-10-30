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
import de.jpx3.intave.packet.contract.mutable.MutableEntityPositionPacket;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public final class EntityPositionSyncWirePacket implements MutableEntityPositionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionSyncWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityPositionSyncWirePacket::entityId,
			EntityPositionPath.STREAM_CODEC, EntityPositionSyncWirePacket::path,
			Rotation.STREAM_CODEC, EntityPositionSyncWirePacket::rotation,
			ByteBufStreamCodecs.BOOLEAN, EntityPositionSyncWirePacket::onGround,
			EntityPositionSyncWirePacket::new
		);

	private int entityId;
	private EntityPositionPath path;
	private Rotation rotation;
	private boolean onGround;

	public EntityPositionSyncWirePacket() {
		this(0, EntityPositionPath.linear(new Position()), Rotation.zero(), false);
	}

	public EntityPositionSyncWirePacket(int entityId, EntityPositionPath path, Rotation rotation, boolean onGround) {
		this.entityId = entityId;
		this.path = path;
		this.rotation = rotation;
		this.onGround = onGround;
	}

	@Override
	public int entityId() {
		return entityId;
	}

	@Override
	public void setEntityId(int value) {
		entityId = value;
	}

	@Override
	public Position position() {
		return path.endPosition();
	}

	@Override
	public void setPosition(Position value) {
		path = path.withEndPosition(value);
	}

	public EntityPositionPath path() {
		return path;
	}

	public void setPath(EntityPositionPath value) {
		path = value;
	}

	public Rotation rotation() {
		return rotation;
	}

	public void setRotation(Rotation value) {
		rotation = value;
	}

	public boolean onGround() {
		return onGround;
	}

	public void setOnGround(boolean value) {
		onGround = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "ENTITY_POSITION_SYNC".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 777;
	}
}
