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

package de.jpx3.intave.packet.wire.entity.attach;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableAttachEntityPacket;
import io.netty.buffer.ByteBuf;

public class AttachEntityWirePacket implements MutableAttachEntityPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AttachEntityWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, AttachEntityWirePacket::entityId,
			ByteBufStreamCodecs.INTEGER, AttachEntityWirePacket::vehicleId,
			AttachEntityWirePacket::new
		);

	protected int entityId;
	protected int vehicleId;

	public AttachEntityWirePacket() {
		this(0, -1);
	}

	public AttachEntityWirePacket(int entityId, int vehicleId) {
		this.entityId = entityId;
		this.vehicleId = vehicleId;
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
	public int vehicleId() {
		return this.vehicleId;
	}

	@Override
	public void setVehicleId(int value) {
		this.vehicleId = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ATTACH_ENTITY".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107;
	}

	@Override
	public boolean isMount() {
		return false;
	}

	@Override
	public void setMount(boolean value) {
		if (value) {
			throw new UnsupportedOperationException("Use MOUNT for passenger updates since 1.9");
		}
	}
}
