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

package de.jpx3.intave.packet.wire.scoreboard.display;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class ScoreboardDisplayByteSlotWirePacket extends ScoreboardDisplayWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ScoreboardDisplayByteSlotWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								output.writeByte(packet.slot);
								ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName == null ? "" : packet.objectiveName);
							},
							input -> {
								int slot = input.readUnsignedByte();
								String name = ByteBufStreamCodecs.VARINT_STRING.decode(input);
								String objectiveName = name.isEmpty() ? null : name;
								return new ScoreboardDisplayByteSlotWirePacket(slot, objectiveName);
							});

	public ScoreboardDisplayByteSlotWirePacket() {
		super();
	}

	public ScoreboardDisplayByteSlotWirePacket(int slot, String objectiveName) {
		super(slot, objectiveName);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 764;
	}
}
