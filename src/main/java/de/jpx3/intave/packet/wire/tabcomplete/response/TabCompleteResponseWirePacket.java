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

package de.jpx3.intave.packet.wire.tabcomplete.response;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableTabCompleteResponsePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.count;

public final class TabCompleteResponseWirePacket {
	private TabCompleteResponseWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol393.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableTabCompleteResponsePacket {
		protected int id, start, length;
		protected List<String> suggestions;
		protected List<String> tooltips;

		protected Base() {
			this(0, 0, 0, new ArrayList<>(), new ArrayList<>());
		}

		protected Base(
			int id, int start, int length, List<String> suggestions, List<String> tooltips) {
			this.id = id;
			this.start = start;
			this.length = length;
			this.suggestions = suggestions;
			this.tooltips = tooltips;
		}

		@Override
		public List<String> suggestions() {
			markMutated();
			return Collections.unmodifiableList(suggestions);
		}

		@Override
		public void setSuggestions(List<String> value) {
			markMutated();
			suggestions = new ArrayList<>(value);
			tooltips.clear();
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.suggestions.size());
					for (int i = 0; i < packet.suggestions.size(); i++) {
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.suggestions.get(i));
					}
				},
				input -> {
					int count = count(input, input.readableBytes());
					List<String> suggestions = new ArrayList<>(count);
					List<String> tooltips = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						suggestions.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
						String tooltip = null;

						tooltips.add(tooltip);
					}
					return new Protocol47(
						suggestions, tooltips);
				});

		public Protocol47() {
			super();
		}

		public Protocol47(
			List<String> suggestions, List<String> tooltips) {
			super(0, 0, 0, suggestions, tooltips);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 393;
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.id);
					ByteBufStreamCodecs.VARINT.encode(output, packet.start);
					ByteBufStreamCodecs.VARINT.encode(output, packet.length);

					ByteBufStreamCodecs.VARINT.encode(output, packet.suggestions.size());
					for (int i = 0; i < packet.suggestions.size(); i++) {
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.suggestions.get(i));

						String tooltip =
							i < packet.tooltips.size() ? packet.tooltips.get(i) : null;
						output.writeBoolean(tooltip != null);
						if (tooltip != null) ByteBufStreamCodecs.stringCodec(262144).encode(output, tooltip);
					}
				},
				input -> {
					int id = ByteBufStreamCodecs.VARINT.decode(input);
					int start = ByteBufStreamCodecs.VARINT.decode(input);
					int length = ByteBufStreamCodecs.VARINT.decode(input);

					int count = count(input, input.readableBytes());
					List<String> suggestions = new ArrayList<>(count);
					List<String> tooltips = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						suggestions.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
						String tooltip = null;
						if ((input.readBoolean())) {
							tooltip = ByteBufStreamCodecs.stringCodec(262144).decode(input);
						}
						tooltips.add(tooltip);
					}
					return new Protocol393(
						id, start, length, suggestions, tooltips);
				});

		public Protocol393() {
			super();
		}

		public Protocol393(
			int id, int start, int length, List<String> suggestions, List<String> tooltips) {
			super(id, start, length, suggestions, tooltips);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393 && protocolVersion < 765;
		}
	}
}
