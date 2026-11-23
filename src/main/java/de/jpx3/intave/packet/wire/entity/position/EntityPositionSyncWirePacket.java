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
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableEntityPositionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EntityPositionSyncWirePacket {
	private EntityPositionSyncWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol768.class,
			Protocol777.class
		));
	}

	static final class Protocol768 extends EntityPositionWirePacket.Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, EntityPositionWirePacket.Base::entityId,
				Position.STREAM_CODEC, EntityPositionWirePacket.Base::position,
				Motion.STREAM_CODEC, Protocol768::motion,
				Rotation.STREAM_CODEC, EntityPositionWirePacket.Base::rotation,
				ByteBufStreamCodecs.BOOLEAN, EntityPositionWirePacket.Base::onGround,
				Protocol768::new
			);

		private Motion motion;

		public Protocol768() {
			this(0, new Position(), Motion.newEmpty(), Rotation.zero(), false);
		}

		public Protocol768(
			int entityId, Position position, Motion motion, Rotation rotation, boolean onGround
		) {
			super(entityId, position, rotation, onGround);
			this.motion = motion;
		}

		public Motion motion() {
			markMutated();
			return motion;
		}

		public void setMotion(Motion value) {
			markMutated();
			motion = value;
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Server.ENTITY_POSITION_SYNC;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768 && protocolVersion < 777;
		}
	}

	static final class Protocol777 extends AbstractMutable implements MutableEntityPositionPacket {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol777::entityId,
				EntityPositionPath.STREAM_CODEC, Protocol777::path,
				Rotation.STREAM_CODEC, Protocol777::rotation,
				ByteBufStreamCodecs.BOOLEAN, Protocol777::onGround,
				Protocol777::new
			);

		private int entityId;
		private EntityPositionPath path;
		private Rotation rotation;
		private boolean onGround;

		public Protocol777() {
			this(0, EntityPositionPath.linear(new Position()), Rotation.zero(), false);
		}

		public Protocol777(int entityId, EntityPositionPath path, Rotation rotation, boolean onGround) {
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
			markMutated();
			entityId = value;
		}

		@Override
		public Position position() {
			Position position = path.endPosition();
			if (!position.isImmutable()) markMutated();
			return position;
		}

		@Override
		public void setPosition(Position value) {
			markMutated();
			path = path.withEndPosition(value);
		}

		public EntityPositionPath path() {
			if (path != null) {
				for (EntityPositionPath.Step step : path.steps()) {
					if (!step.position().isImmutable()) {
						markMutated();
						break;
					}
				}
			}
			return path;
		}

		public void setPath(EntityPositionPath value) {
			markMutated();
			path = value;
		}

		public Rotation rotation() {
			markMutated();
			return rotation;
		}

		public void setRotation(Rotation value) {
			markMutated();
			rotation = value;
		}

		public boolean onGround() {
			return onGround;
		}

		public void setOnGround(boolean value) {
			markMutated();
			onGround = value;
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Server.ENTITY_POSITION_SYNC;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 777;
		}
	}
}
