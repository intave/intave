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

package de.jpx3.intave.packet.wire.window.horseopen;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class HorseWindowOpenProtocol768WirePacket extends HorseWindowOpenWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, HorseWindowOpenProtocol768WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, HorseWindowOpenWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, HorseWindowOpenWirePacket::count,
			ByteBufStreamCodecs.INTEGER, HorseWindowOpenWirePacket::entityId,
			HorseWindowOpenProtocol768WirePacket::new
		);

	public HorseWindowOpenProtocol768WirePacket() {
		super();
	}

	public HorseWindowOpenProtocol768WirePacket(int containerId, int count, int entityId) {
		super(containerId, count, entityId);
	}

	@Override
	public int slots() {
		return count * 3 + (1);
	}

	@Override
	public void setSlots(int value) {
		int offset = 1;
		if (((value < offset || (value - offset) % 3 != 0)))
			throw new IllegalArgumentException("Horse slots must contain complete columns");
		count = (value - offset) / 3;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768 && protocolVersion < 770;
	}
}
