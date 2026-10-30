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
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.BitSet;

public final class ChatWirePacket extends ChatWithoutChecksumWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(256), ChatUnsignedWirePacket::text,
			ByteBufStreamCodecs.INSTANT, ChatWithoutChecksumWirePacket::timestamp,
			ByteBufStreamCodecs.LONG, ChatWithoutChecksumWirePacket::salt,
			ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.fixedByteArray(256)), ChatWithoutChecksumWirePacket::signature,
			ByteBufStreamCodecs.VARINT, ChatWithoutChecksumWirePacket::offset,
			ByteBufStreamCodecs.fixedBitSet(20), ChatWithoutChecksumWirePacket::acknowledged,
			ByteBufStreamCodecs.BYTE, ChatWirePacket::checksum,
			ChatWirePacket::new
		);

	private byte checksum;

	public ChatWirePacket() {
		super();
	}

	public ChatWirePacket(
		String text, Instant timestamp, long salt, byte[] signature, int offset, BitSet acknowledged, byte checksum
	) {
		super(text, timestamp, salt, signature, offset, acknowledged);
		this.checksum = checksum;
	}

	public byte checksum() {
		return checksum;
	}

	public void setChecksum(byte value) {
		checksum = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 770;
	}
}
