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
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

public final class ExplosionWithEffectsWirePacket extends ExplosionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ExplosionWithEffectsWirePacket> STREAM_CODEC = StreamCodec.of(
		(output, packet) -> {
			output.writeDouble(packet.x).writeDouble(packet.y).writeDouble(packet.z).writeFloat(packet.power);
			VAR_INT_BLOCK_OFFSETS.encode(output, packet.affectedBlockOffsets);
			Motion.FLOAT_STREAM_CODEC.encode(output, packet.motion);
			output.writeBytes(packet.effects);
		}, input -> {
			ExplosionWithEffectsWirePacket packet = new ExplosionWithEffectsWirePacket();
			packet.x = input.readDouble();
			packet.y = input.readDouble();
			packet.z = input.readDouble();
			packet.power = input.readFloat();
			packet.affectedBlockOffsets = VAR_INT_BLOCK_OFFSETS.decode(input);
			packet.motion = Motion.FLOAT_STREAM_CODEC.decode(input);
			// Registry-dependent effect data is unchanged when editing only the motion.
			packet.effects = new byte[input.readableBytes()];
			input.readBytes(packet.effects);
			return packet;
		});

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 765 && protocolVersion < 768;
	}
}
