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

package de.jpx3.intave.packet.wire.ability.outbound;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableAbilityOutPacket;
import io.netty.buffer.ByteBuf;

public class AbilityOutWirePacket implements MutableAbilityOutPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AbilityOutWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, AbilityOutWirePacket::flags,
			ByteBufStreamCodecs.FLOAT, AbilityOutWirePacket::flyingSpeed,
			ByteBufStreamCodecs.FLOAT, AbilityOutWirePacket::walkingSpeed,
			AbilityOutWirePacket::new
		);

	protected float flyingSpeed;
	protected float walkingSpeed;

	private int flags;

	public AbilityOutWirePacket() {
		this(0, 0.05f, 0.1f);
	}

	public AbilityOutWirePacket(int flags, float flyingSpeed, float walkingSpeed) {
		this.flags = flags;
		this.flyingSpeed = flyingSpeed;
		this.walkingSpeed = walkingSpeed;
	}

	@Override
	public float flyingSpeed() {
		return this.flyingSpeed;
	}

	@Override
	public void setFlyingSpeed(float value) {
		this.flyingSpeed = value;
	}

	@Override
	public float walkingSpeed() {
		return this.walkingSpeed;
	}

	@Override
	public void setWalkingSpeed(float value) {
		this.walkingSpeed = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ABILITIES".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}

	@Override
	public boolean flying() {
		return (flags & 2) != 0;
	}

	@Override
	public boolean flyingAllowed() {
		return (flags & 4) != 0;
	}

	@Override
	public void setFlying(boolean value) {
		flags = value ? flags | 2 : flags & ~2;
	}

	@Override
	public void setFlyingAllowed(boolean value) {
		flags = value ? flags | 4 : flags & ~4;
	}

	public int flags() {
		return flags;
	}
}
