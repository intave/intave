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

package de.jpx3.intave.packet.wire.ability.inbound;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class AbilityInWithSpeedsWirePacket extends AbilityInWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AbilityInWithSpeedsWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, AbilityInWirePacket::flags,
			ByteBufStreamCodecs.FLOAT, AbilityInWithSpeedsWirePacket::flyingSpeed,
			ByteBufStreamCodecs.FLOAT, AbilityInWithSpeedsWirePacket::walkingSpeed,
			AbilityInWithSpeedsWirePacket::new
		);

	private float flyingSpeed = 0.05f, walkingSpeed = 0.1f;

	public AbilityInWithSpeedsWirePacket() {
		super();
	}

	public AbilityInWithSpeedsWirePacket(int flags, float flyingSpeed, float walkingSpeed) {
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
		this.flyingSpeed = flyingSpeed;
	}

	public float walkingSpeed() {
		return walkingSpeed;
	}

	public void setWalkingSpeed(float walkingSpeed) {
		this.walkingSpeed = walkingSpeed;
	}
}
