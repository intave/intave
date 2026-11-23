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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.immutable.SignUpdatePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SignUpdateWirePacket {
	private SignUpdateWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class, Protocol477.class, Protocol763.class, Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements SignUpdatePacket {
		// Retain the wire text so the tracker can reject oversized updates before server decoding.
		protected static final StreamCodec<ByteBuf, ByteBuf, List<String>> LINES = StreamCodec.of((output, lines) -> {
			if (lines.size() != 4) {
				throw new IllegalArgumentException("A sign update must contain four lines");
			}
			for (String line : lines) {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, line);
			}
		}, input -> {
			List<String> lines = new ArrayList<>(4);
			for (int i = 0; i < 4; i++) {
				lines.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
			}
			return lines;
		});

		private final BlockPosition position;
		private final List<String> lines;
		private final boolean frontText;

		Base(BlockPosition position, boolean frontText, List<String> lines) {
			this.position = new BlockPosition(position);
			this.frontText = frontText;
			this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
		}

		@Override
		public BlockPosition blockPosition() {
			return new BlockPosition(position);
		}

		@Override
		public List<String> lines() {
			return lines;
		}

		@Override
		public boolean frontText() {
			return frontText;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = StreamCodec.compound(
			BlockPosition.LEGACY_STREAM_CODEC, Base::blockPosition,
			LINES, Base::lines,
			Protocol47::new
		);

		Protocol47() {
			this(BlockPosition.ORIGIN, Arrays.asList("", "", "", ""));
		}

		Protocol47(BlockPosition position, List<String> lines) {
			super(position, true, lines);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 47 && protocol < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = StreamCodec.compound(
			BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
			LINES, Base::lines,
			Protocol477::new
		);

		Protocol477() {
			this(BlockPosition.ORIGIN, Arrays.asList("", "", "", ""));
		}

		Protocol477(BlockPosition position, List<String> lines) {
			super(position, true, lines);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 477 && protocol < 763;
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = StreamCodec.compound(
			BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
			ByteBufStreamCodecs.BOOLEAN, Base::frontText,
			LINES, Base::lines,
			Protocol763::new
		);

		Protocol763() {
			this(BlockPosition.ORIGIN, true, Arrays.asList("", "", "", ""));
		}

		Protocol763(BlockPosition position, boolean frontText, List<String> lines) {
			super(position, frontText, lines);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 763 && protocol < 777;
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC = StreamCodec.compound(
			BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
			LINES, Base::lines,
			ByteBufStreamCodecs.VARINT, Protocol777::textSlot,
			Protocol777::new
		);

		private final int textSlot;

		Protocol777() {
			this(BlockPosition.ORIGIN, Arrays.asList("", "", "", ""), 1);
		}

		Protocol777(BlockPosition position, List<String> lines, int textSlot) {
			super(position, textSlot == 1, lines);
			this.textSlot = textSlot;
		}

		int textSlot() {
			return textSlot;
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 777;
		}
	}
}
