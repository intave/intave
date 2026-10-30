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

package de.jpx3.intave.packet.wire.block.acknowledge;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableBlockChangedAckPacket;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class BlockChangedAckWirePacket implements MutableBlockChangedAckPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockChangedAckWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, BlockChangedAckWirePacket::sequence,
			BlockChangedAckWirePacket::new
		);

	private int sequence;

	public BlockChangedAckWirePacket() {
		this(0);
	}

	public BlockChangedAckWirePacket(int sequence) {
		this.sequence = sequence;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("BLOCK_CHANGED_ACK".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 759;
	}

	@Override
	public OptionalInt sequenceNumber() {
		return OptionalInt.of(sequence);
	}

	@Override
	public void setSequenceNumber(int value) {
		sequence = value;
	}

	public int sequence() {
		return sequence;
	}
}
