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
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerDigAction;
import io.netty.buffer.ByteBuf;

public final class BlockDigLegacyPositionWirePacket extends BlockDigWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockDigLegacyPositionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, BlockDigWirePacket::actionId,
			BlockPosition.LEGACY_STREAM_CODEC, BlockDigWirePacket::blockPosition,
			ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), BlockDigWirePacket::direction,
			BlockDigLegacyPositionWirePacket::new
		);

	public BlockDigLegacyPositionWirePacket() {
		super();
	}

	public BlockDigLegacyPositionWirePacket(int actionId, BlockPosition blockPosition, Direction direction) {
		super(actionId, blockPosition, direction, 0);
	}

	@Override
	public PlayerDigAction action() {
		int ordinal = actionId;

		return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
	}

	@Override
	public void setAction(PlayerDigAction value) {
		if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
			throw new IllegalArgumentException("Direction changes require 26.3");
		} else {
			actionId = value.ordinal();
		}
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 477;
	}
}
