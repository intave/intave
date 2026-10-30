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

package de.jpx3.intave.packet.wire.client.settings;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.contract.mutable.MutableClientSettingsPacket;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ClientSettingsWirePacketCodecsTest {
	@Test
	void clientSettingsKeepEachAddedFieldInOrder() {
		for (int protocol : new int[] {47, 107, 755, 757, 768}) {
			MutableClientSettingsPacket packet =
					roundTrip(
							SERVERBOUND,
							"CLIENT_SETTINGS",
							protocol,
							b -> {
								ByteBufStreamCodecs.VARINT_STRING.encode(b, "en_us");
								b.writeByte(10).writeByte(0).writeBoolean(true).writeByte(127);
								if (protocol >= 107) b.writeByte(1);
								if (protocol >= 755) b.writeBoolean(true);
								if (protocol >= 757) b.writeBoolean(false);
								if (protocol >= 768) b.writeByte(2);
							});
			assertEquals("en_us", packet.locale());
			if (protocol == 47) assertNull(packet.mainHand());
			else assertEquals(1, packet.mainHand().ordinal());
		}
	}
}
