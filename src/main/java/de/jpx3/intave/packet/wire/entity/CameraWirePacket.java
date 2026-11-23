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

package de.jpx3.intave.packet.wire.entity;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.immutable.CameraPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

public final class CameraWirePacket extends AbstractMutable implements CameraPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, CameraWirePacket> STREAM_CODEC =
		StreamCodec.compound(ByteBufStreamCodecs.VARINT, CameraWirePacket::entityId, CameraWirePacket::new);

	private final int entityId;

	public CameraWirePacket() {
		this(0);
	}

	public CameraWirePacket(int entityId) {
		this.entityId = entityId;
	}

	@Override
	public int entityId() {
		return entityId;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}
