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

public class BlockInteractionProtocol315WirePacket extends BlockInteractionProtocol107WirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockInteractionProtocol315WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			BlockPosition.LEGACY_STREAM_CODEC, BlockInteractionProtocol107WirePacket::blockPosition,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol107WirePacket::directionId,
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), BlockInteractionProtocol107WirePacket::hand,
			RawVector3d.FLOAT_STREAM_CODEC, BlockInteractionProtocol107WirePacket::hitPosition,
			BlockInteractionProtocol315WirePacket::new
		);

	public BlockInteractionProtocol315WirePacket() {
		this(BlockPosition.ORIGIN, 0, PlayerHand.MAIN_HAND, new RawVector3d(0, 0, 0));
	}

	public BlockInteractionProtocol315WirePacket(
			BlockPosition blockPosition,
			int directionId,
			PlayerHand hand,
			RawVector3d hitPosition) {
		super(blockPosition, directionId, hand, hitPosition);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 315 && protocolVersion < 477;
	}
}
