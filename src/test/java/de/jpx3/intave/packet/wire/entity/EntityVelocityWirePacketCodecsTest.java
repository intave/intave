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

import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.share.Motion;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class EntityVelocityWirePacketCodecsTest {
	@Test
	void velocityPreservesQuantizedValuesWithoutRoundingThemAgain() {
		EntityVelocityWirePacket.Base packet =
				roundTrip(
						CLIENTBOUND,
						PacketType.Server.ENTITY_VELOCITY,
						772,
						b ->
								b.writeByte(42)
										.writeShort(2949)
										.writeShort(-2949)
										.writeShort(Short.MIN_VALUE));
		assertEquals(2949 / 8000.0, packet.motion().motionX);
		assertEquals(-2949 / 8000.0, packet.motion().motionY);
		EntityVelocityWirePacket.Base zero =
				roundTrip(CLIENTBOUND, PacketType.Server.ENTITY_VELOCITY, 773, b -> b.writeByte(42).writeByte(0));
		assertEquals(0, zero.motion().motionX);
	}

	@Test
	void exposedMotionMarksPossibleChanges() {
		Motion motion = new Motion(1, 2, 3);
		EntityVelocityWirePacket.Protocol773 velocity = new EntityVelocityWirePacket.Protocol773(12, motion);
		assertFalse(velocity.couldHaveBeenMutated());
		Motion exposed = velocity.motion();
		assertTrue(velocity.couldHaveBeenMutated());
		exposed.motionX = 5;
		assertEquals(5, velocity.motion().motionX);
	}
}
