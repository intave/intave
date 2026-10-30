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

public final class HorseWindowOpenProtocol477WirePacket extends HorseWindowOpenWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, HorseWindowOpenProtocol477WirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, HorseWindowOpenWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, HorseWindowOpenWirePacket::count,
			ByteBufStreamCodecs.INTEGER, HorseWindowOpenWirePacket::entityId,
			HorseWindowOpenProtocol477WirePacket::new
		);

	public HorseWindowOpenProtocol477WirePacket() {
		super();
	}

	public HorseWindowOpenProtocol477WirePacket(int containerId, int count, int entityId) {
		super(containerId, count, entityId);
	}

	@Override
	public int slots() {
		return count;
	}

	@Override
	public void setSlots(int value) {
		int offset = 1;

		count = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 477 && protocolVersion < 767;
	}
}
