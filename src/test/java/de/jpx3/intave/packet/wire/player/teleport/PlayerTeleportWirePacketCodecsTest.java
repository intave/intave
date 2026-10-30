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

package de.jpx3.intave.packet.wire.player.teleport;

import de.jpx3.intave.packet.contract.mutable.MutablePlayerTeleportPacket;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerTeleportWirePacketCodecsTest {
	@Test
	void playerTeleportFormatsPreserveIdsDismountAndMotion() {
		for (int protocol : new int[] {47, 107, 755, 762, 768}) {
			MutablePlayerTeleportPacket packet =
					roundTrip(
							CLIENTBOUND,
							"POSITION",
							protocol,
							b -> {
								if (protocol >= 768) b.writeByte(42);
								b.writeDouble(1).writeDouble(2).writeDouble(3);
								if (protocol >= 768)
									b.writeDouble(0.5).writeDouble(-0.25).writeDouble(0);
								b.writeFloat(90).writeFloat(45);
								if (protocol >= 768) b.writeInt(7);
								else b.writeByte(7);
								if (protocol >= 107 && protocol < 768) b.writeByte(42);
								if (protocol == 755) b.writeBoolean(true);
							});
			assertEquals(2, packet.position().getY());
			assertEquals(protocol >= 107, packet.teleportId().isPresent());
			if (protocol >= 768) assertEquals(0.5, packet.motion().motionX);
			else assertNull(packet.motion());
		}
	}

	@Test
	void olderVariantsRejectFieldsTheirFormatCannotEncode() {
		assertThrows(
				IllegalStateException.class,
				() -> new PlayerTeleportWithoutIdWirePacket().setTeleportId(1));
	}
}
