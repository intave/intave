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

public class BlockInteractionWirePacket extends BlockInteractionProtocol759WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockInteractionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), BlockInteractionProtocol107WirePacket::hand,
			BlockPosition.WIRE_STREAM_CODEC, BlockInteractionProtocol107WirePacket::blockPosition,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol107WirePacket::directionId,
			RawVector3d.FLOAT_STREAM_CODEC, BlockInteractionProtocol107WirePacket::hitPosition,
			ByteBufStreamCodecs.BOOLEAN, BlockInteractionProtocol477WirePacket::inside,
			ByteBufStreamCodecs.BOOLEAN, BlockInteractionWirePacket::border,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol759WirePacket::sequence,
			BlockInteractionWirePacket::new
		);

	protected boolean border;

	public BlockInteractionWirePacket() {
		this(
			PlayerHand.MAIN_HAND,
			BlockPosition.ORIGIN,
			0,
			new RawVector3d(0, 0, 0),
			false, false,
			0
		);
	}

	public BlockInteractionWirePacket(
		PlayerHand hand,
		BlockPosition blockPosition,
		int directionId,
		RawVector3d hitPosition,
		boolean inside, boolean border,
		int sequence
	) {
		super(hand, blockPosition, directionId, hitPosition, inside, sequence);
		this.border = border;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}

	public boolean border() {
		return border;
	}
}
