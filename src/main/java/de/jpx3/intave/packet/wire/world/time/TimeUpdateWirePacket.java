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

package de.jpx3.intave.packet.wire.world.time;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableTimeUpdatePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TimeUpdateWirePacket {
	private TimeUpdateWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableTimeUpdatePacket {
		protected long gameTime;
		protected long dayTime;
		protected boolean ticking;

		protected Base() {
			this(0, 0, true);
		}

		protected Base(long gameTime, long dayTime, boolean ticking) {
			this.gameTime = gameTime;
			this.dayTime = dayTime;
			this.ticking = ticking;
		}

		@Override
		public long gameTime() {
			return this.gameTime;
		}

		@Override
		public void setGameTime(long value) {
			markMutated();
			this.gameTime = value;
		}

		@Override
		public long dayTime() {
			return dayTime;
		}

		@Override
		public abstract void setDayTime(long value);

		@Override
		public boolean ticking() {
			return ticking;
		}

		@Override
		public void setTicking(boolean value) {
			markMutated();
			ticking = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.of((output, packet) -> {
			output.writeLong(packet.gameTime);
			// Legacy clients observe frozen zero as tick 1, encoded using the
			// -1 sentinel.
			long wireTime = !packet.ticking ? (packet.dayTime == 0 ? -1 : -packet.dayTime) : packet.dayTime;
			output.writeLong(wireTime);
		}, input -> {
			long gameTime = input.readLong();
			long wireTime = input.readLong();
			if (wireTime == Long.MIN_VALUE) {
				throw new IllegalArgumentException("Legacy day time magnitude exceeds Long.MAX_VALUE");
			}
			long dayTime = Math.abs(wireTime);
			boolean ticking = wireTime >= 0;
			return new Protocol47(gameTime, dayTime, ticking);
		});

		public Protocol47() {
			super();
		}

		public Protocol47(long gameTime, long dayTime, boolean ticking) {
			super(gameTime, dayTime, ticking);
		}

		@Override
		public void setDayTime(long value) {
			markMutated();
			if (value < 0) {
				throw new IllegalArgumentException("Legacy day time must be nonnegative; use setTicking to freeze it");
			}
			dayTime = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.LONG, Protocol768::gameTime,
				ByteBufStreamCodecs.LONG, Protocol768::dayTime,
				ByteBufStreamCodecs.BOOLEAN, Protocol768::ticking,
				Protocol768::new
			);

		public Protocol768() {
			super();
		}

		public Protocol768(long gameTime, long dayTime, boolean ticking) {
			super(gameTime, dayTime, ticking);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768 && protocolVersion < 775;
		}

		@Override
		public void setDayTime(long value) {
			markMutated();
			dayTime = value;
		}
	}
}
