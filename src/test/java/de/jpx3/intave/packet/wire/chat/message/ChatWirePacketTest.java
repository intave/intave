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

package de.jpx3.intave.packet.wire.chat.message;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.contract.Packet;
import de.jpx3.intave.packet.wire.WirePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.BitSet;
import java.util.UUID;
import java.util.function.Consumer;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.decodePayload;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.encodePayload;
import static org.junit.jupiter.api.Assertions.*;

final class ChatWirePacketTest {
	private static final long TIMESTAMP = 1723456789123L;
	private static final long SALT = -1234567890123L;

	@Test
	void unsignedChatKeepsItsVersionedTextLimits() {
		for (int protocol : new int[] {47, 210, 315, 758}) {
			int limit = protocol < 315 ? 100 : 256;
			ChatUnsignedWirePacket packet = roundTrip(protocol, buffer -> text(buffer, "a".repeat(limit)));
			assertEquals(protocol < 315 ? ChatShortMessageWirePacket.class : ChatUnsignedWirePacket.class, packet.getClass());
			assertEquals(limit, packet.text().length());
			packet.setText("a".repeat(limit + 1));
			ByteBuf output = Unpooled.buffer();
			try {
				assertThrows(IllegalArgumentException.class, () -> encodePayload(packet, output));
				text(output, packet.text());
				assertThrows(IllegalArgumentException.class, () -> decodePayload(packet.getClass(), output));
			} finally {
				output.release();
			}
		}
	}

	@Test
	void originalSignedChatDecodesItsPreviewAndLengthPrefixedSignature() {
		// https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.19/protocol.json
		for (int signatureLength : new int[] {0, 256}) {
			byte[] signature = signature(signatureLength);
			ChatWithPreviewWirePacket packet = (ChatWithPreviewWirePacket) roundTrip(759, buffer -> {
				header(buffer, "hello");
				bytes(buffer, signature);
				buffer.writeBoolean(true);
			});
			assertEquals(ChatWithPreviewWirePacket.class, packet.getClass());
			assertEquals(Instant.ofEpochMilli(TIMESTAMP), packet.timestamp());
			assertEquals(SALT, packet.salt());
			assertArrayEquals(signature, packet.signature());
			assertTrue(packet.signedPreview());
			packet.setText("edited");
			assertEncoding(packet, buffer -> {
				header(buffer, "edited");
				bytes(buffer, signature);
				buffer.writeBoolean(true);
			});
		}
	}

	@Test
	void lastSeenChatDecodesEachSenderSignatureAndOptionalLastReceivedMessage() {
		// https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.19.2/protocol.json
		for (boolean hasLastReceived : new boolean[] {false, true}) {
			Consumer<ByteBuf> seen = buffer -> {
				buffer.writeByte(2);
				buffer.writeLong(1).writeLong(2);
				bytes(buffer, signature(256));
				buffer.writeLong(3).writeLong(4);
				bytes(buffer, signature(256));
				buffer.writeBoolean(hasLastReceived);
				if (hasLastReceived) {
					buffer.writeLong(5).writeLong(6);
					bytes(buffer, signature(256));
				}
			};
			ChatWithLastSeenWirePacket packet = (ChatWithLastSeenWirePacket) roundTrip(760, buffer -> {
				header(buffer, "hello");
				bytes(buffer, signature(256));
				buffer.writeBoolean(false);
				seen.accept(buffer);
			});
			assertEquals(2, packet.lastSeen().size());
			assertEquals(new UUID(1, 2), packet.lastSeen().get(0).sender());
			assertEquals(new UUID(3, 4), packet.lastSeen().get(1).sender());
			assertArrayEquals(signature(256), packet.lastSeen().get(1).signature());
			if (hasLastReceived) {
				assertEquals(new UUID(5, 6), packet.lastReceived().sender());
				assertArrayEquals(signature(256), packet.lastReceived().signature());
			} else assertNull(packet.lastReceived());
			packet.setText("edited");
			assertEncoding(packet, buffer -> {
				header(buffer, "edited");
				bytes(buffer, signature(256));
				buffer.writeBoolean(false);
				seen.accept(buffer);
			});
		}
	}

