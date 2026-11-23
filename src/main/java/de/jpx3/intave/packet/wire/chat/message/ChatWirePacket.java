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
import de.jpx3.intave.packet.contract.mutable.MutableChatPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public final class ChatWirePacket {
	private ChatWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol315.class,
			Protocol759.class,
			Protocol760.class,
			Protocol761.class,
			Protocol770.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableChatPacket {
		protected String text;

		protected Base() {
			this("");
		}

		protected Base(String text) {
			this.text = text;
		}

		@Override
		public String text() {
			return text;
		}

		@Override
		public void setText(String value) {
			markMutated();
			text = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(100), Base::text,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(String text) {
			super(text);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 315;
		}
	}

	static final class Protocol315 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol315> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(256), Protocol315::text,
				Protocol315::new
			);

		public Protocol315() {
			super();
		}

		public Protocol315(String text) {
			super(text);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 315 && protocolVersion < 759;
		}
	}

	static class Protocol759 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(256), Base::text,
				ByteBufStreamCodecs.INSTANT, Protocol759::timestamp,
				ByteBufStreamCodecs.LONG, Protocol759::salt,
				ByteBufStreamCodecs.VARINT_BYTES, Protocol759::signature,
				ByteBufStreamCodecs.BOOLEAN, Protocol759::signedPreview,
				Protocol759::new
			);

		private Instant timestamp;
		private long salt;
		private byte[] signature;
		private boolean signedPreview;

		public Protocol759() {
			this("", Instant.EPOCH, 0, new byte[0], false);
		}

		public Protocol759(String text, Instant timestamp, long salt, byte[] signature, boolean signedPreview) {
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
			markMutated();
			timestamp = value;
		}

		public long salt() {
			return salt;
		}

		public void setSalt(long value) {
			markMutated();
			salt = value;
		}

		public byte[] signature() {
			markMutated();
			return signature;
		}

		public void setSignature(byte[] value) {
			markMutated();
			signature = value;
		}

		public boolean signedPreview() {
			return signedPreview;
		}

		public void setSignedPreview(boolean value) {
			markMutated();
			signedPreview = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion == 759;
		}
	}

	static final class Protocol760 extends ChatWirePacket.Protocol759 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol760> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(256), Base::text,
				ByteBufStreamCodecs.INSTANT, ChatWirePacket.Protocol759::timestamp,
				ByteBufStreamCodecs.LONG, ChatWirePacket.Protocol759::salt,
				ByteBufStreamCodecs.VARINT_BYTES, ChatWirePacket.Protocol759::signature,
				ByteBufStreamCodecs.BOOLEAN, ChatWirePacket.Protocol759::signedPreview,
				ChatLastSeenMessage.STREAM_CODEC.toListCodec(ByteBufStreamCodecs.VARINT), Protocol760::lastSeen,
				ByteBufStreamCodecs.optionalCodecOf(ChatLastSeenMessage.STREAM_CODEC), Protocol760::lastReceived,
				Protocol760::new
			);

		private List<ChatLastSeenMessage> lastSeen;
		private ChatLastSeenMessage lastReceived;

		public Protocol760() {
			this("", Instant.EPOCH, 0, new byte[0], false, Collections.emptyList(), null);
		}

		public Protocol760(
			String text, Instant timestamp, long salt, byte[] signature, boolean signedPreview,
			List<ChatLastSeenMessage> lastSeen, ChatLastSeenMessage lastReceived
		) {
			super(text, timestamp, salt, signature, signedPreview);
			this.lastSeen = lastSeen;
			this.lastReceived = lastReceived;
		}

		public List<ChatLastSeenMessage> lastSeen() {
			markMutated();
			return lastSeen;
		}

		public void setLastSeen(List<ChatLastSeenMessage> value) {
			markMutated();
			lastSeen = value;
		}

		public ChatLastSeenMessage lastReceived() {
			markMutated();
			return lastReceived;
		}

		public void setLastReceived(ChatLastSeenMessage value) {
			markMutated();
			lastReceived = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion == 760;
		}
	}

	static class Protocol761 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(256), Base::text,
				ByteBufStreamCodecs.INSTANT, Protocol761::timestamp,
				ByteBufStreamCodecs.LONG, Protocol761::salt,
				ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.fixedByteArray(256)), Protocol761::signature,
				ByteBufStreamCodecs.VARINT, Protocol761::offset,
				ByteBufStreamCodecs.fixedBitSet(20), Protocol761::acknowledged,
				Protocol761::new
			);

		private Instant timestamp;
		private long salt;
		private byte[] signature;
		private int offset;
		private BitSet acknowledged;

		public Protocol761() {
			this("", Instant.EPOCH, 0, null, 0, new BitSet());
		}

		public Protocol761(
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
			markMutated();
			timestamp = value;
		}

		public long salt() {
			return salt;
		}

		public void setSalt(long value) {
			markMutated();
			salt = value;
		}

		public byte[] signature() {
			markMutated();
			return signature;
		}

		public void setSignature(byte[] value) {
			markMutated();
			signature = value;
		}

		public int offset() {
			return offset;
		}

		public void setOffset(int value) {
			markMutated();
			offset = value;
		}

		public BitSet acknowledged() {
			markMutated();
			return acknowledged;
		}

		public void setAcknowledged(BitSet value) {
			markMutated();
			acknowledged = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 761 && protocolVersion < 770;
		}
	}

	static final class Protocol770 extends ChatWirePacket.Protocol761 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(256), Base::text,
				ByteBufStreamCodecs.INSTANT, ChatWirePacket.Protocol761::timestamp,
				ByteBufStreamCodecs.LONG, ChatWirePacket.Protocol761::salt,
				ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.fixedByteArray(256)), ChatWirePacket.Protocol761::signature,
				ByteBufStreamCodecs.VARINT, ChatWirePacket.Protocol761::offset,
				ByteBufStreamCodecs.fixedBitSet(20), ChatWirePacket.Protocol761::acknowledged,
				ByteBufStreamCodecs.BYTE, Protocol770::checksum,
				Protocol770::new
			);

		private byte checksum;

		public Protocol770() {
			super();
		}

		public Protocol770(
			String text, Instant timestamp, long salt, byte[] signature, int offset, BitSet acknowledged, byte checksum
		) {
			super(text, timestamp, salt, signature, offset, acknowledged);
			this.checksum = checksum;
		}

		public byte checksum() {
			return checksum;
		}

		public void setChecksum(byte value) {
			markMutated();
			checksum = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 770;
		}
	}
}
