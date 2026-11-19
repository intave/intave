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
import de.jpx3.intave.packet.contract.mutable.MutableSingleBlockChangePacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SingleBlockChangeWirePacket extends MutableWirePacket implements MutableSingleBlockChangePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, SingleBlockChangeWirePacket> STREAM_CODEC =
		BlockUpdate.STREAM_CODEC.beforeAndAfter(SingleBlockChangeWirePacket::new, packet -> packet.update);

	protected BlockUpdate update;

	public SingleBlockChangeWirePacket() {
		this(BlockPosition.ORIGIN, 0);
	}

	public SingleBlockChangeWirePacket(BlockPosition position, int stateId) {
		this(new BlockUpdate(position, stateId));
	}

	public SingleBlockChangeWirePacket(BlockUpdate update) {
		this.update = checkedUpdate(update);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 477;
	}

	@Override
	public BlockPosition blockPosition() {
		return update.position();
	}

	public int stateId() {
		return update.stateId();
	}

	@Override
	public List<BlockUpdate> blockUpdates() {
		return Collections.singletonList(update);
	}

	@Override
	public void setBlockPosition(BlockPosition position) {
		markMutated();
		update = checkedUpdate(new BlockUpdate(position, update.stateId()));
	}

	public void setStateId(int stateId) {
		markMutated();
		update = new BlockUpdate(update.position(), stateId);
	}

	@Override
	public void setBlockUpdates(List<BlockUpdate> updates) {
		markMutated();
		if (updates.size() != 1) throw new IllegalArgumentException("Expected exactly one block update");
		update = checkedUpdate(updates.get(0));
	}

	private static BlockUpdate checkedUpdate(BlockUpdate update) {
		BlockPosition position = Objects.requireNonNull(update, "update").position();
		if (position.getX() < -33554432 || position.getX() > 33554431
			|| position.getY() < -2048 || position.getY() > 2047
			|| position.getZ() < -33554432 || position.getZ() > 33554431) {
			throw new IllegalArgumentException("Block position is outside the packed wire range: " + position);
		}
		return update;
	}
}
