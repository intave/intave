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

package de.jpx3.intave.packet.wire.ability.inbound;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class AbilityInWirePacketCodecsTest {
	@Test
	void unsupportedFieldsCannotBeAddedToPacketsThatDoNotCarryThem() {
		AbilityInWirePacket modernAbility = new AbilityInWirePacket(2);
		assertTrue(modernAbility.requestedFlying());
		assertThrows(UnsupportedOperationException.class, modernAbility::flyingSpeed);
		assertThrows(UnsupportedOperationException.class, () -> modernAbility.setFlyingSpeed(0.1f));
		AbilityInWithSpeedsWirePacket legacyAbility =
				new AbilityInWithSpeedsWirePacket(2, 0.05f, 0.1f);
		assertEquals(0.05f, legacyAbility.flyingSpeed());
		assertEquals(0.1f, legacyAbility.walkingSpeed());
	}

	@Test
	void abilitiesUseSpeedsOnlyBeforeOneSixteen() {
		AbilityInWirePacket legacy =
				roundTrip(
						SERVERBOUND,
						"ABILITIES",
						578,
						b -> b.writeByte(2).writeFloat(0.05f).writeFloat(0.1f));
		AbilityInWirePacket modern = roundTrip(SERVERBOUND, "ABILITIES", 735, b -> b.writeByte(2));
		assertInstanceOf(AbilityInWithSpeedsWirePacket.class, legacy);
		assertEquals(AbilityInWirePacket.class, modern.getClass());
		assertTrue(legacy.requestedFlying());
		assertTrue(modern.requestedFlying());
	}
}
