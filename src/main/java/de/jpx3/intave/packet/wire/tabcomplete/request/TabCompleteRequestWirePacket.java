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

package de.jpx3.intave.packet.wire.tabcomplete.request;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableTabCompleteRequestPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TabCompleteRequestWirePacket {
	private TabCompleteRequestWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol393.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableTabCompleteRequestPacket {
		protected String text;
		protected int transactionId;

		protected Base() {
			this(0, "");
		}

		protected Base(int transactionId, String text) {
			this.transactionId = transactionId;
			this.text = text;
		}

		@Override
		public String text() {
			return this.text;
		}

		@Override
		public void setText(String value) {
			markMutated();
			this.text = value;
		}

		public int transactionId() {
			return transactionId;
		}
	}

	static class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT_STRING, Base::text,
				ByteBufStreamCodecs.optionalCodecOf(BlockPosition.LEGACY_STREAM_CODEC), Protocol47::target,
				Protocol47::new
			);

		protected BlockPosition target;

		public Protocol47() {
			super();
		}

		public Protocol47(String text, BlockPosition target) {
			super(0, text);
			this.target = target;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}

		public BlockPosition target() {
			return target;
		}
	}

	static final class Protocol107
		extends TabCompleteRequestWirePacket.Protocol47 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT_STRING, Base::text,
				ByteBufStreamCodecs.BOOLEAN, Protocol107::assumeCommand,
				ByteBufStreamCodecs.optionalCodecOf(BlockPosition.LEGACY_STREAM_CODEC), TabCompleteRequestWirePacket.Protocol47::target,
				Protocol107::new
			);

		private boolean assumeCommand;

		public Protocol107() {
			super();
		}

		public Protocol107(
			String text, boolean assumeCommand, BlockPosition target) {
			super(text, target);
			this.assumeCommand = assumeCommand;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 393;
		}

		public boolean assumeCommand() {
			return assumeCommand;
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol393::transactionId,
				ByteBufStreamCodecs.VARINT_STRING, Protocol393::text,
				Protocol393::new
			);

		public Protocol393() {
			super();
		}

		public Protocol393(int transactionId, String text) {
			super(transactionId, text);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393;
		}
	}
}
