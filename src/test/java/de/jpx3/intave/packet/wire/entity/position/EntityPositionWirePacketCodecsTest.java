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

package de.jpx3.intave.packet.wire.entity.position;

import de.jpx3.intave.packet.contract.mutable.MutableEntityPositionPacket;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EntityPositionWirePacketCodecsTest {
	@Test
	void entityTeleportsPreserveRotationMotionAndRelativeFlags() {
		for (int protocol : new int[] {47, 107, 768}) {
			EntityPositionDoublePositionWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"ENTITY_TELEPORT",
							protocol,
							b -> {
								b.writeByte(42);
								if (protocol < 107) b.writeInt(48).writeInt(64).writeInt(-32);
								else b.writeDouble(1.5).writeDouble(2).writeDouble(-1);
								if (protocol >= 768)
									b.writeDouble(0.1)
											.writeDouble(0.2)
											.writeDouble(0.3)
											.writeFloat(90)
											.writeFloat(45)
											.writeInt(7);
								else b.writeByte(64).writeByte(32);
								b.writeBoolean(true);
							});
			assertEquals(1.5, packet.position().getX());
			assertEquals(-1, packet.position().getZ());
		}
	}

	@Test
	void entityPositionSyncKeepsMotionOnlyInTheEarlierFormat() {
		for (int protocol : new int[] {768, 777}) {
			MutableEntityPositionPacket packet =
					roundTrip(
							CLIENTBOUND,
							"ENTITY_POSITION_SYNC",
							protocol,
							b -> {
								b.writeByte(42);
								if (protocol == 777) b.writeByte(0);
								b.writeDouble(1).writeDouble(2).writeDouble(3);
								if (protocol == 768)
									b.writeDouble(0.1).writeDouble(0.2).writeDouble(0.3);
								b.writeFloat(90).writeFloat(45).writeBoolean(true);
							});
			assertEquals(2, packet.position().getY());
		}
	}
}
