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

public class BlockInteractionProtocol477WirePacket extends BlockInteractionProtocol315WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockInteractionProtocol477WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), BlockInteractionProtocol107WirePacket::hand,
			BlockPosition.WIRE_STREAM_CODEC, BlockInteractionProtocol107WirePacket::blockPosition,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol107WirePacket::directionId,
			RawVector3d.FLOAT_STREAM_CODEC, BlockInteractionProtocol107WirePacket::hitPosition,
			ByteBufStreamCodecs.BOOLEAN, BlockInteractionProtocol477WirePacket::inside,
			BlockInteractionProtocol477WirePacket::new
		);

	protected boolean inside;

	public BlockInteractionProtocol477WirePacket() {
		this(PlayerHand.MAIN_HAND, BlockPosition.ORIGIN, 0, new RawVector3d(0, 0, 0), false);
	}

	public BlockInteractionProtocol477WirePacket(
			PlayerHand hand,
			BlockPosition blockPosition,
			int directionId,
			RawVector3d hitPosition,
			boolean inside) {
		super(blockPosition, directionId, hand, hitPosition);
		this.inside = inside;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 477 && protocolVersion < 759;
	}

	public boolean inside() {
		return inside;
	}
}
