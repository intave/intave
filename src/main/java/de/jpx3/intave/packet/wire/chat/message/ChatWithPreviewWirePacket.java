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

public class ChatWithPreviewWirePacket extends ChatUnsignedWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatWithPreviewWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(256), ChatUnsignedWirePacket::text,
			ByteBufStreamCodecs.INSTANT, ChatWithPreviewWirePacket::timestamp,
			ByteBufStreamCodecs.LONG, ChatWithPreviewWirePacket::salt,
			ByteBufStreamCodecs.VARINT_BYTES, ChatWithPreviewWirePacket::signature,
			ByteBufStreamCodecs.BOOLEAN, ChatWithPreviewWirePacket::signedPreview,
			ChatWithPreviewWirePacket::new
		);

	private Instant timestamp;
	private long salt;
	private byte[] signature;
	private boolean signedPreview;

	public ChatWithPreviewWirePacket() {
		this("", Instant.EPOCH, 0, new byte[0], false);
	}

	public ChatWithPreviewWirePacket(String text, Instant timestamp, long salt, byte[] signature, boolean signedPreview) {
		super(text);
		this.timestamp = timestamp;
		this.salt = salt;
		this.signature = signature;
		this.signedPreview = signedPreview;
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

	public boolean signedPreview() {
		return signedPreview;
	}

	public void setSignedPreview(boolean value) {
		signedPreview = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion == 759;
	}
}
