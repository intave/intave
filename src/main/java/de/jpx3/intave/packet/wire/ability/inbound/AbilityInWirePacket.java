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
import de.jpx3.intave.packet.contract.mutable.MutableAbilityInPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

public class AbilityInWirePacket extends MutableWirePacket implements MutableAbilityInPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AbilityInWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, AbilityInWirePacket::flags,
			AbilityInWirePacket::new
		);

	protected int flags;

	public AbilityInWirePacket() {
		this(0);
	}

	public AbilityInWirePacket(int flags) {
		this.flags = flags;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 735;
	}

	@Override
	public boolean requestedFlying() {
		return (flags & 2) != 0;
	}

	@Override
	public float flyingSpeed() {
		throw new UnsupportedOperationException("This packet does not support flying speed");
	}

	@Override
	public float walkingSpeed() {
		throw new UnsupportedOperationException("This packet does not support walking speed");
	}

	public int flags() {
		return flags;
	}

	@Override
	public void setRequestedFlying(boolean value) {
		markMutated();
		flags = value ? flags | 2 : flags & ~2;
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
