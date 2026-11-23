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
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EntityVelocityWirePacket {
	private EntityVelocityWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol773.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityVelocityPacket {
		protected int entityId;
		protected Motion motion;

		protected Base() {
			this(0, new Motion(0, 0, 0));
		}

		protected Base(int entityId, Motion motion) {
			this.entityId = entityId;
			this.motion = motion;
		}

		@Override
		public int entityId() {
			return this.entityId;
		}

		@Override
		public void setEntityId(int value) {
			markMutated();
			this.entityId = value;
		}

		@Override
		public Motion motion() {
			markMutated();
			return this.motion;
		}

		@Override
		public void setMotion(Motion value) {
			markMutated();
			this.motion = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::entityId,
				ByteBufStreamCodecs.SHORT, Protocol47::velocityX,
				ByteBufStreamCodecs.SHORT, Protocol47::velocityY,
				ByteBufStreamCodecs.SHORT, Protocol47::velocityZ,
				Protocol47::new
			);

		private short rawX, rawY, rawZ;

		public Protocol47() {
			super();
		}

		public Protocol47(int entityId, short rawX, short rawY, short rawZ) {
			super(entityId, new Motion(rawX / 8000.0, rawY / 8000.0, rawZ / 8000.0));
			this.rawX = rawX;
			this.rawY = rawY;
			this.rawZ = rawZ;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 773;
		}

		private static int quantize(double value, short original) {
			if (value == original / 8000.0) return original;
			return (int) (Math.max(-3.9, Math.min(3.9, value)) * 8000);
		}

		public short velocityX() {
			return (short) quantize(motion.motionX, rawX);
		}

		public short velocityY() {
			return (short) quantize(motion.motionY, rawY);
		}

		public short velocityZ() {
			return (short) quantize(motion.motionZ, rawZ);
		}
	}

	static final class Protocol773 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol773> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol773::entityId,
				Motion.LOW_PRECISION_STREAM_CODEC, Protocol773::motion,
				Protocol773::new
			);

		public Protocol773() {
			super();
		}

		public Protocol773(int entityId, Motion motion) {
			super(entityId, motion);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 773;
		}
	}
}
