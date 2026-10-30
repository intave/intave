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

package de.jpx3.intave.packet.wire.cookie.response;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.ConnectionState.*;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static org.junit.jupiter.api.Assertions.*;

final class CookieResponseWirePacketTest {
	@Test
	void optionalPayloadsKeepNullDistinctFromEmptyAndAcceptTheMaximumSize() {
		for (ConnectionState state : new ConnectionState[] {LOGIN, CONFIGURATION, PLAY}) {
			for (int protocol : new int[] {766, 777}) {
				assertInstanceOf(CookieResponseWirePacket.class, WirePackets.createPacket(state, "COOKIE_RESPONSE", protocol));
			}
		}
		for (int length : new int[] {-1, 0, 128, 5120}) {
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				ByteBufStreamCodecs.VARINT_STRING.encode(input, "minecraft:test");
				input.writeBoolean(length >= 0);
				byte[] payload = length < 0 ? null : new byte[length];
				if (payload != null) {
					for (int i = 0; i < length; i++) payload[i] = (byte) i;
					ByteBufStreamCodecs.VARINT.encode(input, length);
					input.writeBytes(payload);
				}
				int payloadEnd = input.writerIndex();
				input.writeByte(0x55);
				CookieResponseWirePacket packet = CookieResponseWirePacket.STREAM_CODEC.decode(input);
				assertEquals("minecraft:test", packet.key().fullKey());
				assertEquals(length >= 0, packet.hasPayload());
				assertArrayEquals(payload, packet.payload());
				assertEquals(payloadEnd, input.readerIndex());
				assertEquals(0x55, input.readUnsignedByte());
				CookieResponseWirePacket.STREAM_CODEC.encode(output, packet);
				assertEquals(input.readerIndex(0).writerIndex(payloadEnd), output);
			} finally {
				input.release();
				output.release();
			}
		}
	}

	@Test
	void payloadCanBeAddedAndCleared() {
		CookieResponseWirePacket packet = new CookieResponseWirePacket(MinecraftKey.withDefaultNamespace("test"), null);
		ByteBuf output = Unpooled.buffer();
		try {
			packet.setPayload(new byte[] {1, 2, 3});
			CookieResponseWirePacket.STREAM_CODEC.encode(output, packet);
			assertEquals("minecraft:test", ByteBufStreamCodecs.VARINT_STRING.decode(output));
			assertTrue(output.readBoolean());
			assertEquals(3, output.readUnsignedByte());
			assertEquals(1, output.readUnsignedByte());
			assertEquals(2, output.readUnsignedByte());
			assertEquals(3, output.readUnsignedByte());
			assertFalse(output.isReadable());
			packet.clearPayload();
			CookieResponseWirePacket.STREAM_CODEC.encode(output.clear(), packet);
			assertEquals("minecraft:test", ByteBufStreamCodecs.VARINT_STRING.decode(output));
			assertFalse(output.readBoolean());
			assertFalse(output.isReadable());
		} finally {
			output.release();
		}
	}

	@Test
	void oversizedNegativeAndTruncatedPayloadsAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			for (int length : new int[] {-1, 5121, 3}) {
				ByteBufStreamCodecs.VARINT_STRING.encode(input.clear(), "minecraft:test");
				input.writeBoolean(true);
				ByteBufStreamCodecs.VARINT.encode(input, length);
				if (length > 0) input.writeZero(length == 3 ? 2 : length);
				assertThrows(IllegalArgumentException.class, () -> CookieResponseWirePacket.STREAM_CODEC.decode(input));
			}
			CookieResponseWirePacket packet = new CookieResponseWirePacket();
			packet.setPayload(new byte[5121]);
			input.clear().writeByte(0x55);
			assertThrows(IllegalArgumentException.class,
				() -> WirePackets.encodePacket(SERVERBOUND, PLAY, "COOKIE_RESPONSE", 766, packet, input));
			assertEquals(1, input.writerIndex());
			assertEquals(0x55, input.getUnsignedByte(0));
		} finally {
			input.release();
		}
	}
}
