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

package de.jpx3.intave.packet.wire.item.use;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

public class UseItemWirePacket extends UseItemProtocol759WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UseItemWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), UseItemProtocol107WirePacket::hand,
			ByteBufStreamCodecs.VARINT, UseItemProtocol759WirePacket::sequence,
			Rotation.STREAM_CODEC, UseItemWirePacket::rotation,
			UseItemWirePacket::new
		);

	protected Rotation rotation;

	public UseItemWirePacket() {
		this(PlayerHand.MAIN_HAND, 0, new Rotation(0, 0));
	}

	public UseItemWirePacket(PlayerHand hand, int sequence, Rotation rotation) {
		super(hand, sequence);
		this.rotation = rotation;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 767;
	}

	public Rotation rotation() {
		return rotation;
	}
}
