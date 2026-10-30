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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class UseItemProtocol107WirePacket implements MutableBlockInteractionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UseItemProtocol107WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), UseItemProtocol107WirePacket::hand,
			UseItemProtocol107WirePacket::new
		);

	protected PlayerHand hand;

	public UseItemProtocol107WirePacket() {
		this(PlayerHand.MAIN_HAND);
	}

	public UseItemProtocol107WirePacket(PlayerHand hand) {
		this.hand = hand;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 759;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("USE_ITEM".equals(name));
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
	public boolean isDirectItemUseRequest() {
		return true;
	}

	@Override
	public BlockPosition blockPosition() {
		return null;
	}

	@Override
	public void setBlockPosition(BlockPosition value) {
		if (value != null) throw new IllegalArgumentException("Item use has no block position");
	}

	@Override
	public Direction direction() {
		return null;
	}

	@Override
	public void setDirection(Direction value) {
		if (value != null) throw new IllegalArgumentException("Item use has no face");
	}

	@Override
	public RawVector3d hitPosition() {
		return null;
	}

	@Override
	public void setHitPosition(RawVector3d value) {
		if (value != null) throw new IllegalArgumentException("Item use has no hit position");
	}

	@Override
	public OptionalInt sequenceNumber() {
		return OptionalInt.empty();
	}

	@Override
	public void setSequenceNumber(int value) {
		throw new IllegalStateException("No sequence before 1.19");
	}
}
