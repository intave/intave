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

package de.jpx3.intave.packet.wire.player.bed;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableBedUsePacket;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;

public class BedUseWirePacket implements MutableBedUsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, BedUseWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, BedUseWirePacket::entityId,
			BlockPosition.LEGACY_STREAM_CODEC, BedUseWirePacket::blockPosition,
			BedUseWirePacket::new
		);

	protected int entityId;
	protected BlockPosition blockPosition;

	public BedUseWirePacket() {
		this(0, BlockPosition.ORIGIN);
	}

	public BedUseWirePacket(int entityId, BlockPosition blockPosition) {
		this.entityId = entityId;
		this.blockPosition = blockPosition;
	}

	@Override
	public int entityId() {
		return this.entityId;
	}

	@Override
	public void setEntityId(int value) {
		this.entityId = value;
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
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("BED".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 477;
	}
}
