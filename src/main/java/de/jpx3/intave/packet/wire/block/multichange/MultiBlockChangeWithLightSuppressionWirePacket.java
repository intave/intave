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
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.List;

public final class MultiBlockChangeWithLightSuppressionWirePacket extends MultiBlockChangeWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MultiBlockChangeWithLightSuppressionWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {
				BlockPosition.SECTION_STREAM_CODEC.encode(output, packet.sectionPosition);
				output.writeBoolean(packet.suppressLightUpdates);
				BlockUpdate.sectionStreamCodec(packet.sectionPosition).encode(output, packet.updates);
			},
			input -> {
				BlockPosition section = BlockPosition.SECTION_STREAM_CODEC.decode(input);
				boolean suppressLightUpdates = input.readBoolean();
				return new MultiBlockChangeWithLightSuppressionWirePacket(section, suppressLightUpdates, BlockUpdate.sectionStreamCodec(section).decode(input));
			}
		);

	private boolean suppressLightUpdates;

	public MultiBlockChangeWithLightSuppressionWirePacket() {
		this(BlockPosition.ORIGIN, false, Collections.emptyList());
	}

	public MultiBlockChangeWithLightSuppressionWirePacket(
		BlockPosition sectionPosition, boolean suppressLightUpdates, List<BlockUpdate> updates
	) {
		super(sectionPosition, updates);
		this.suppressLightUpdates = suppressLightUpdates;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 751 && protocolVersion < 763;
	}

	public boolean suppressLightUpdates() {
		return suppressLightUpdates;
	}

	public void setSuppressLightUpdates(boolean suppressLightUpdates) {
		markMutated();
		this.suppressLightUpdates = suppressLightUpdates;
	}
}
