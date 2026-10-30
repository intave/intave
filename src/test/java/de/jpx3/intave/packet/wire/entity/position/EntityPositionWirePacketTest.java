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

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

final class EntityPositionWirePacketTest {
	@Test
	void legacyTeleportsExposeRotationAndGroundState() {
		verifyLegacy(EntityPositionFixedPointWirePacket.STREAM_CODEC, true);
		verifyLegacy(EntityPositionDoublePositionWirePacket.STREAM_CODEC, false);
	}

	private static <T extends EntityPositionDoublePositionWirePacket> void verifyLegacy(
		StreamCodec<ByteBuf, ByteBuf, T> codec, boolean fixedPoint
	) {
		T packet = roundTrip(codec, buffer -> {
			buffer.writeByte(42);
			if (fixedPoint) buffer.writeInt(48).writeInt(64).writeInt(-32);
			else buffer.writeDouble(1.5).writeDouble(2).writeDouble(-1);
			buffer.writeByte(0x81).writeByte(0xff).writeBoolean(true);
		});
		assertEquals(new Rotation(-127 * 360.0f / 256, -360.0f / 256), packet.rotation());
		assertTrue(packet.onGround());
		packet.setPosition(new Position(4, 5, -6));
		packet.setRotation(new Rotation(90, -45));
		packet.setOnGround(false);
		assertEncoding(codec, packet, buffer -> {
			buffer.writeByte(42);
			if (fixedPoint) buffer.writeInt(128).writeInt(160).writeInt(-192);
			else buffer.writeDouble(4).writeDouble(5).writeDouble(-6);
			buffer.writeByte(64).writeByte(-32).writeBoolean(false);
		});
	}

	@Test
	void modernTeleportPreservesMotionRotationFlagsAndGroundWhenPositionChanges() {
		EntityPositionWirePacket packet = roundTrip(EntityPositionWirePacket.STREAM_CODEC, buffer ->
			buffer.writeByte(42).writeDouble(1).writeDouble(2).writeDouble(3)
				.writeDouble(0.1).writeDouble(-0.2).writeDouble(0.3)
				.writeFloat(-90.5f).writeFloat(45.25f).writeInt(329).writeBoolean(true));
		assertEquals(new Motion(0.1, -0.2, 0.3), packet.motion());
		assertEquals(new Rotation(-90.5f, 45.25f), packet.rotation());
		assertEquals(EnumSet.of(Relative.X, Relative.Y_ROT, Relative.DELTA_Y, Relative.ROTATE_DELTA), packet.flags());
		assertTrue(packet.onGround());
		packet.setPosition(new Position(4, 5, 6));
		assertEncoding(EntityPositionWirePacket.STREAM_CODEC, packet, buffer ->
			buffer.writeByte(42).writeDouble(4).writeDouble(5).writeDouble(6)
				.writeDouble(0.1).writeDouble(-0.2).writeDouble(0.3)
				.writeFloat(-90.5f).writeFloat(45.25f).writeInt(329).writeBoolean(true));
	}

	@Test
	void earlierSyncPreservesMotionAndAllowsEditingTheFormerTrailingFields() {
		EntityPositionSyncPositionMoveRotationWirePacket packet = roundTrip(
			EntityPositionSyncPositionMoveRotationWirePacket.STREAM_CODEC, buffer ->
				buffer.writeByte(42).writeDouble(1).writeDouble(2).writeDouble(3)
					.writeDouble(0.1).writeDouble(0.2).writeDouble(0.3)
					.writeFloat(90).writeFloat(45).writeBoolean(true));
		assertEquals(new Motion(0.1, 0.2, 0.3), packet.motion());
		assertEquals(new Rotation(90, 45), packet.rotation());
		assertTrue(packet.onGround());
		packet.setPosition(new Position(4, 5, 6));
		packet.setRotation(new Rotation(-90, -45));
		packet.setOnGround(false);
		assertEncoding(EntityPositionSyncPositionMoveRotationWirePacket.STREAM_CODEC, packet, buffer ->
			buffer.writeByte(42).writeDouble(4).writeDouble(5).writeDouble(6)
				.writeDouble(0.1).writeDouble(0.2).writeDouble(0.3)
				.writeFloat(-90).writeFloat(-45).writeBoolean(false));
	}

	@Test
	void linearSyncPreservesItsPathTypeWhenPositionChanges() {
		EntityPositionSyncWirePacket packet = roundTrip(EntityPositionSyncWirePacket.STREAM_CODEC, buffer ->
			buffer.writeByte(42).writeByte(0).writeDouble(1).writeDouble(2).writeDouble(3)
				.writeFloat(90).writeFloat(45).writeBoolean(true));
		assertFalse(packet.path().stepped());
		assertEquals(new Rotation(90, 45), packet.rotation());
		assertTrue(packet.onGround());
		packet.setPosition(new Position(4, 5, 6));
		assertEncoding(EntityPositionSyncWirePacket.STREAM_CODEC, packet, buffer ->
			buffer.writeByte(42).writeByte(0).writeDouble(4).writeDouble(5).writeDouble(6)
				.writeFloat(90).writeFloat(45).writeBoolean(true));
	}

	@Test
	void steppedSyncPreservesEarlierStepsAndTickOffsetsWhenItsEndpointChanges() {
		EntityPositionSyncWirePacket packet = roundTrip(EntityPositionSyncWirePacket.STREAM_CODEC, buffer ->
			buffer.writeByte(42).writeByte(1).writeByte(2)
				.writeDouble(1).writeDouble(2).writeDouble(3).writeByte(2)
				.writeDouble(4).writeDouble(5).writeDouble(6).writeByte(5)
				.writeFloat(90).writeFloat(45).writeBoolean(true));
		assertTrue(packet.path().stepped());
		assertEquals(new Position(4, 5, 6), packet.position());
		assertEquals(2, packet.path().steps().get(0).tickOffset());
		assertEquals(5, packet.path().steps().get(1).tickOffset());
		packet.setPosition(new Position(7, 8, 9));
		assertEncoding(EntityPositionSyncWirePacket.STREAM_CODEC, packet, buffer ->
			buffer.writeByte(42).writeByte(1).writeByte(2)
				.writeDouble(1).writeDouble(2).writeDouble(3).writeByte(2)
				.writeDouble(7).writeDouble(8).writeDouble(9).writeByte(5)
				.writeFloat(90).writeFloat(45).writeBoolean(true));
	}

	@Test
	void malformedPositionPathsAreRejected() {
		for (byte[] bytes : new byte[][] {{2}, {1, 0}, {1, 127}, {0, 0}}) {
			ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
			try {
				assertThrows(RuntimeException.class, () -> EntityPositionPath.STREAM_CODEC.decode(buffer));
			} finally {
				buffer.release();
			}
		}
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, ByteBuf, T> codec, Consumer<ByteBuf> writer) {
		ByteBuf input = Unpooled.buffer();
		try {
			writer.accept(input);
			T packet = codec.decode(input);
			assertFalse(input.isReadable());
			assertEncoding(codec, packet, writer);
			return packet;
		} finally {
			input.release();
		}
	}

	private static <T> void assertEncoding(
		StreamCodec<ByteBuf, ByteBuf, T> codec, T packet, Consumer<ByteBuf> writer
	) {
		ByteBuf actual = Unpooled.buffer();
		ByteBuf expected = Unpooled.buffer();
		try {
			writer.accept(expected);
			codec.encode(actual, packet);
			assertEquals(expected, actual);
		} finally {
			actual.release();
			expected.release();
		}
	}
}
