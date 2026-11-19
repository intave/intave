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

package de.jpx3.intave.module.player;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.EntityVelocityPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QPressDebugTrackerTest {
	@Test
	void legacyDebugVelocityUsesTheCodecClampAndTruncation() {
		for (int protocol : new int[]{47, 754, 772}) {
			ByteBuf encoded = Unpooled.buffer();
			try {
				WirePackets.encodePacket(ConnectionState.PLAY, PacketType.Server.ENTITY_VELOCITY, protocol,
					QPressDebugTracker.velocityPacket(protocol, 321, new Vector(8, -8, -0.12349)), encoded);
				ByteBufStreamCodecs.VARINT.decode(encoded);
				assertEquals(321, ByteBufStreamCodecs.VARINT.decode(encoded));
				assertEquals(31200, encoded.readShort());
				assertEquals(-31200, encoded.readShort());
				assertEquals(-987, encoded.readShort());
				assertFalse(encoded.isReadable());
			} finally {
				encoded.release();
			}
		}
	}

	@Test
	void modernDebugVelocityExceedsTheLegacyShortLimit() {
		for (int protocol : new int[]{773, 777}) {
			ByteBuf encoded = Unpooled.buffer();
			try {
				WirePackets.encodePacket(ConnectionState.PLAY, PacketType.Server.ENTITY_VELOCITY, protocol,
					QPressDebugTracker.velocityPacket(protocol, 321, new Vector(8, 0.4, -8)), encoded);
				EntityVelocityPacket packet = WirePackets.decodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, protocol, encoded);
				assertEquals(321, packet.entityId());
				assertEquals(8, packet.motion().motionX(), 0.001);
				assertEquals(0.4, packet.motion().motionY(), 0.001);
				assertEquals(-8, packet.motion().motionZ(), 0.001);
			} finally {
				encoded.release();
			}
		}
	}
}
