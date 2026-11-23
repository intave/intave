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

package de.jpx3.intave.packet.wire.player.ground;

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

public final class PlayerGroundWirePacket {
	private PlayerGroundWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutablePlayerMovePacket {
		protected int flags;

		protected Base() {
			this(0);
		}

		protected Base(int flags) {
			this.flags = flags;
		}

		@Override
		public Position position() {
			return null;
		}

		@Override
		public void setPosition(Position value) {
			markMutated();
			throw new UnsupportedOperationException("This packet does not support position");
		}

		@Override
		public Rotation rotation() {
			return null;
		}

		@Override
		public void setRotation(Rotation value) {
			markMutated();
			throw new UnsupportedOperationException("This packet does not support rotation");
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Client.FLYING;
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
		public abstract Boolean horizontalCollision();

		@Override
		public void setOnGround(boolean value) {
			markMutated();
			flags = value ? flags | 1 : flags & ~1;
		}

		@Override
		public abstract void setHorizontalCollision(boolean value);

		public int flags() {
			return flags;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::flags,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int flags) {
			super(flags);
		}

		@Override
		public Boolean horizontalCollision() {
			return null;
		}

		@Override
		public void setHorizontalCollision(boolean value) {
			markMutated();
			throw new IllegalStateException("No horizontal collision flag before 1.21.2");
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Protocol768::flags,
				Protocol768::new
			);

		public Protocol768() {
			super();
		}

		public Protocol768(int flags) {
			super(flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}

		@Override
		public Boolean horizontalCollision() {
			return (flags & 2) != 0;
		}

		@Override
		public void setHorizontalCollision(boolean value) {
			markMutated();
			flags = value ? flags | 2 : flags & ~2;
		}
	}
}
