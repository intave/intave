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

package de.jpx3.intave.packet.wire.entity.velocity;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EntityVelocityWirePacketCodecsTest {
	@Test
	void velocityPreservesQuantizedValuesWithoutRoundingThemAgain() {
		EntityVelocityWirePacket packet =
				roundTrip(
						CLIENTBOUND,
						"ENTITY_VELOCITY",
						772,
						b ->
								b.writeByte(42)
										.writeShort(2949)
										.writeShort(-2949)
										.writeShort(Short.MIN_VALUE));
		assertEquals(2949 / 8000.0, packet.motion().motionX);
		assertEquals(-2949 / 8000.0, packet.motion().motionY);
		EntityVelocityWirePacket zero =
				roundTrip(CLIENTBOUND, "ENTITY_VELOCITY", 773, b -> b.writeByte(42).writeByte(0));
		assertEquals(0, zero.motion().motionX);
	}
}
