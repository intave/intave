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

package de.jpx3.intave.packet.wire.entity.attributes;

import de.jpx3.intave.packet.contract.immutable.EntityAttributesPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityAttributesPacket;
import de.jpx3.intave.packet.wire.WirePacketTestSupport;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.registry.PacketTypeRegistries;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.UPDATE_ATTRIBUTES;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class EntityAttributesWirePacketTest {
	private static final UUID SPRINT = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");

	@Test
	void snapshotsRoundTripInEveryRegisteredProtocol() {
		for (int protocol : PacketTypeRegistries.clientbound().supportedProtocolVersions()) {
			String key = protocol < 735 ? "generic.movementSpeed"
				: protocol < 768 ? "minecraft:generic.movement_speed" : "minecraft:movement_speed";
			EntityAttributesPacket packet = roundTrip(CLIENTBOUND, UPDATE_ATTRIBUTES, protocol, out -> {
				VARINT.encode(out, 300);
				if (protocol < 755) out.writeInt(1);
				else VARINT.encode(out, 1);
				if (protocol < 766) VARINT_STRING.encode(out, key);
				else VARINT.encode(out, movementSpeedId(protocol));
				out.writeDouble(0.25);
				VARINT.encode(out, 2);
				if (protocol < 767) out.writeLong(SPRINT.getMostSignificantBits()).writeLong(SPRINT.getLeastSignificantBits());
				else VARINT_STRING.encode(out, "minecraft:sprinting");
				out.writeDouble(0.3).writeByte(2);
				if (protocol < 767) out.writeLong(1).writeLong(2);
				else VARINT_STRING.encode(out, "plugin:bonus");
				out.writeDouble(-0.125).writeByte(1);
			});
			assertEquals(300, packet.entityId());
			assertEquals(1, packet.attributes().size());
			var attribute = packet.attributes().get(0);
			assertEquals(key, attribute.attributeKey(), "protocol " + protocol);
			assertEquals(0.25, attribute.baseValue());
			var modifiers = new ArrayList<>(attribute.modifiers());
			assertEquals(2, modifiers.size());
			if (protocol < 767) assertEquals(SPRINT, modifiers.get(0).id());
			else assertEquals("minecraft:sprinting", modifiers.get(0).key().fullKey());
			assertEquals(AttributeModifier.Operation.ADD_PERCENTAGE, modifiers.get(0).operation());
			assertEquals(-0.125, modifiers.get(1).amount());
			assertEquals(AttributeModifier.Operation.MULTIPLY_PERCENTAGE, modifiers.get(1).operation());
		}
	}

	private static int movementSpeedId(int protocol) {
		if (protocol == 766) return 17;
		if (protocol < 771) return 21;
		if (protocol < 776) return 22;
		return 26;
	}

	@Test
	void freshAndDecodedPacketsStayCleanUntilEdited() {
		for (int protocol : PacketTypeRegistries.clientbound().supportedProtocolVersions()) {
			MutableEntityAttributesPacket empty = WirePackets.createPlayPacket(UPDATE_ATTRIBUTES, protocol);
			assertFalse(empty.couldHaveBeenMutated());
			empty.setEntityId(3);
			assertTrue(empty.couldHaveBeenMutated());

			MutableEntityAttributesPacket decoded = roundTrip(CLIENTBOUND, UPDATE_ATTRIBUTES, protocol, out -> {
				VARINT.encode(out, 3);
				if (protocol < 755) out.writeInt(0);
				else VARINT.encode(out, 0);
			});
			assertTrue(decoded.attributes().isEmpty());
			assertFalse(decoded.couldHaveBeenMutated());
			decoded.setAttributes(Collections.emptyList());
			assertTrue(decoded.couldHaveBeenMutated());
		}
	}

	@Test
	void snapshotCountLimitStartsIn262() {
		for (int protocol : new int[]{775, 776}) {
			ByteBuf payload = Unpooled.buffer();
			try {
				VARINT.encode(payload, 42);
				VARINT.encode(payload, 129);
				for (int i = 0; i < 129; i++) payload.writeByte(0).writeDouble(i).writeByte(0);
				if (protocol == 775) {
					assertEquals(129, WirePacketTestSupport.decodePayload(EntityAttributesWirePacket.Protocol771.class, payload).attributes().size());
				} else {
					assertThrows(IllegalArgumentException.class, () ->
						WirePacketTestSupport.decodePayload(EntityAttributesWirePacket.Protocol776.class, payload));
				}
			} finally {
				payload.release();
			}
		}
	}
}
