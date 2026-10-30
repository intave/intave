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

package de.jpx3.intave.packet.wire.world.border;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.VarLongCodec;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class WorldBorderWirePacketCodecsTest {
	@Test
	void worldBorderInitializationPreservesWarningsAndLerpTime() {
		for (int protocol : new int[] {47, 755, 777}) {
			WorldBorderWirePacket border =
					roundTrip(
							CLIENTBOUND,
							protocol < 755 ? "WORLD_BORDER" : "INITIALIZE_BORDER",
							protocol,
							b -> {
								if (protocol < 755) b.writeByte(3);
								b.writeDouble(12)
										.writeDouble(-24)
										.writeDouble(600)
										.writeDouble(400);
								VarLongCodec.INSTANCE.encode(b, 60000L);
								ByteBufStreamCodecs.VARINT.encode(b, 29999984);
								b.writeByte(5).writeByte(15);
							});
			assertEquals(12, border.center().getX());
			assertEquals(-24, border.center().getZ());
			assertEquals(400.0, border.size());
			assertEquals(60000L, border.lerpTime());
		}
	}

	@Test
	void modernWorldBorderPacketsContainOnlyTheirOwnActionPayload() {
		WorldBorderWirePacket size =
				roundTrip(CLIENTBOUND, "SET_BORDER_SIZE", 777, b -> b.writeDouble(400));
		assertEquals(400.0, size.size());
		assertNull(size.center());
		WorldBorderWirePacket center =
				roundTrip(
						CLIENTBOUND,
						"SET_BORDER_CENTER",
						777,
						b -> b.writeDouble(12).writeDouble(24));
		assertEquals(24, center.center().getZ());
		WorldBorderWirePacket lerp =
				roundTrip(
						CLIENTBOUND,
						"SET_BORDER_LERP_SIZE",
						777,
						b -> {
							b.writeDouble(600).writeDouble(400);
							VarLongCodec.INSTANCE.encode(b, 60000L);
						});
		assertEquals(60000L, lerp.lerpTime());
		roundTrip(CLIENTBOUND, "SET_BORDER_WARNING_DELAY", 777, b -> b.writeByte(15));
		roundTrip(CLIENTBOUND, "SET_BORDER_WARNING_DISTANCE", 777, b -> b.writeByte(5));
		assertThrows(
				IllegalArgumentException.class,
				() -> size.setCenter(new de.jpx3.intave.share.Position()));
	}
}
