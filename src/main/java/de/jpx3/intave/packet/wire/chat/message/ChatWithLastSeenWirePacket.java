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
import java.util.Collections;
import java.util.List;

public final class ChatWithLastSeenWirePacket extends ChatWithPreviewWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatWithLastSeenWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(256), ChatUnsignedWirePacket::text,
			ByteBufStreamCodecs.INSTANT, ChatWithPreviewWirePacket::timestamp,
			ByteBufStreamCodecs.LONG, ChatWithPreviewWirePacket::salt,
			ByteBufStreamCodecs.VARINT_BYTES, ChatWithPreviewWirePacket::signature,
			ByteBufStreamCodecs.BOOLEAN, ChatWithPreviewWirePacket::signedPreview,
			ChatLastSeenMessage.STREAM_CODEC.toListCodec(ByteBufStreamCodecs.VARINT), ChatWithLastSeenWirePacket::lastSeen,
			ByteBufStreamCodecs.optionalCodecOf(ChatLastSeenMessage.STREAM_CODEC), ChatWithLastSeenWirePacket::lastReceived,
			ChatWithLastSeenWirePacket::new
		);

	private List<ChatLastSeenMessage> lastSeen;
	private ChatLastSeenMessage lastReceived;

	public ChatWithLastSeenWirePacket() {
		this("", Instant.EPOCH, 0, new byte[0], false, Collections.emptyList(), null);
	}

	public ChatWithLastSeenWirePacket(
		String text, Instant timestamp, long salt, byte[] signature, boolean signedPreview,
		List<ChatLastSeenMessage> lastSeen, ChatLastSeenMessage lastReceived
	) {
		super(text, timestamp, salt, signature, signedPreview);
		this.lastSeen = lastSeen;
		this.lastReceived = lastReceived;
	}

	public List<ChatLastSeenMessage> lastSeen() {
		return lastSeen;
	}

	public void setLastSeen(List<ChatLastSeenMessage> value) {
		lastSeen = value;
	}

	public ChatLastSeenMessage lastReceived() {
		return lastReceived;
	}

	public void setLastReceived(ChatLastSeenMessage value) {
		lastReceived = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion == 760;
	}
}
