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
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

public final class EntityVelocityShortVectorWirePacket extends EntityVelocityWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityVelocityShortVectorWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityVelocityWirePacket::entityId,
			ByteBufStreamCodecs.SHORT, EntityVelocityShortVectorWirePacket::velocityX,
			ByteBufStreamCodecs.SHORT, EntityVelocityShortVectorWirePacket::velocityY,
			ByteBufStreamCodecs.SHORT, EntityVelocityShortVectorWirePacket::velocityZ,
			EntityVelocityShortVectorWirePacket::new
		);

	private short rawX, rawY, rawZ;

	public EntityVelocityShortVectorWirePacket() {
		super();
	}

	public EntityVelocityShortVectorWirePacket(int entityId, short rawX, short rawY, short rawZ) {
		super(entityId, new Motion(rawX / 8000.0, rawY / 8000.0, rawZ / 8000.0));
		this.rawX = rawX;
		this.rawY = rawY;
		this.rawZ = rawZ;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 773;
	}

	protected static int quantize(double value, short original) {
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
