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

package de.jpx3.intave.packet.wire.block.dig;

import de.jpx3.intave.share.PlayerDigAction;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlockDigWirePacketCodecsTest {
	@Test
	void blockPositionPackingAndDigActionIndicesFollowTheClientVersion() {
		for (int protocol : new int[] {47, 477, 759, 777}) {
			BlockDigWirePacket packet =
					roundTrip(
							SERVERBOUND,
							"BLOCK_DIG",
							protocol,
							b -> {
								b.writeByte(protocol >= 777 ? 2 : 1);
								long x = -7 & 0x3ffffffL, y = -12 & 0xfffL, z = 19;
								b.writeLong(
										protocol < 477
												? x << 38 | y << 26 | z
												: x << 38 | z << 12 | y);
								b.writeByte(1);
								if (protocol >= 759) b.writeByte(42);
							});
			assertEquals(-7, packet.blockPosition().getX());
			assertEquals(-12, packet.blockPosition().getY());
			assertEquals(19, packet.blockPosition().getZ());
			assertEquals(PlayerDigAction.ABORT_DESTROY_BLOCK, packet.action());
		}
	}
}
