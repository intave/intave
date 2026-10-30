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

public class EntityPositionDoublePositionWirePacket implements MutableEntityPositionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionDoublePositionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityPositionDoublePositionWirePacket::entityId,
			Position.STREAM_CODEC, EntityPositionDoublePositionWirePacket::position,
			Rotation.BYTE_STREAM_CODEC, EntityPositionDoublePositionWirePacket::rotation,
			ByteBufStreamCodecs.BOOLEAN, EntityPositionDoublePositionWirePacket::onGround,
			EntityPositionDoublePositionWirePacket::new
		);

	private int entityId;
	private Position position;
	private Rotation rotation;
	private boolean onGround;

	public EntityPositionDoublePositionWirePacket() {
		this(0, new Position(), Rotation.zero(), false);
	}

	public EntityPositionDoublePositionWirePacket(int entityId, Position position, Rotation rotation, boolean onGround) {
		this.entityId = entityId;
		this.position = position;
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
		return position;
	}

	@Override
	public void setPosition(Position value) {
		position = value;
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
		return state == ConnectionState.PLAY && "ENTITY_TELEPORT".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 768;
	}
}
