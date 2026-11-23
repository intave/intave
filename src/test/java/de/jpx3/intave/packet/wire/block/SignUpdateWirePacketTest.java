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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.packet.contract.immutable.SignUpdatePacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Client.UPDATE_SIGN;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SignUpdateWirePacketTest {
	@Test
	void preservesPositionsTextAndSidesAcrossEverySupportedProtocol() {
		for (int protocol : registry(SERVERBOUND).supportedProtocolVersions()) {
			for (boolean front : new boolean[]{true, false}) {
				List<String> lines = protocol < 107
					? Arrays.asList("{\"text\":\"hello\"}", "\"\"", "{\"text\":\"ä\"}", "{\"text\":\"\\\\\"}")
					: Arrays.asList("hello", "", "ä", "\\");
				int x = -12345, y = protocol < 477 ? 200 : -48, z = 23456;
				SignUpdatePacket packet = roundTrip(SERVERBOUND, UPDATE_SIGN, protocol, out -> {
					long position = protocol < 477
						? ((x & 0x3ffffffL) << 38) | ((y & 0xfffL) << 26) | (z & 0x3ffffffL)
						: ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
					out.writeLong(position);
					if (protocol >= 763 && protocol < 777) out.writeBoolean(front);
					for (String line : lines) VARINT_STRING.encode(out, line);
					if (protocol >= 777) VARINT.encode(out, front ? 1 : 0);
				});
				assertEquals(new BlockPosition(x, y, z), packet.blockPosition(), "protocol " + protocol);
				assertEquals(lines, packet.lines());
				assertEquals(protocol < 763 || front, packet.frontText());
				assertThrows(UnsupportedOperationException.class, () -> packet.lines().set(0, "changed"));
			}
		}
	}

	@Test
	void rejectsMissingLines() {
		for (int protocol : new int[]{47, 477, 763, 777}) {
			ByteBuf frame = Unpooled.buffer();
			try {
				VARINT.encode(frame, registry(SERVERBOUND).idOf(PLAY, UPDATE_SIGN, protocol));
				frame.writeLong(0);
				if (protocol >= 763 && protocol < 777) frame.writeBoolean(true);
				for (int i = 0; i < 3; i++) VARINT_STRING.encode(frame, "");
				assertThrows(RuntimeException.class, () -> WirePackets.decodePacket(SERVERBOUND, PLAY, protocol, frame));
			} finally {
				frame.release();
			}
		}
	}
}
