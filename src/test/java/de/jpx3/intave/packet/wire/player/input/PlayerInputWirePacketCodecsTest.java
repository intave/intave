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

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerInputWirePacketCodecsTest {
	@Test
	void legacyInputRetainsAnalogAxesAndUnexposedFlagBits() {
		LegacyPlayerInputWirePacket packet =
				roundTrip(
						SERVERBOUND,
						"PLAYER_INPUT",
						767,
						b -> b.writeFloat(-0.25f).writeFloat(0.75f).writeByte(0x83));
		assertEquals(-0.25f, packet.strafe());
		assertEquals(0.75f, packet.forward());
		assertTrue(packet.jumping());
		assertTrue(packet.dismounting());
		assertNull(packet.input());
	}
}
