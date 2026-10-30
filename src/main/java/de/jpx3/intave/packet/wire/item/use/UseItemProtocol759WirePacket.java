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
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class UseItemProtocol759WirePacket extends UseItemProtocol107WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UseItemProtocol759WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), UseItemProtocol107WirePacket::hand,
			ByteBufStreamCodecs.VARINT, UseItemProtocol759WirePacket::sequence,
			UseItemProtocol759WirePacket::new
		);

	protected int sequence;

	public UseItemProtocol759WirePacket() {
		this(PlayerHand.MAIN_HAND, 0);
	}

	public UseItemProtocol759WirePacket(PlayerHand hand, int sequence) {
		super(hand);
		this.sequence = sequence;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 759 && protocolVersion < 767;
	}

	@Override
	public OptionalInt sequenceNumber() {
		return OptionalInt.of(sequence);
	}

	@Override
	public void setSequenceNumber(int value) {
		sequence = value;
	}

	public int sequence() {
		return sequence;
	}
}
