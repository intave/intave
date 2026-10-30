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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class BlockInteractionProtocol107WirePacket implements MutableBlockInteractionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockInteractionProtocol107WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			BlockPosition.LEGACY_STREAM_CODEC, BlockInteractionProtocol107WirePacket::blockPosition,
			ByteBufStreamCodecs.VARINT, BlockInteractionProtocol107WirePacket::directionId,
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), BlockInteractionProtocol107WirePacket::hand,
			RawVector3d.BYTE_HIT_POSITION_STREAM_CODEC, BlockInteractionProtocol107WirePacket::hitPosition,
			BlockInteractionProtocol107WirePacket::new
		);

	protected BlockPosition blockPosition;
	protected int directionId;
	protected PlayerHand hand;
	protected RawVector3d hitPosition;

	public BlockInteractionProtocol107WirePacket() {
		this(BlockPosition.ORIGIN, 0, PlayerHand.MAIN_HAND, RawVector3d.zero());
	}

	public BlockInteractionProtocol107WirePacket(
			BlockPosition blockPosition,
			int directionId,
			PlayerHand hand,
			RawVector3d hitPosition) {
		this.blockPosition = blockPosition;
		this.directionId = directionId;
		this.hand = hand;
		this.hitPosition = hitPosition;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 315;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("USE_ITEM_ON".equals(name));
	}

	@Override
	public BlockPosition blockPosition() {
		return this.blockPosition;
	}

	@Override
	public void setBlockPosition(BlockPosition value) {
		this.blockPosition = value;
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
	public RawVector3d hitPosition() {
		return this.hitPosition;
	}

	@Override
	public void setHitPosition(RawVector3d value) {
		this.hitPosition = value;
	}

	@Override
	public boolean isDirectItemUseRequest() {
		return false;
	}

	@Override
	public Direction direction() {
		return directionId >= 0 && directionId < 6 ? Direction.values()[directionId] : null;
	}

	@Override
	public void setDirection(Direction value) {
		directionId = value == null ? 255 : value.ordinal();
	}

	@Override
	public OptionalInt sequenceNumber() {
		return OptionalInt.empty();
	}

	@Override
	public void setSequenceNumber(int value) {
		throw new IllegalStateException("No sequence before 1.19");
	}

	public int directionId() {
		return directionId;
	}
}
