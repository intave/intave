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
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MultiBlockChangeWirePacket extends AbstractMutable implements MutableMultiBlockChangePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MultiBlockChangeWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {
				BlockPosition.SECTION_STREAM_CODEC.encode(output, packet.sectionPosition);
				BlockUpdate.sectionStreamCodec(packet.sectionPosition).encode(output, packet.updates);
			},
			input -> {
				BlockPosition section = BlockPosition.SECTION_STREAM_CODEC.decode(input);
				return new MultiBlockChangeWirePacket(section, BlockUpdate.sectionStreamCodec(section).decode(input));
			}
		);

	protected BlockPosition sectionPosition;
	protected List<BlockUpdate> updates;

	public MultiBlockChangeWirePacket() {
		this(BlockPosition.ORIGIN, Collections.emptyList());
	}

	public MultiBlockChangeWirePacket(BlockPosition sectionPosition, List<BlockUpdate> updates) {
		this.updates = checkedUpdates(sectionPosition, updates);
		this.sectionPosition = sectionPosition;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 763;
	}

	public BlockPosition sectionPosition() {
		return sectionPosition;
	}

	@Override
	public List<BlockUpdate> blockUpdates() {
		return updates;
	}

	/** Replaces the batch, deriving its section from the first update. Empty batches retain the header. */
	@Override
	public void setBlockUpdates(List<BlockUpdate> updates) {
		markMutated();
		BlockPosition section = updates.isEmpty() ? sectionPosition : sectionOf(updates.get(0).position());
		this.updates = checkedUpdates(section, updates);
		this.sectionPosition = section;
	}

	private static BlockPosition sectionOf(BlockPosition position) {
		return new BlockPosition(position.getX() >> 4, position.getY() >> 4, position.getZ() >> 4);
	}

	private static List<BlockUpdate> checkedUpdates(BlockPosition section, List<BlockUpdate> updates) {
		Objects.requireNonNull(section, "sectionPosition");
		if (section.getX() < -2097152 || section.getX() > 2097151
			|| section.getY() < -524288 || section.getY() > 524287
			|| section.getZ() < -2097152 || section.getZ() > 2097151) {
			throw new IllegalArgumentException("Section position is outside the packed wire range: " + section);
		}
		List<BlockUpdate> copy = new ArrayList<>(updates);
		for (BlockUpdate update : copy) {
			if (!section.equals(sectionOf(update.position()))) {
				throw new IllegalArgumentException("All block updates must belong to section " + section);
			}
		}
		return Collections.unmodifiableList(copy);
	}
}
