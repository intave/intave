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
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

class ModernExplosionWirePacketTest {
	@Test
	void legacyMotionWithEffectsPreservesTheEntireTailWhenEdited() {
		for (int protocol : new int[]{765, 766, 767}) {
			byte[] effects = {0, 4, 4, 0, 3, 97, 98, 99, 0};
			ExplosionWithEffectsWirePacket packet = roundTrip(CLIENTBOUND, PacketType.Server.EXPLOSION, protocol, output -> {
				output.writeDouble(1.25).writeDouble(64.5).writeDouble(-3.75).writeFloat(4);
				output.writeByte(2).writeByte(-1).writeByte(2).writeByte(3).writeByte(4).writeByte(-5).writeByte(6);
				output.writeFloat(0.125f).writeFloat(-0.25f).writeFloat(0.5f);
				output.writeBytes(effects);
			});
			assertEquals(2, packet.affectedBlockOffsets().size());
			assertEquals(-0.25, packet.motion().motionY());
			packet.setMotion(new Motion(1, 2, 3));
			ByteBuf encoded = Unpooled.buffer();
			try {
				WirePackets.encodePacket(PLAY, PacketType.Server.EXPLOSION, protocol, packet, encoded);
				ByteBufStreamCodecs.VARINT.decode(encoded);
				encoded.skipBytes(24 + 4 + 1 + 6);
				assertEquals(1, encoded.readFloat());
				assertEquals(2, encoded.readFloat());
				assertEquals(3, encoded.readFloat());
				byte[] actual = new byte[encoded.readableBytes()];
				encoded.readBytes(actual);
				assertArrayEquals(effects, actual);
			} finally {
				encoded.release();
			}
		}
	}

	@Test
	void defaultEffectsRespectTheDirectSoundToHolderBoundary() {
		for (int protocol : new int[]{765, 766, 767}) {
			ExplosionWirePacket packet = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, protocol);
			packet.setDefaultEffects(protocol);
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(PLAY, PacketType.Server.EXPLOSION, protocol, packet, output);
				ByteBufStreamCodecs.VARINT.decode(output);
				output.skipBytes(24 + 4 + 1 + 12);
				assertEquals(0, ByteBufStreamCodecs.VARINT.decode(output));
				assertEquals(protocol == 765 ? 5 : 4, ByteBufStreamCodecs.VARINT.decode(output));
				assertEquals(protocol == 765 ? 5 : 4, ByteBufStreamCodecs.VARINT.decode(output));
				if (protocol >= 766) assertEquals(0, ByteBufStreamCodecs.VARINT.decode(output));
				assertEquals("minecraft:entity.generic.explode", ByteBufStreamCodecs.VARINT_STRING.decode(output));
				assertFalse(output.readBoolean());
				assertFalse(output.isReadable());
			} finally {
				output.release();
			}
		}
	}
	private static ExplosionOptionalMotionWirePacket cloud(int protocol, double x, double y, double z, float radius, Motion motion) {
		ExplosionOptionalMotionWirePacket packet = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, protocol);
		packet.setDefaultEffects(protocol);
		packet.setPosition(x, y, z);
		packet.setPower(radius);
		packet.setMotion(motion);
		return packet;
	}

	@Test
	void cloudExplosionMatchesClientFieldOrderAndRegistryIds() {
		for (int protocol : new int[]{768, 769, 770, 771, 772, 773, 774, 775, 776, 777}) {
			for (Motion motion : new Motion[]{null, new Motion(0.125, -1.25, 12.5)}) {
				ByteBuf encoded = Unpooled.buffer();
				try {
					WirePackets.encodePacket(PLAY, PacketType.Server.EXPLOSION, protocol,
						cloud(protocol, -1.25, 64.000000001, 7.5, 4, motion), encoded);
					ExplosionOptionalMotionWirePacket decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, encoded);
					assertEquals(motion != null, decoded.hasMotion());
					if (motion != null) assertEquals(-1.25, decoded.motion().motionY());
					ByteBufStreamCodecs.VARINT.decode(encoded);
					assertEquals(-1.25, encoded.readDouble());
					assertEquals(64.000000001, encoded.readDouble());
					assertEquals(7.5, encoded.readDouble());
					if (protocol >= 773) {
						assertEquals(4, encoded.readFloat());
						assertEquals(0, encoded.readInt());
					}
					assertEquals(motion != null, encoded.readBoolean());
					if (motion != null) {
						assertEquals(0.125, encoded.readDouble());
						assertEquals(-1.25, encoded.readDouble());
						assertEquals(12.5, encoded.readDouble());
					}
					assertEquals(protocol >= 776 ? 11 : 4, ByteBufStreamCodecs.VARINT.decode(encoded));
					assertEquals(0, ByteBufStreamCodecs.VARINT.decode(encoded));
					assertEquals("minecraft:entity.generic.explode", ByteBufStreamCodecs.VARINT_STRING.decode(encoded));
					assertFalse(encoded.readBoolean());
					if (protocol >= 773) assertEquals(0, ByteBufStreamCodecs.VARINT.decode(encoded));
					if (protocol >= 777) assertTrue(encoded.readBoolean());
					assertFalse(encoded.isReadable());
				} finally {
					encoded.release();
				}
			}
		}
	}

	@Test
	void forwardedExplosionsPreserveUninterpretedEffectData() {
		for (int protocol : new int[]{768, 773, 777}) {
			ExplosionOptionalMotionWirePacket packet = roundTrip(CLIENTBOUND, PacketType.Server.EXPLOSION, protocol, output -> {
				output.writeDouble(1).writeDouble(2).writeDouble(3);
				if (protocol >= 773) output.writeFloat(7).writeInt(123);
				output.writeBoolean(true).writeDouble(0.125).writeDouble(-1.5).writeDouble(8);
				output.writeBytes(new byte[]{4, 0, 3, 97, 98, 99, 0, 0, 1});
			});
			assertEquals(1, packet.x());
			assertEquals(-1.5, packet.motion().motionY());
		}
	}

	@Test
	void absentMotionCanBeAddedAndRemovedWithoutLosingEffects() {
		ExplosionOptionalMotionWirePacket packet = cloud(777, 1, 2, 3, 4, null);
		packet.setMotion(new Motion(1, 2, 3));
		assertTrue(packet.hasMotion());
		packet.setMotion(null);
		assertFalse(packet.hasMotion());
		assertThrows(IllegalArgumentException.class, () -> packet.setDefaultEffects(767));
		assertThrows(IllegalArgumentException.class, () -> packet.setDefaultEffects(778));
	}
}
