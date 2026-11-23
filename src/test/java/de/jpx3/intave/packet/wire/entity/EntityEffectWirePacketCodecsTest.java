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

package de.jpx3.intave.packet.wire.entity;

import de.jpx3.intave.packet.contract.immutable.EntityEffectPacket;
import de.jpx3.intave.packet.contract.immutable.RemoveEntityEffectPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityEffectPacket;
import de.jpx3.intave.packet.contract.mutable.MutableRemoveEntityEffectPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.ENTITY_EFFECT;
import static de.jpx3.intave.packet.contract.PacketType.Server.REMOVE_ENTITY_EFFECT;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.encodePayload;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class EntityEffectWirePacketCodecsTest {
	@Test
	void movementEffectsRoundTripInEveryRegisteredProtocol() {
		String[] names = {"speed", "slowness", "jump_boost"};
		int[] legacyIds = {1, 2, 8};
		for (int protocol : PacketTypeRegistries.clientbound().supportedProtocolVersions()) {
			for (int i = 0; i < names.length; i++) {
				int id = legacyIds[i] - (protocol >= 764 ? 1 : 0);
				MinecraftKey key = MinecraftKey.withDefaultNamespace(names[i]);
				EntityEffectPacket effect = roundTrip(CLIENTBOUND, ENTITY_EFFECT, protocol,
					b -> writeEffect(b, protocol, 300, id, 2, 600, false));
				assertEquals(300, effect.entityId());
				assertEquals(key, effect.effectType(), "protocol " + protocol);
				assertEquals(2, effect.effectAmplifier());
				assertEquals(600, effect.effectDuration());
				assertFalse(effect.infinite());
				RemoveEntityEffectPacket removal = roundTrip(CLIENTBOUND, REMOVE_ENTITY_EFFECT, protocol, b -> {
					VARINT.encode(b, 300);
					writeEffectId(b, protocol, id);
				});
				assertEquals(300, removal.entityId());
				assertEquals(key, removal.effectType());
			}
		}
	}

	@Test
	void factorDataAndFlagsSurviveMutation() {
		for (int protocol : new int[]{759, 760, 761, 762, 763, 764, 765}) {
			int darknessId = protocol < 764 ? 33 : 32;
			MutableEntityEffectPacket effect = roundTrip(CLIENTBOUND, ENTITY_EFFECT, protocol,
				b -> writeEffect(b, protocol, 300, darknessId, 1, 600, true));
			assertEquals(MinecraftKey.withDefaultNamespace("darkness"), effect.effectType());
			assertFalse(effect.couldHaveBeenMutated());
			effect.setEntityId(401);
			effect.setEffectType(MinecraftKey.withDefaultNamespace("jump_boost"));
			effect.setEffectAmplifier(3);
			effect.setInfinite();
			assertTrue(effect.couldHaveBeenMutated());
			assertTrue(effect.infinite());
			ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
			try {
				writeEffect(expected, protocol, 401, protocol < 764 ? 8 : 7, 3, -1, true);
				encodePayload(effect, actual);
				assertEquals(expected, actual, "Flags and factor NBT must be preserved in " + protocol);
			} finally {
				expected.release();
				actual.release();
			}
		}
	}

	@Test
	void amplifierChangesFromSignedByteToVarIntIn1205() {
		for (int protocol : new int[]{47, 758, 759, 764, 765, 766, 777}) {
			EntityEffectPacket effect = roundTrip(CLIENTBOUND, ENTITY_EFFECT, protocol,
				b -> writeEffect(b, protocol, 1, protocol < 764 ? 1 : 0, 255, -1, false));
			assertEquals(protocol < 766 ? -1 : 255, effect.effectAmplifier());
			assertTrue(effect.infinite());
		}
	}

	@Test
	void modernEffectsAndMutableRemovalsUseZeroBasedIds() {
		for (int protocol : new int[]{774, 775, 776, 777}) {
			MutableRemoveEntityEffectPacket removal = roundTrip(CLIENTBOUND, REMOVE_ENTITY_EFFECT, protocol,
				b -> b.writeByte(1).writeByte(39));
			assertEquals(MinecraftKey.withDefaultNamespace("breath_of_the_nautilus"), removal.effectType());
			assertFalse(removal.couldHaveBeenMutated());
			removal.setEntityId(300);
			removal.setEffectType(MinecraftKey.withDefaultNamespace("speed"));
			assertTrue(removal.couldHaveBeenMutated());
			ByteBuf encoded = Unpooled.buffer();
			try {
				WirePackets.encodePacket(PLAY, REMOVE_ENTITY_EFFECT, protocol, removal, encoded);
				RemoveEntityEffectPacket decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, encoded);
				assertEquals(300, decoded.entityId());
				assertEquals(MinecraftKey.withDefaultNamespace("speed"), decoded.effectType());
			} finally {
				encoded.release();
			}
		}
	}

	private static void writeEffect(ByteBuf b, int protocol, int entityId, int id, int amplifier, int duration, boolean factor) {
		VARINT.encode(b, entityId);
		writeEffectId(b, protocol, id);
		if (protocol < 766) b.writeByte(amplifier);
		else VARINT.encode(b, amplifier);
		VARINT.encode(b, duration);
		b.writeByte(0x0f);
		if (protocol >= 759 && protocol < 766) {
			b.writeBoolean(factor);
			if (factor) {
				// A compound containing an integer "padding_duration" = 22, in the client's NBT format.
				b.writeByte(10);
				if (protocol < 764) b.writeShort(0);
				b.writeByte(3).writeShort(16).writeBytes(new byte[]{
					'p', 'a', 'd', 'd', 'i', 'n', 'g', '_', 'd', 'u', 'r', 'a', 't', 'i', 'o', 'n'
				});
				b.writeInt(22).writeByte(0);
			}
		}
	}

	private static void writeEffectId(ByteBuf b, int protocol, int id) {
		if (protocol < 758) b.writeByte(id);
		else VARINT.encode(b, id);
	}
}
