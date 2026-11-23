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

package de.jpx3.intave.packet.wire.vehicle.move;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerMovePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VehicleMoveWirePacket {
	private VehicleMoveWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol107.class,
			Protocol769.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutablePlayerMovePacket {
		protected Position position;
		protected Rotation rotation;
		protected boolean onGround;

		protected Base() {
			this(new Position(), new Rotation(0, 0), false);
		}

		protected Base(Position position, Rotation rotation, boolean onGround) {
			this.position = position;
			this.rotation = rotation;
			this.onGround = onGround;
		}

		@Override
		public Position position() {
			if (position != null && !position.isImmutable()) markMutated();
			return this.position;
		}

		@Override
		public void setPosition(Position value) {
			markMutated();
			this.position = value;
		}

		@Override
		public Rotation rotation() {
			markMutated();
			return this.rotation;
		}

		@Override
		public void setRotation(Rotation value) {
			markMutated();
			this.rotation = value;
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Client.VEHICLE_MOVE;
		}

		@Override
		public boolean isVehicleMove() {
			return true;
		}

		@Override
		public abstract Boolean onGround();

		@Override
		public Boolean horizontalCollision() {
			return null;
		}

		@Override
		public abstract void setOnGround(boolean value);

		@Override
		public void setHorizontalCollision(boolean value) {
			markMutated();
			throw new IllegalStateException("Vehicle packets have no horizontal collision flag");
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Base::position,
				Rotation.STREAM_CODEC, Base::rotation,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(Position position, Rotation rotation) {
			super(position, rotation, false);
		}

		@Override
		public Boolean onGround() {
			return null;
		}

		@Override
		public void setOnGround(boolean value) {
			markMutated();
			throw new IllegalStateException("No ground flag before 1.21.4");
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 769;
		}
	}

	static final class Protocol769 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol769> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Protocol769::position,
				Rotation.STREAM_CODEC, Protocol769::rotation,
				ByteBufStreamCodecs.BOOLEAN, Protocol769::onGround,
				Protocol769::new
			);

		public Protocol769() {
			super();
		}

		public Protocol769(Position position, Rotation rotation, boolean onGround) {
			super(position, rotation, onGround);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 769;
		}

		@Override
		public Boolean onGround() {
			return onGround;
		}

		@Override
		public void setOnGround(boolean value) {
			markMutated();
			onGround = value;
		}
	}
}
