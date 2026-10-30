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

package de.jpx3.intave.packet.wire.player.input;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerInputWirePacketsTest {
	@Test
	void consumesThePacketIdBeforeDecodingPlayerInputFlags() {
		int protocolVersion = 776;
		int packetId = PacketIdRegistries.serverbound().idOf(PLAY, "PLAYER_INPUT", protocolVersion);
		Input expected = new Input(false, false, true, false, true, false, true);
		ByteBuf packet = Unpooled.buffer();
		try {
			ByteBufStreamCodecs.VARINT.encode(packet, packetId);
			PlayerInputWirePacket.STREAM_CODEC.encode(packet, new PlayerInputWirePacket(expected));

			PlayerInputWirePacket decoded = WirePackets.decodePacket(PLAY, protocolVersion, packet);

			assertEquals(expected, decoded.input());
			assertEquals(0, packet.readerIndex());
		} finally {
			packet.release();
		}
	}
}
