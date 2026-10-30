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

package de.jpx3.intave.packet.wire.block.dig;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableBlockDigPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerDigAction;
import io.netty.buffer.ByteBuf;

public class BlockDigWirePacket implements MutableBlockDigPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockDigWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, BlockDigWirePacket::actionId,
			BlockPosition.WIRE_STREAM_CODEC, BlockDigWirePacket::blockPosition,
			ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), BlockDigWirePacket::direction,
			ByteBufStreamCodecs.VARINT, BlockDigWirePacket::sequence,
			BlockDigWirePacket::new
		);

	protected BlockPosition blockPosition;
	protected Direction direction;
	protected int actionId, sequence;

	public BlockDigWirePacket() {
		this(0, BlockPosition.ORIGIN, Direction.DOWN, 0);
	}

	public BlockDigWirePacket(
			int actionId, BlockPosition blockPosition,
			Direction direction, int sequence
	) {
		this.actionId = actionId;
		this.blockPosition = blockPosition;
		this.direction = direction;
		this.sequence = sequence;
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
	public Direction direction() {
		return this.direction;
	}

	@Override
	public void setDirection(Direction value) {
		this.direction = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("BLOCK_DIG".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 777;
	}

	@Override
	public PlayerDigAction action() {
		int ordinal = (actionId > 1) ? actionId - 1 : actionId;
		if (actionId == 1) return PlayerDigAction.CHANGE_DESTROY_DIRECTION;
		return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
	}

	@Override
	public void setAction(PlayerDigAction value) {
		if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
			actionId = 1;
		} else {
			actionId = value.ordinal() + ((value.ordinal() >= 1) ? 1 : 0);
		}
	}

	public int actionId() {
		return actionId;
	}

	public int sequence() {
		return sequence;
	}
}
