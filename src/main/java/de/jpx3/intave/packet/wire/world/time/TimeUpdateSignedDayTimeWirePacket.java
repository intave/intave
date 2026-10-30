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

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class TimeUpdateSignedDayTimeWirePacket extends TimeUpdateWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TimeUpdateSignedDayTimeWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								output.writeLong(packet.gameTime);
								// Legacy clients observe frozen zero as tick 1, encoded using the
								// -1 sentinel.
								long wireTime =
										!packet.ticking
												? (packet.dayTime == 0 ? -1 : -packet.dayTime)
												: packet.dayTime;
								output.writeLong(wireTime);
							},
							input -> {
								long gameTime = input.readLong();
								long wireTime = input.readLong();

								if (wireTime == Long.MIN_VALUE) {
									throw new IllegalArgumentException(
											"Legacy day time magnitude exceeds Long.MAX_VALUE");
								}
								long dayTime = Math.abs(wireTime);
								boolean ticking = wireTime >= 0;
								return new TimeUpdateSignedDayTimeWirePacket(
										gameTime, dayTime, ticking);
							});

	public TimeUpdateSignedDayTimeWirePacket() {
		super();
	}

	public TimeUpdateSignedDayTimeWirePacket(long gameTime, long dayTime, boolean ticking) {
		super(gameTime, dayTime, ticking);
	}

	@Override
	public void setDayTime(long value) {
		if (value < 0) {
			throw new IllegalArgumentException(
					"Legacy day time must be nonnegative; use setTicking to freeze it");
		}
		dayTime = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 768;
	}
}
