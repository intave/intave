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

public class ExplosionOptionalMotionWirePacket extends ExplosionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ExplosionOptionalMotionWirePacket> STREAM_CODEC =
		StreamCodec.of((output, packet) -> packet.write(output, false), input -> {
			ExplosionOptionalMotionWirePacket packet = new ExplosionOptionalMotionWirePacket();
			packet.read(input, false);
			return packet;
		});

	protected int blockCount;

	public ExplosionOptionalMotionWirePacket() {
		motion = null;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768 && protocolVersion < 773;
	}

	@Override
	public void setMotion(Motion motion) {
		markMutated();
		this.motion = motion;
	}

	@Override
	public void setSilentDefaults(int protocol) {
		super.setSilentDefaults(protocol);
		blockCount = 0;
	}

	protected final void read(ByteBuf input, boolean hasRadius) {
		x = input.readDouble();
		y = input.readDouble();
		z = input.readDouble();
		if (hasRadius) {
			power = input.readFloat();
			blockCount = input.readInt();
		}
		motion = input.readBoolean() ? Motion.STREAM_CODEC.decode(input) : null;
		// Keep the version-specific particle, sound, weighted particles and playSound payload intact.
		effects = new byte[input.readableBytes()];
		input.readBytes(effects);
	}

	protected final void write(ByteBuf output, boolean hasRadius) {
		output.writeDouble(x).writeDouble(y).writeDouble(z);
		if (hasRadius) output.writeFloat(power).writeInt(blockCount);
		output.writeBoolean(motion != null);
		if (motion != null) Motion.STREAM_CODEC.encode(output, motion);
		output.writeBytes(effects);
	}
}
