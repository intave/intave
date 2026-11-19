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

package de.jpx3.intave.packet.wire.block.change;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

public final class SingleBlockChangeLegacyPositionWirePacket extends SingleBlockChangeWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, SingleBlockChangeLegacyPositionWirePacket> STREAM_CODEC =
		BlockUpdate.LEGACY_STREAM_CODEC.beforeAndAfter(SingleBlockChangeLegacyPositionWirePacket::new, packet -> packet.update);

	public SingleBlockChangeLegacyPositionWirePacket() {
		super();
	}

	public SingleBlockChangeLegacyPositionWirePacket(BlockPosition position, int stateId) {
		super(position, stateId);
	}

	public SingleBlockChangeLegacyPositionWirePacket(BlockUpdate update) {
		super(update);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 477;
	}
}
