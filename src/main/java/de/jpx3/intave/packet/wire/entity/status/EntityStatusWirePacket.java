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

package de.jpx3.intave.packet.wire.entity.status;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableEntityStatusPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

public final class EntityStatusWirePacket extends MutableWirePacket implements MutableEntityStatusPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityStatusWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, EntityStatusWirePacket::entityId,
			ByteBufStreamCodecs.BYTE, EntityStatusWirePacket::status,
			EntityStatusWirePacket::new
		);

	private int entityId;
	private Byte status;

	public EntityStatusWirePacket() {
		this(0, (byte) 0);
	}

	public EntityStatusWirePacket(int entityId, Byte status) {
		this.entityId = entityId;
		this.status = status;
	}

	@Override
	public int entityId() {
		return this.entityId;
	}

	@Override
	public void setEntityId(int value) {
		markMutated();
		this.entityId = value;
	}

	@Override
	public Byte status() {
		return this.status;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}

	@Override
	public void setStatus(byte status) {
		markMutated();
		this.status = status;
	}
}
