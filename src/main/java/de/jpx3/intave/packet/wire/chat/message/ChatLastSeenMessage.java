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

import java.util.UUID;

final class ChatLastSeenMessage {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatLastSeenMessage> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.UUID, ChatLastSeenMessage::sender,
		ByteBufStreamCodecs.VARINT_BYTES, ChatLastSeenMessage::signature,
		ChatLastSeenMessage::new
	);

	private final UUID sender;
	private final byte[] signature;

	public ChatLastSeenMessage(UUID sender, byte[] signature) {
		this.sender = sender;
		this.signature = signature;
	}

	public UUID sender() {
		return sender;
	}

	public byte[] signature() {
		return signature;
	}
}
