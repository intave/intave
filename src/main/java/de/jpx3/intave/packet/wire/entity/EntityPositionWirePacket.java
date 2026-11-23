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

package de.jpx3.intave.packet.wire.entity;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableEntityPositionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.*;

public final class EntityPositionWirePacket {
	private EntityPositionWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityPositionPacket {
		protected int entityId;
		protected Position position;
		protected Rotation rotation;
		protected boolean onGround;

		protected Base() {
			this(0, new Position(), Rotation.zero(), false);
		}

		protected Base(int entityId, Position position, Rotation rotation, boolean onGround) {
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
			markMutated();
			entityId = value;
		}

		@Override
		public Position position() {
			if (position != null && !position.isImmutable()) markMutated();
			return position;
		}

		@Override
		public void setPosition(Position value) {
			markMutated();
			position = value;
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
			return state == ConnectionState.PLAY && type == PacketType.Server.ENTITY_TELEPORT;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::entityId,
				Position.FIXED_POINT_STREAM_CODEC, Base::position,
				Rotation.BYTE_STREAM_CODEC, Base::rotation,
				ByteBufStreamCodecs.BOOLEAN, Base::onGround,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int entityId, Position position, Rotation rotation, boolean onGround) {
			super(entityId, position, rotation, onGround);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol107::entityId,
				Position.STREAM_CODEC, Protocol107::position,
				Rotation.BYTE_STREAM_CODEC, Protocol107::rotation,
				ByteBufStreamCodecs.BOOLEAN, Protocol107::onGround,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(int entityId, Position position, Rotation rotation, boolean onGround) {
			super(entityId, position, rotation, onGround);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::entityId,
				Position.STREAM_CODEC, Base::position,
				Motion.STREAM_CODEC, Protocol768::motion,
				Rotation.STREAM_CODEC, Base::rotation,
				Relative.FLAGS_STREAM_CODEC, Protocol768::flags,
				ByteBufStreamCodecs.BOOLEAN, Base::onGround,
				Protocol768::new
			);

		private Motion motion;
		private Set<Relative> flags;

		public Protocol768() {
			this(0, new Position(), Motion.newEmpty(), Rotation.zero(), EnumSet.noneOf(Relative.class), false);
		}

		public Protocol768(
			int entityId, Position position, Motion motion, Rotation rotation, Set<Relative> flags, boolean onGround
		) {
			super(entityId, position, rotation, onGround);
			this.motion = motion;
			this.flags = flags;
		}

		public Motion motion() {
			markMutated();
			return motion;
		}

		public void setMotion(Motion value) {
			markMutated();
			motion = value;
		}

		public Set<Relative> flags() {
			markMutated();
			return flags;
		}

		public void setFlags(Set<Relative> value) {
			markMutated();
			flags = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}
	}
}
