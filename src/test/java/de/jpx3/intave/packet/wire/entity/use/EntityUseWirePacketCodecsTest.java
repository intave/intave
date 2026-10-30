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

package de.jpx3.intave.packet.wire.entity.use;

import de.jpx3.intave.packet.contract.EntityVelocityPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityUsePacket;
import de.jpx3.intave.share.EntityUseAction;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class EntityUseWirePacketCodecsTest {
	@Test
	void interactionsAndVelocitiesShareTheLowPrecisionVectorFormat() {
		byte[][] vectors = {
			{0},
			{(byte) 0xf1, (byte) 0xff, 0, 0, (byte) 0xff, (byte) 0xff},
			{(byte) 0xf4, (byte) 0xff, 0, 0, (byte) 0xff, (byte) 0xff, 1}
		};
		double[] scales = {0, 1, 4};
		for (int i = 0; i < vectors.length; i++) {
			byte[] vector = vectors[i];
			for (int protocol : new int[] {775, 777}) {
				InteractEntityWirePacket interaction = roundTrip(
					SERVERBOUND, "USE_ENTITY", protocol,
					buffer -> buffer.writeByte(42).writeByte(1).writeBytes(vector).writeBoolean(true));
				assertEquals(42, interaction.entityId());
				assertEquals(de.jpx3.intave.share.PlayerHand.OFF_HAND, interaction.hand());
				assertEquals(scales[i], interaction.hitPosition().x());
				assertEquals(0, interaction.hitPosition().y());
				assertEquals(-scales[i], interaction.hitPosition().z(), 0.0);
				assertTrue(interaction.secondary());
			}
			EntityVelocityPacket velocity = roundTrip(
				CLIENTBOUND, "ENTITY_VELOCITY", 773,
				buffer -> buffer.writeByte(42).writeBytes(vector));
			assertEquals(scales[i], velocity.motion().motionX());
			assertEquals(0, velocity.motion().motionY());
			assertEquals(-scales[i], velocity.motion().motionZ(), 0.0);
		}
	}

	@Test
	void entityInteractionAddsHandThenSecondaryAction() {
		EntityUseAction[] actions = {
			EntityUseAction.INTERACT, EntityUseAction.ATTACK, EntityUseAction.INTERACT_AT
		};
		for (int protocol : new int[] {47, 107, 578, 735, 774}) {
			for (int actionId = 0; actionId < actions.length; actionId++) {
				final int id = actionId;
				MutableEntityUsePacket packet =
						roundTrip(
								SERVERBOUND,
								"USE_ENTITY",
								protocol,
								b -> {
									b.writeByte(42).writeByte(id);
									if (id == 2)
										b.writeFloat(0.5f).writeFloat(1.25f).writeFloat(-0.5f);
									if (protocol >= 107 && id != 1) b.writeByte(1);
									if (protocol >= 735) b.writeBoolean(true);
								});
				assertEquals(actions[id], packet.action());
				if (id == 2) assertEquals(0.5, packet.hitPosition().x());
				else assertNull(packet.hitPosition());
				if (protocol < 107 || id == 1) assertNull(packet.hand());
				else assertEquals(1, packet.hand().ordinal());
				if (protocol >= 735) assertTrue(((EntityUseWirePacket) packet).secondary());
			}
		}
	}
}
