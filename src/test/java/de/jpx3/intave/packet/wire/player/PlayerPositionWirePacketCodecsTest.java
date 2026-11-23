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

package de.jpx3.intave.packet.wire.player;

import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.event.PacketEvent;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.wire.WirePacketTestSupport.decodePayload;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.encodePayload;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerPositionWirePacketCodecsTest {
	@Test
	void unsupportedFieldsCannotBeAddedToPacketsThatDoNotCarryThem() {
		assertNull(new PlayerPositionWirePacket.Protocol768().rotation());
		assertThrows(
				UnsupportedOperationException.class,
				() ->
						new PlayerPositionWirePacket.Protocol768()
								.setRotation(new de.jpx3.intave.share.Rotation(1, 2)));
	}

	@Test
	void decodedPositionsStayCleanUntilEditingIsRequested() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			encodePayload(new PlayerPositionWirePacket.Protocol768(new Position(1, 2, 3), 1), input);
			PlayerPositionWirePacket.Protocol768 packet = decodePayload(PlayerPositionWirePacket.Protocol768.class, input);
			PacketEvent event = new PacketReceiveEvent(null, PacketType.Client.POSITION, packet);
			Position original = packet.position();
			assertTrue(original.isImmutable());
			assertEquals(new Position(1, 2, 3), original);
			assertTrue(packet.hasPosition());
			encodePayload(packet, output);
			assertFalse(event.dirty());

			Position editable = packet.mutablePosition();
			assertFalse(editable.isImmutable());
			assertNotSame(original, editable);
			assertSame(editable, packet.mutablePosition());
			assertTrue(event.dirty());
			editable.setX(9);
			assertEquals(1, original.getX());
			output.clear();
			encodePayload(packet, output);
			PlayerPositionWirePacket.Protocol768 decoded = decodePayload(PlayerPositionWirePacket.Protocol768.class, output);
			assertEquals(new Position(9, 2, 3), decoded.position());
			assertEquals(1, decoded.flags());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void alreadyMutablePositionsAreTrackedAndReused() {
		Position position = Position.mutableOf(1, 2, 3);
		PlayerPositionWirePacket.Protocol768 packet = new PlayerPositionWirePacket.Protocol768(position, 0);
		assertFalse(packet.couldHaveBeenMutated());
		assertSame(position, packet.position());
		assertTrue(packet.couldHaveBeenMutated());
		assertSame(position, packet.mutablePosition());
		packet.mutablePosition().setY(7);
		assertEquals(7, packet.position().getY());

		PlayerPositionWirePacket.Protocol768 replaced = new PlayerPositionWirePacket.Protocol768();
		replaced.setPosition(new Position(4, 5, 6));
		assertTrue(replaced.couldHaveBeenMutated(), "Replacing a position still changes the packet");
	}

	@Test
	void nullPositionStaysCleanAndIsNotCreatedForEditing() {
		PlayerPositionWirePacket.Protocol768 nullable = new PlayerPositionWirePacket.Protocol768(null, 0);
		assertNull(nullable.mutablePosition());
		assertFalse(nullable.couldHaveBeenMutated());
	}
}
