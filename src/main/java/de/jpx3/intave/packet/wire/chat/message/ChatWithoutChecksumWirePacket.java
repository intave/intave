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

public class ChatWithoutChecksumWirePacket extends ChatUnsignedWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatWithoutChecksumWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(256), ChatUnsignedWirePacket::text,
			ByteBufStreamCodecs.INSTANT, ChatWithoutChecksumWirePacket::timestamp,
			ByteBufStreamCodecs.LONG, ChatWithoutChecksumWirePacket::salt,
			ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.fixedByteArray(256)), ChatWithoutChecksumWirePacket::signature,
			ByteBufStreamCodecs.VARINT, ChatWithoutChecksumWirePacket::offset,
			ByteBufStreamCodecs.fixedBitSet(20), ChatWithoutChecksumWirePacket::acknowledged,
			ChatWithoutChecksumWirePacket::new
		);

	private Instant timestamp;
	private long salt;
	private byte[] signature;
	private int offset;
	private BitSet acknowledged;

	public ChatWithoutChecksumWirePacket() {
		this("", Instant.EPOCH, 0, null, 0, new BitSet());
	}

	public ChatWithoutChecksumWirePacket(
		String text, Instant timestamp, long salt, byte[] signature, int offset, BitSet acknowledged
	) {
		super(text);
		this.timestamp = timestamp;
		this.salt = salt;
		this.signature = signature;
		this.offset = offset;
		this.acknowledged = acknowledged;
	}

	public Instant timestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant value) {
		timestamp = value;
	}

	public long salt() {
		return salt;
	}

	public void setSalt(long value) {
		salt = value;
	}

	public byte[] signature() {
		return signature;
	}

	public void setSignature(byte[] value) {
		signature = value;
	}

	public int offset() {
		return offset;
	}

	public void setOffset(int value) {
		offset = value;
	}

	public BitSet acknowledged() {
		return acknowledged;
	}

	public void setAcknowledged(BitSet value) {
		acknowledged = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 761 && protocolVersion < 770;
	}
}
