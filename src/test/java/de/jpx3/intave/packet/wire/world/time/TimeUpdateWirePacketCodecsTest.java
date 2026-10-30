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

package de.jpx3.intave.packet.wire.world.time;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.wire.WirePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class TimeUpdateWirePacketCodecsTest {
	@Test
	void timeUpdatesStopBeforeRegistryBackedClocks() {
		TimeUpdateWirePacket legacy =
				roundTrip(CLIENTBOUND, "UPDATE_TIME", 767, b -> b.writeLong(42).writeLong(-6000));
		assertEquals(6000L, legacy.dayTime());
		assertFalse(legacy.ticking());
		TimeUpdateWirePacket modern =
				roundTrip(
						CLIENTBOUND,
						"UPDATE_TIME",
						774,
						b -> b.writeLong(42).writeLong(6000).writeBoolean(false));
		assertEquals(6000L, modern.dayTime());
		assertFalse(modern.ticking());
	}

	@Test
	void timeFieldsCanBeMutatedWithoutRecordingTypes() {
		for (int protocol : new int[] {767, 768}) {
			TimeUpdateWirePacket packet =
					WirePackets.createPlayPacket(CLIENTBOUND, "UPDATE_TIME", protocol);
			packet.setGameTime(42);
			packet.setDayTime(6000);
			packet.setTicking(false);
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(
						CLIENTBOUND, PLAY, "UPDATE_TIME", protocol, packet, output);
				ByteBufStreamCodecs.VARINT.decode(output);
				assertEquals(42, output.readLong());
				assertEquals(protocol < 768 ? -6000 : 6000, output.readLong());
				if (protocol >= 768) assertFalse(output.readBoolean());
				assertFalse(output.isReadable());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void legacyTimeRetainsTheFrozenZeroSentinel() {
		TimeUpdateWirePacket packet = new TimeUpdateSignedDayTimeWirePacket();
		packet.setDayTime(0);
		packet.setTicking(false);
		ByteBuf output = Unpooled.buffer();
		try {
			WirePackets.encodePacket(CLIENTBOUND, PLAY, "UPDATE_TIME", 767, packet, output);
			ByteBuf payload = output.duplicate();
			ByteBufStreamCodecs.VARINT.decode(payload);
			payload.readLong();
			assertEquals(-1L, payload.readLong());
			TimeUpdateWirePacket decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, 767, output);
			assertEquals(1L, decoded.dayTime());
			assertFalse(decoded.ticking());
			assertThrows(IllegalArgumentException.class, () -> packet.setDayTime(-1));
		} finally {
			output.release();
		}
	}
}
