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

package de.jpx3.intave.packet.wire.block.multichange;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMultiBlockChangePacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MultiBlockChangeChunkWirePacket extends MutableWirePacket implements MutableMultiBlockChangePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MultiBlockChangeChunkWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {
				output.writeInt(packet.chunkX);
				output.writeInt(packet.chunkZ);
				BlockUpdate.chunkStreamCodec(packet.chunkX, packet.chunkZ).encode(output, packet.updates);
			},
			input -> {
				int chunkX = input.readInt();
				int chunkZ = input.readInt();
				return new MultiBlockChangeChunkWirePacket(chunkX, chunkZ, BlockUpdate.chunkStreamCodec(chunkX, chunkZ).decode(input));
			}
		);

	private int chunkX, chunkZ;
	private List<BlockUpdate> updates;

	public MultiBlockChangeChunkWirePacket() {
		this(0, 0, Collections.emptyList());
	}

	public MultiBlockChangeChunkWirePacket(int chunkX, int chunkZ, List<BlockUpdate> updates) {
		this.updates = checkedUpdates(chunkX, chunkZ, updates);
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 751;
	}

	public int chunkX() {
		return chunkX;
	}

	public int chunkZ() {
		return chunkZ;
	}

	@Override
	public List<BlockUpdate> blockUpdates() {
		return updates;
	}

	/** Replaces the batch, deriving its chunk from the first update. Empty batches retain the header. */
	@Override
	public void setBlockUpdates(List<BlockUpdate> updates) {
		markMutated();
		int x = updates.isEmpty() ? chunkX : updates.get(0).position().getX() >> 4;
		int z = updates.isEmpty() ? chunkZ : updates.get(0).position().getZ() >> 4;
		this.updates = checkedUpdates(x, z, updates);
		this.chunkX = x;
		this.chunkZ = z;
	}

	private static List<BlockUpdate> checkedUpdates(int chunkX, int chunkZ, List<BlockUpdate> updates) {
		List<BlockUpdate> copy = new ArrayList<>(updates);
		for (BlockUpdate update : copy) {
			BlockPosition position = update.position();
			if ((position.getX() & ~15) != (chunkX << 4) || (position.getZ() & ~15) != (chunkZ << 4)
				|| position.getY() < 0 || position.getY() > 255) {
				throw new IllegalArgumentException("Block updates must belong to the same chunk with Y in [0, 255]");
			}
		}
		return Collections.unmodifiableList(copy);
	}
}
