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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EntityUseWirePacketTest {
	@Test
	void actionChangesOmitFieldsThatAreNotPartOfTheNewAction() {
		EntityUseWirePacket packet =
				new EntityUseWirePacket(
						42,
						EntityUseAction.INTERACT_AT,
						new RawVector3d(1, 2, 3),
						PlayerHand.OFF_HAND,
						true);
		ByteBuf buffer = Unpooled.buffer();
		try {
			packet.setAction(EntityUseAction.ATTACK);
			assertNull(packet.hand());
			assertNull(packet.hitPosition());
			EntityUseWirePacket.STREAM_CODEC.encode(buffer, packet);
			assertEquals(42, buffer.readUnsignedByte());
			assertEquals(1, buffer.readUnsignedByte());
			assertTrue(buffer.readBoolean());
			assertFalse(buffer.isReadable());

			packet.setAction(EntityUseAction.INTERACT);
			assertEquals(PlayerHand.OFF_HAND, packet.hand());
			assertNull(packet.hitPosition());
			EntityUseWirePacket.STREAM_CODEC.encode(buffer, packet);
			assertEquals(42, buffer.readUnsignedByte());
			assertEquals(0, buffer.readUnsignedByte());
			assertEquals(1, buffer.readUnsignedByte());
			assertTrue(buffer.readBoolean());
			assertFalse(buffer.isReadable());
		} finally {
			buffer.release();
		}
	}

	@Test
	void malformedActionsHandsAndMissingHitPositionsAreRejected() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			for (int id : new int[] {-1, 3, Integer.MAX_VALUE}) {
				buffer.clear();
				ByteBufStreamCodecs.VARINT.encode(buffer, id);
				assertThrows(
						IllegalArgumentException.class,
						() -> EntityUseInteraction.STREAM_CODEC.decode(buffer));
			}
			buffer.clear().writeByte(0).writeByte(2);
			assertThrows(
					IllegalArgumentException.class,
					() -> EntityUseInteraction.STREAM_CODEC.decode(buffer));
			buffer.clear().writeByte(2).writeFloat(1);
			assertThrows(
					IndexOutOfBoundsException.class,
					() -> EntityUseInteraction.STREAM_CODEC.decode(buffer));

			EntityUseWirePacket packet = new EntityUseWirePacket();
			packet.setAction(EntityUseAction.INTERACT_AT);
			buffer.clear();
			assertThrows(
					IllegalArgumentException.class,
					() -> EntityUseWirePacket.STREAM_CODEC.encode(buffer, packet));
			packet.setHitPosition(new RawVector3d(1, 2, 3));
			packet.clearHand();
			buffer.clear();
			assertThrows(
					IllegalArgumentException.class,
					() -> EntityUseWirePacket.STREAM_CODEC.encode(buffer, packet));
			assertThrows(NullPointerException.class, () -> packet.setAction(null));
		} finally {
			buffer.release();
		}
	}

	@Test
	void preHandVersionKeepsItsEmptyPayloadAndRejectsAddingAHand() {
		EntityUseWithoutHandWirePacket packet = new EntityUseWithoutHandWirePacket();
		assertNull(packet.hand());
		packet.clearHand();
		assertThrows(
				UnsupportedOperationException.class, () -> packet.setHand(PlayerHand.MAIN_HAND));
		ByteBuf buffer = Unpooled.buffer();
		try {
			EntityUseWithoutHandWirePacket.STREAM_CODEC.encode(buffer, packet);
			assertEquals(0, buffer.readUnsignedByte());
			assertEquals(0, buffer.readUnsignedByte());
			assertFalse(buffer.isReadable());
		} finally {
			buffer.release();
		}
	}
}