	@Test
	void modernChatDecodesOptionalFixedSignaturesAcknowledgementsAndVersionedChecksums() {
		for (int protocol : new int[] {761, 762, 769, 770, 774, 775, 777}) {
			for (boolean signed : new boolean[] {false, true}) {
				Consumer<ByteBuf> signedFields = buffer -> {
					buffer.writeBoolean(signed);
					if (signed) buffer.writeBytes(signature(256));
					buffer.writeByte(0xac).writeByte(2); // offset 300
					buffer.writeByte(5).writeByte(0x80).writeByte(8); // bits 0, 2, 15 and 19
					if (protocol >= 770) buffer.writeByte(0xfe);
				};
				ChatWithoutChecksumWirePacket packet = (ChatWithoutChecksumWirePacket) roundTrip(protocol, buffer -> {
					header(buffer, "hello");
					signedFields.accept(buffer);
				});
				assertEquals(protocol >= 770 ? ChatWirePacket.class : ChatWithoutChecksumWirePacket.class, packet.getClass());
				assertEquals(Instant.ofEpochMilli(TIMESTAMP), packet.timestamp());
				assertEquals(SALT, packet.salt());
				assertArrayEquals(signed ? signature(256) : null, packet.signature());
				assertEquals(300, packet.offset());
				assertEquals(BitSet.valueOf(new byte[] {5, (byte) 0x80, 8}), packet.acknowledged());
				if (protocol >= 770) assertEquals((byte) 0xfe, ((ChatWirePacket) packet).checksum());
				packet.setText("edited");
				assertEncoding(packet, buffer -> {
					header(buffer, "edited");
					signedFields.accept(buffer);
				});
			}
		}
	}

	@Test
	void truncatedSignaturesAndAcknowledgementsAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			header(input, "hello");
			input.writeBoolean(true).writeZero(255);
			assertThrows(IllegalArgumentException.class, () -> ChatWirePacket.STREAM_CODEC.decode(input));
			header(input.clear(), "hello");
			input.writeBoolean(false).writeByte(0).writeZero(2);
			assertThrows(IllegalArgumentException.class, () -> ChatWithoutChecksumWirePacket.STREAM_CODEC.decode(input));
			header(input.clear(), "hello");
			input.writeBoolean(false).writeByte(0).writeZero(3);
			assertThrows(IndexOutOfBoundsException.class, () -> ChatWirePacket.STREAM_CODEC.decode(input));
			header(input.clear(), "hello");
			input.writeByte(0x80).writeByte(2).writeZero(255);
			assertThrows(IllegalArgumentException.class, () -> ChatWithPreviewWirePacket.STREAM_CODEC.decode(input));
		} finally {
			input.release();
		}
	}

	@Test
	void invalidFixedSignaturesAndAcknowledgementBitsCannotBeEncoded() {
		ChatWirePacket packet = new ChatWirePacket();
		ByteBuf output = Unpooled.buffer();
		try {
			for (int length : new int[] {0, 255, 257}) {
				packet.setSignature(new byte[length]);
				assertThrows(IllegalArgumentException.class, () -> ChatWirePacket.STREAM_CODEC.encode(output.clear(), packet));
			}
			packet.setSignature(null);
			packet.acknowledged().set(20);
			assertThrows(IllegalArgumentException.class, () -> ChatWirePacket.STREAM_CODEC.encode(output.clear(), packet));
		} finally {
			output.release();
		}
	}

	private static ChatUnsignedWirePacket roundTrip(int protocol, Consumer<ByteBuf> writer) {
		Packet prototype = WirePackets.createPacket(PLAY, "CHAT", protocol);
		ByteBuf input = Unpooled.buffer();
		try {
			writer.accept(input);
			int payloadLength = input.writerIndex();
			input.writeByte(0x55);
			ChatUnsignedWirePacket packet = (ChatUnsignedWirePacket) decodePayload(prototype.getClass(), input);
			assertEquals(payloadLength, input.readerIndex(), "CHAT in " + protocol + " consumed the wrong number of bytes");
			assertEquals(0x55, input.readUnsignedByte());
			assertEncoding(packet, writer);
			return packet;
		} finally {
			input.release();
		}
	}

	private static void assertEncoding(Packet packet, Consumer<ByteBuf> writer) {
		ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
		try {
			writer.accept(expected);
			encodePayload(packet, actual);
			assertEquals(expected, actual);
		} finally {
			expected.release();
			actual.release();
		}
	}

	private static void header(ByteBuf buffer, String text) {
		text(buffer, text);
		buffer.writeLong(TIMESTAMP).writeLong(SALT);
	}

	private static void text(ByteBuf buffer, String text) {
		bytes(buffer, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void bytes(ByteBuf buffer, byte[] bytes) {
		ByteBufStreamCodecs.VARINT.encode(buffer, bytes.length);
		buffer.writeBytes(bytes);
	}

	private static byte[] signature(int length) {
		byte[] bytes = new byte[length];
		for (int i = 0; i < length; i++) bytes[i] = (byte) i;
		return bytes;
	}
}
