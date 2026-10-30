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

package de.jpx3.intave.packet.wire.world.explosion;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class ExplosionWirePacketCodecsTest {
	@Test
	void explosionsWithIntBlockCountsDecodeEveryField() {
		for (int protocol : new int[] {47, 754}) {
			ExplosionWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"EXPLOSION",
							protocol,
							b -> {
								b.writeFloat(-10.25f)
										.writeFloat(64.5f)
										.writeFloat(2.75f)
										.writeFloat(4);
								b.writeInt(2);
								b.writeByte(-128).writeByte(0).writeByte(127);
								b.writeByte(3).writeByte(-1).writeByte(-4);
								b.writeFloat(0.125f).writeFloat(-0.25f).writeFloat(0.5f);
							});
			assertInstanceOf(ExplosionIntBlockCountWirePacket.class, packet);
			assertEquals(-10.25, packet.x());
			assertEquals(64.5, packet.y());
			assertEquals(2.75, packet.z());
			assertEquals(4, packet.power());
			assertEquals(
					List.of(new BlockPosition(-128, 0, 127), new BlockPosition(3, -1, -4)),
					packet.affectedBlockOffsets());
			assertEquals(0.125, packet.motion().motionX);
			assertEquals(-0.25, packet.motion().motionY);
			assertEquals(0.5, packet.motion().motionZ);
		}
	}

	@Test
	void explosionsWithVarIntBlockCountsDecodeMultibyteCounts() {
		for (int protocol : new int[] {755, 760}) {
			ExplosionWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"EXPLOSION",
							protocol,
							b -> {
								b.writeFloat(-10.25f)
										.writeFloat(64.5f)
										.writeFloat(2.75f)
										.writeFloat(4);
								b.writeByte(0x80).writeByte(1);
								for (int i = 0; i < 128; i++)
									b.writeByte(i - 128).writeByte(0).writeByte(127);
								b.writeFloat(0.125f).writeFloat(-0.25f).writeFloat(0.5f);
							});
			assertInstanceOf(ExplosionVarIntBlockCountWirePacket.class, packet);
			assertEquals(-10.25, packet.x());
			assertEquals(64.5, packet.y());
			assertEquals(2.75, packet.z());
			assertEquals(4, packet.power());
			assertEquals(128, packet.affectedBlockOffsets().size());
			assertEquals(new BlockPosition(-128, 0, 127), packet.affectedBlockOffsets().getFirst());
			assertEquals(new BlockPosition(-1, 0, 127), packet.affectedBlockOffsets().getLast());
			assertEquals(0.125, packet.motion().motionX);
			assertEquals(-0.25, packet.motion().motionY);
			assertEquals(0.5, packet.motion().motionZ);
		}
	}

	@Test
	void explosionsWithDoublePositionsRetainTheirPrecision() {
		for (int protocol : new int[] {761, 764}) {
			ExplosionWirePacket packet =
					roundTrip(
							CLIENTBOUND,
							"EXPLOSION",
							protocol,
							b -> {
								b.writeDouble(-10.2500000001)
										.writeDouble(64.5000000001)
										.writeDouble(2.7500000001)
										.writeFloat(4);
								b.writeByte(1).writeByte(-128).writeByte(0).writeByte(127);
								b.writeFloat(0.125f).writeFloat(-0.25f).writeFloat(0.5f);
							});
			assertInstanceOf(ExplosionDoublePositionWirePacket.class, packet);
			assertEquals(-10.2500000001, packet.x());
			assertEquals(64.5000000001, packet.y());
			assertEquals(2.7500000001, packet.z());
			assertEquals(4, packet.power());
			assertEquals(List.of(new BlockPosition(-128, 0, 127)), packet.affectedBlockOffsets());
			assertEquals(0.125, packet.motion().motionX);
			assertEquals(-0.25, packet.motion().motionY);
			assertEquals(0.5, packet.motion().motionZ);
		}
	}

	@Test
	void explosionsCanBeEncodedFromExplicitFields() {
		for (int protocol : new int[] {47, 755, 761}) {
			int id = registry(CLIENTBOUND).idOf(PLAY, "EXPLOSION", protocol);
			ExplosionWirePacket packet =
					(ExplosionWirePacket) WirePackets.createPacket(CLIENTBOUND, PLAY, id, protocol);
			packet.setPosition(-10.25, 64.5, 2.75);
			packet.setPower(4);
			packet.setAffectedBlockOffsets(List.of(new BlockPosition(-128, 0, 127)));
			packet.setMotion(new Motion(0.125, -0.25, 0.5));
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(CLIENTBOUND, PLAY, "EXPLOSION", protocol, packet, output);
				assertEquals(id, ByteBufStreamCodecs.VARINT.decode(output));
				if (protocol < 761) {
					assertEquals(-10.25f, output.readFloat());
					assertEquals(64.5f, output.readFloat());
					assertEquals(2.75f, output.readFloat());
				} else {
					assertEquals(-10.25, output.readDouble());
					assertEquals(64.5, output.readDouble());
					assertEquals(2.75, output.readDouble());
				}
				assertEquals(4, output.readFloat());
				assertEquals(
						1,
						protocol < 755
								? output.readInt()
								: ByteBufStreamCodecs.VARINT.decode(output));
				assertEquals(-128, output.readByte());
				assertEquals(0, output.readByte());
				assertEquals(127, output.readByte());
				assertEquals(0.125f, output.readFloat());
				assertEquals(-0.25f, output.readFloat());
				assertEquals(0.5f, output.readFloat());
				assertFalse(output.isReadable());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void malformedExplosionCountsAreRejectedWithoutConsumingTheFrame() {
		for (int protocol : new int[] {47, 755, 761}) {
			for (int count : new int[] {-1, 5, Integer.MAX_VALUE}) {
				ByteBuf input = Unpooled.buffer();
				try {
					ByteBufStreamCodecs.VARINT.encode(
							input, registry(CLIENTBOUND).idOf(PLAY, "EXPLOSION", protocol));
					input.writeZero(protocol < 761 ? 16 : 28);
					if (protocol < 755) input.writeInt(count);
					else ByteBufStreamCodecs.VARINT.encode(input, count);
					input.writeZero(3 + 12); // One block offset and the knockback vector.
					assertThrows(
							IllegalArgumentException.class,
							() -> WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, input));
					assertEquals(0, input.readerIndex());
				} finally {
					input.release();
				}
			}
		}
	}

	@Test
	void explosionBlockOffsetsMustFitSignedBytes() {
		for (int protocol : new int[] {47, 755, 761}) {
			int id = registry(CLIENTBOUND).idOf(PLAY, "EXPLOSION", protocol);
			ExplosionWirePacket packet =
					(ExplosionWirePacket) WirePackets.createPacket(CLIENTBOUND, PLAY, id, protocol);
			for (BlockPosition offset :
					List.of(
							new BlockPosition(128, 0, 0),
							new BlockPosition(0, -129, 0),
							new BlockPosition(0, 0, 256))) {
				packet.setAffectedBlockOffsets(List.of(offset));
				ByteBuf output = Unpooled.buffer().writeByte(0x55);
				try {
					assertThrows(
							IllegalArgumentException.class,
							() ->
									WirePackets.encodePacket(
											CLIENTBOUND,
											PLAY,
											"EXPLOSION",
											protocol,
											packet,
											output));
					assertEquals(1, output.writerIndex());
					assertEquals(0x55, output.getUnsignedByte(0));
				} finally {
					output.release();
				}
			}
		}
	}
}
