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

package de.jpx3.intave.packet.wire.block.interaction;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class BlockInteractionProtocol759WirePacket extends BlockInteractionProtocol477WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockInteractionProtocol759WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), BlockInteractionProtocol107WirePacket::hand,
			BlockPosition.WIRE_STREAM_CODEC, BlockInteractionProtocol107WirePacket::blockPosition,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol107WirePacket::directionId,
			RawVector3d.FLOAT_STREAM_CODEC, BlockInteractionProtocol107WirePacket::hitPosition,
			ByteBufStreamCodecs.BOOLEAN, BlockInteractionProtocol477WirePacket::inside,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol759WirePacket::sequence,
			BlockInteractionProtocol759WirePacket::new
		);

	protected int sequence;

	public BlockInteractionProtocol759WirePacket() {
		this(PlayerHand.MAIN_HAND, BlockPosition.ORIGIN, 0, new RawVector3d(0, 0, 0), false, 0);
	}

	public BlockInteractionProtocol759WirePacket(
			PlayerHand hand,
			BlockPosition blockPosition,
			int directionId,
			RawVector3d hitPosition,
			boolean inside,
			int sequence) {
		super(hand, blockPosition, directionId, hitPosition, inside);
		this.sequence = sequence;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 759 && protocolVersion < 768;
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
