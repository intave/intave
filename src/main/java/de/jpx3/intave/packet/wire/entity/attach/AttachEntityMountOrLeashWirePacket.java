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
import io.netty.buffer.ByteBuf;

public final class AttachEntityMountOrLeashWirePacket extends AttachEntityWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AttachEntityMountOrLeashWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, AttachEntityWirePacket::entityId,
			ByteBufStreamCodecs.INTEGER, AttachEntityWirePacket::vehicleId,
			ByteBufStreamCodecs.UNSIGNED_BYTE.beforeAndAfter( value -> value == 0, value -> value ? 0 : 1), AttachEntityMountOrLeashWirePacket::isMount,
			AttachEntityMountOrLeashWirePacket::new
		);

	private boolean mount;

	public AttachEntityMountOrLeashWirePacket() {
		super();
	}

	public AttachEntityMountOrLeashWirePacket(int entityId, int vehicleId, boolean mount) {
		super(entityId, vehicleId);
		this.mount = mount;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	@Override
	public boolean isMount() {
		return mount;
	}

	@Override
	public void setMount(boolean value) {
		mount = value;
	}
}
