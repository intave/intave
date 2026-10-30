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

package de.jpx3.intave.packet.wire.player.animation;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.PlayerHand;
import io.netty.buffer.ByteBuf;

public final class ArmAnimationWithoutHandWirePacket extends ArmAnimationWirePacket {
	private final static ArmAnimationWithoutHandWirePacket INSTANCE = new ArmAnimationWithoutHandWirePacket();

	public static final StreamCodec<ByteBuf, ByteBuf, ArmAnimationWithoutHandWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {},
			input -> INSTANCE
		);

	public ArmAnimationWithoutHandWirePacket() {
		super(null);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	@Override
	public void setHand(PlayerHand hand) {
		throw new UnsupportedOperationException("This packet does not support a hand");
	}
}
