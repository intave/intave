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

package de.jpx3.intave.packet.wire.connection.keepalive;

import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.wire.WirePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class KeepAliveWirePacketCodecsTest {
	@Test
	void keepAliveChangesFromVarIntToLongInBothDirections() {
		for (PacketDirection direction : PacketDirection.values()) {
			KeepAliveWirePacket legacy =
					roundTrip(
							direction,
							"KEEP_ALIVE",
							335,
							b -> b.writeBytes(new byte[] {(byte) 0xac, 2}));
			assertEquals(300, legacy.id());
			assertInstanceOf(KeepAliveVarIntIdWirePacket.class, legacy);
			KeepAliveWirePacket modern =
					roundTrip(direction, "KEEP_ALIVE", 340, b -> b.writeLong(0x123456789abcdefL));
			assertEquals(0x123456789abcdefL, modern.id());
			assertEquals(KeepAliveWirePacket.class, modern.getClass());
		}
	}

	@Test
	void failedEncodingRestoresTheOutputIndex() {
		ByteBuf output = Unpooled.buffer().writeByte(0x55);
		try {
			assertThrows(
					IllegalArgumentException.class,
					() ->
							WirePackets.encodePacket(
									SERVERBOUND,
									PLAY,
									"KEEP_ALIVE",
									340,
									new KeepAliveVarIntIdWirePacket(),
									output));
			assertEquals(1, output.writerIndex());
			assertEquals(0x55, output.getUnsignedByte(0));
		} finally {
			output.release();
		}
	}
}
