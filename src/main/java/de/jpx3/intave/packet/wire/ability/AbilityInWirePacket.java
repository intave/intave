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

package de.jpx3.intave.packet.wire.ability;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableAbilityInPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AbilityInWirePacket {
	private AbilityInWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol735.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableAbilityInPacket {
		protected int flags;

		protected Base() {
			this(0);
		}

		protected Base(int flags) {
			this.flags = flags;
		}

		@Override
		public boolean requestedFlying() {
			return (flags & 2) != 0;
		}

		@Override
		public abstract float flyingSpeed();

		@Override
		public abstract float walkingSpeed();

		public int flags() {
			return flags;
		}

		@Override
		public void setRequestedFlying(boolean value) {
			markMutated();
			flags = value ? flags | 2 : flags & ~2;
		}

		@Override
		public abstract void setFlyingSpeed(float flyingSpeed);

		@Override
		public abstract void setWalkingSpeed(float walkingSpeed);
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::flags,
				ByteBufStreamCodecs.FLOAT, Protocol47::flyingSpeed,
				ByteBufStreamCodecs.FLOAT, Protocol47::walkingSpeed,
				Protocol47::new
			);

		private float flyingSpeed = 0.05f, walkingSpeed = 0.1f;

		public Protocol47() {
			super();
		}

		public Protocol47(int flags, float flyingSpeed, float walkingSpeed) {
			super(flags);
			this.flyingSpeed = flyingSpeed;
			this.walkingSpeed = walkingSpeed;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 735;
		}

		public float flyingSpeed() {
			return flyingSpeed;
		}

		public void setFlyingSpeed(float flyingSpeed) {
			markMutated();
			this.flyingSpeed = flyingSpeed;
		}

		public float walkingSpeed() {
			return walkingSpeed;
		}

		public void setWalkingSpeed(float walkingSpeed) {
			markMutated();
			this.walkingSpeed = walkingSpeed;
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Protocol735::flags,
				Protocol735::new
			);

		public Protocol735() {
			super();
		}

		public Protocol735(int flags) {
			super(flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 735;
		}

		@Override
		public float flyingSpeed() {
			throw new UnsupportedOperationException("This packet does not support flying speed");
		}

		@Override
		public float walkingSpeed() {
			throw new UnsupportedOperationException("This packet does not support walking speed");
		}

		@Override
		public void setFlyingSpeed(float flyingSpeed) {
			markMutated();
			throw new UnsupportedOperationException("This packet does not support flying speed");
		}

		@Override
		public void setWalkingSpeed(float walkingSpeed) {
			markMutated();
			throw new UnsupportedOperationException("This packet does not support walking speed");
		}
	}
}
