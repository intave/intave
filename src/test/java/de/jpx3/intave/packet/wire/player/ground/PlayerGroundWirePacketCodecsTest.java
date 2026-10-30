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

package de.jpx3.intave.packet.wire.player.ground;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerGroundWirePacketCodecsTest {
	@Test
	void modernMovementRetainsHorizontalCollision() {
		PlayerGroundWirePacket packet = roundTrip(SERVERBOUND, "GROUND", 768, b -> b.writeByte(3));
		assertTrue(packet.onGround());
		assertTrue(packet.horizontalCollision());
		PlayerGroundWirePacket legacy = roundTrip(SERVERBOUND, "GROUND", 767, b -> b.writeByte(1));
		assertNull(legacy.horizontalCollision());
	}

	@Test
	void olderVariantsRejectFieldsTheirFormatCannotEncode() {
		assertThrows(
				IllegalStateException.class,
				() -> new PlayerGroundOnGroundOnlyWirePacket().setHorizontalCollision(true));
	}
}
