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

package de.jpx3.intave.packet.injection;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class CompressionRescue {
	private static final int MAX_PACKET_BYTES = 8 * 1024 * 1024;
	private static final int MAX_COMPRESSED_BYTES = MAX_PACKET_BYTES + 64 * 1024;

	private CompressionRescue() {
	}

	static ByteBuf copyPacket(ByteBuf envelope) {
		ByteBuf input = envelope.duplicate();
		int uncompressedLength = ByteBufStreamCodecs.VARINT.decode(input);
		if (uncompressedLength < 0 || uncompressedLength > MAX_PACKET_BYTES) {
			throw new IllegalArgumentException("Invalid uncompressed packet length: " + uncompressedLength);
		}
		return uncompressedLength == 0
			? copyUncompressedPacket(input)
			: inflatePacket(input, uncompressedLength);
	}

	private static ByteBuf copyUncompressedPacket(ByteBuf input) {
		int packetLength = input.readableBytes();
		if (packetLength == 0 || packetLength > MAX_PACKET_BYTES) {
			throw new IllegalArgumentException("Invalid packet length: " + packetLength);
		}

		byte[] packet = new byte[packetLength];
		input.readBytes(packet);
		return Unpooled.wrappedBuffer(packet);
	}

	private static ByteBuf inflatePacket(ByteBuf input, int uncompressedLength) {
		int compressedLength = input.readableBytes();
		if (compressedLength == 0 || compressedLength > MAX_COMPRESSED_BYTES) {
			throw new IllegalArgumentException("Invalid compressed packet length: " + compressedLength);
		}

		byte[] compressed = new byte[compressedLength];
		input.readBytes(compressed);
		byte[] packet = new byte[uncompressedLength];
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(compressed);
			int written = 0;
			while (written < packet.length && !inflater.finished()) {
				int inflated = inflater.inflate(packet, written, packet.length - written);
				if (inflated == 0) {
					break;
				}
				written += inflated;
			}

			if (written != packet.length) {
				throw new IllegalArgumentException(
					"Compressed packet produced " + written + " bytes instead of " + packet.length
				);
			}
			if (!inflater.finished()) {
				byte[] extraByte = new byte[1];
				if (inflater.inflate(extraByte) != 0 || !inflater.finished()) {
					throw new IllegalArgumentException("Compressed packet exceeds its declared length");
				}
			}
			if (inflater.getRemaining() != 0) {
				throw new IllegalArgumentException("Compressed packet has trailing bytes");
			}
			return Unpooled.wrappedBuffer(packet);
		} catch (DataFormatException failure) {
			throw new IllegalArgumentException("Invalid compressed packet data", failure);
		} finally {
			inflater.end();
		}
	}
}
