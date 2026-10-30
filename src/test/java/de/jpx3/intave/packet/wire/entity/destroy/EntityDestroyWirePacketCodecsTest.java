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

package de.jpx3.intave.packet.wire.entity.destroy;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EntityDestroyWirePacketCodecsTest {
	@Test
	void entityRemovalUsesSingleIdOnlyInOneSeventeen() {
		for (int protocol : new int[] {754, 755, 756}) {
			EntityDestroyWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"ENTITY_DESTROY",
							protocol,
							b -> {
								if (protocol != 755) b.writeByte(1);
								b.writeByte(42);
							});
			assertEquals(1, packet.entityCount());
			assertEquals(42, packet.entityId(0));
		}
	}
}
