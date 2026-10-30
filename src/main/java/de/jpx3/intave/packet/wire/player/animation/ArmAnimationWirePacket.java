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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableArmAnimationPacket;
import de.jpx3.intave.share.PlayerHand;
import io.netty.buffer.ByteBuf;

public class ArmAnimationWirePacket implements MutableArmAnimationPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ArmAnimationWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), ArmAnimationWirePacket::hand,
			ArmAnimationWirePacket::new
		);

	protected PlayerHand hand;

	public ArmAnimationWirePacket() {
		this(PlayerHand.MAIN_HAND);
	}

	public ArmAnimationWirePacket(PlayerHand hand) {
		this.hand = hand;
	}

	@Override
	public PlayerHand hand() {
		return this.hand;
	}

	@Override
	public void setHand(PlayerHand value) {
		this.hand = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ARM_ANIMATION".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107;
	}
}
