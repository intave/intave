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

package de.jpx3.intave.packet.wire.world.explosion;

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class ExplosionWithRadiusWirePacket extends ExplosionOptionalMotionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ExplosionWithRadiusWirePacket> STREAM_CODEC =
		StreamCodec.of((output, packet) -> packet.write(output, true), input -> {
			ExplosionWithRadiusWirePacket packet = new ExplosionWithRadiusWirePacket();
			packet.read(input, true);
			return packet;
		});

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 773;
	}
}
