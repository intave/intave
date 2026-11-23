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

package de.jpx3.intave.packet.wire.scoreboard;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardTeamPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChatComponent;
import de.jpx3.intave.share.ScoreboardTeamAction;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

public final class ScoreboardTeamWirePacket {
	private ScoreboardTeamWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class, Protocol107.class, Protocol393.class, Protocol765.class, Protocol770.class, Protocol776.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableScoreboardTeamPacket {
		private static final List<String> FORMATTINGS = Arrays.asList(
			"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
			"dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
			"obfuscated", "bold", "strikethrough", "underlined", "italic", "reset"
		);
		protected String name = "";
		protected ScoreboardTeamAction action = ScoreboardTeamAction.CREATE;
		protected ChatComponent display = ChatComponent.legacy("");
		protected ChatComponent prefix = ChatComponent.legacy(""), suffix = ChatComponent.legacy("");
		protected int flags, color = 21;
		protected List<String> entries = Collections.emptyList();

		static <T extends Base> StreamCodec<ByteBuf, ByteBuf, T> codec(Supplier<T> constructor) {
			return StreamCodec.of((output, packet) -> {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.name);
				output.writeByte(packet.action.id());
				if (packet.hasParameters()) packet.writeParameters(output);
				if (packet.hasEntries()) {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entries.size());
					for (String entry : packet.entries) ByteBufStreamCodecs.VARINT_STRING.encode(output, entry);
				}
			}, input -> {
				T packet = constructor.get();
				packet.name = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				packet.action = ScoreboardTeamAction.byId(input.readUnsignedByte());
				if (packet.hasParameters()) packet.readParameters(input);
				if (packet.hasEntries()) {
					int count = ByteBufStreamCodecs.VARINT.decode(input);
					if (count < 0 || count > input.readableBytes()) throw new IllegalArgumentException("Invalid team entry count " + count);
					List<String> entries = new ArrayList<>(count);
					for (int i = 0; i < count; i++) entries.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
					packet.entries = Collections.unmodifiableList(entries);
				}
				return packet;
			});
		}

		final boolean hasParameters() { return action == ScoreboardTeamAction.CREATE || action == ScoreboardTeamAction.UPDATE; }
		final boolean hasEntries() {
			return action == ScoreboardTeamAction.CREATE || action == ScoreboardTeamAction.ADD_ENTRIES || action == ScoreboardTeamAction.REMOVE_ENTRIES;
		}
		abstract void readParameters(ByteBuf input);
		abstract void writeParameters(ByteBuf output);
		int resetColor() { return 21; }

		@Override public String name() { return name; }
		@Override public ScoreboardTeamAction action() { return action; }
		@Override public String prefixJson() { return hasParameters() ? prefix.json() : null; }
		@Override public String suffixJson() { return hasParameters() ? suffix.json() : null; }
		@Override public String prefixText() { return hasParameters() ? prefix.legacyText() : null; }
		@Override public String suffixText() { return hasParameters() ? suffix.legacyText() : null; }
		@Override public String color() {
			return !hasParameters() ? null : color >= 0 && color < FORMATTINGS.size() ? FORMATTINGS.get(color) : "reset";
		}
		@Override public List<String> entries() { return hasEntries() ? entries : Collections.emptyList(); }
		@Override public void setName(String value) { markMutated(); name = Objects.requireNonNull(value); }
		@Override public void setAction(ScoreboardTeamAction value) { markMutated(); action = Objects.requireNonNull(value); }
		@Override public void setPrefixJson(String value) {
			markMutated(); prefix = value == null ? ChatComponent.legacy("") : ChatComponent.json(value);
		}
		@Override public void setSuffixJson(String value) {
			markMutated(); suffix = value == null ? ChatComponent.legacy("") : ChatComponent.json(value);
		}
		@Override public void setColor(String value) {
			int id = value == null || value.equals("reset") ? resetColor() : FORMATTINGS.indexOf(value);
			if (value != null && !value.equals("reset") && (id < 0 || id > 15)) {
				throw new IllegalArgumentException("Unknown team color " + value);
			}
			markMutated(); color = id;
		}
		@Override public void setEntries(List<String> value) {
			markMutated(); entries = Collections.unmodifiableList(new ArrayList<>(value));
		}
	}

	static class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = codec(Protocol47::new);
		protected String visibility = "always";

		Protocol47() { color = -1; }

		void readVisibility(ByteBuf input) { visibility = ByteBufStreamCodecs.VARINT_STRING.decode(input); }
		void writeVisibility(ByteBuf output) { ByteBufStreamCodecs.VARINT_STRING.encode(output, visibility); }
		@Override void readParameters(ByteBuf input) {
			display = ChatComponent.LEGACY_CODEC.decode(input);
			prefix = ChatComponent.LEGACY_CODEC.decode(input);
			suffix = ChatComponent.LEGACY_CODEC.decode(input);
			flags = input.readByte();
			readVisibility(input);
			color = input.readByte();
		}
		@Override void writeParameters(ByteBuf output) {
			ChatComponent.LEGACY_CODEC.encode(output, display);
			ChatComponent.LEGACY_CODEC.encode(output, prefix);
			ChatComponent.LEGACY_CODEC.encode(output, suffix);
			output.writeByte(flags);
			writeVisibility(output);
			output.writeByte(color);
		}
		@Override int resetColor() { return -1; }
		@Override public boolean appliesTo(int protocol) { return protocol >= 47 && protocol < 107; }
	}

	static final class Protocol107 extends Protocol47 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC = codec(Protocol107::new);
		private String collision = "always";

		Protocol107() {
		}

		@Override void readVisibility(ByteBuf input) {
			super.readVisibility(input);
			collision = ByteBufStreamCodecs.VARINT_STRING.decode(input);
		}
		@Override void writeVisibility(ByteBuf output) {
			super.writeVisibility(output);
			ByteBufStreamCodecs.VARINT_STRING.encode(output, collision);
		}
		@Override public boolean appliesTo(int protocol) { return protocol >= 107 && protocol < 393; }
	}

	static class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC = codec(Protocol393::new);
		private String visibility = "always", collision = "always";

		Protocol393() {
		}

		StreamCodec<ByteBuf, ByteBuf, ChatComponent> components() { return ChatComponent.JSON_CODEC; }
		void readRules(ByteBuf input) {
			visibility = ByteBufStreamCodecs.VARINT_STRING.decode(input);
			collision = ByteBufStreamCodecs.VARINT_STRING.decode(input);
		}
		void writeRules(ByteBuf output) {
			ByteBufStreamCodecs.VARINT_STRING.encode(output, visibility);
			ByteBufStreamCodecs.VARINT_STRING.encode(output, collision);
		}
		@Override void readParameters(ByteBuf input) {
			display = components().decode(input);
			flags = input.readByte();
			readRules(input);
			color = ByteBufStreamCodecs.VARINT.decode(input);
			prefix = components().decode(input);
			suffix = components().decode(input);
		}
		@Override void writeParameters(ByteBuf output) {
			components().encode(output, display);
			output.writeByte(flags);
			writeRules(output);
			ByteBufStreamCodecs.VARINT.encode(output, color);
			components().encode(output, prefix);
			components().encode(output, suffix);
		}
		@Override public boolean appliesTo(int protocol) { return protocol >= 393 && protocol < 765; }
	}

	static class Protocol765 extends Protocol393 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = codec(Protocol765::new);

		Protocol765() {
		}

		@Override StreamCodec<ByteBuf, ByteBuf, ChatComponent> components() { return ChatComponent.NBT_CODEC; }
		@Override public boolean appliesTo(int protocol) { return protocol >= 765 && protocol < 770; }
	}

	static class Protocol770 extends Protocol765 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = codec(Protocol770::new);
		private int visibility, collision;

		Protocol770() {
		}

		@Override void readRules(ByteBuf input) {
			visibility = ByteBufStreamCodecs.VARINT.decode(input);
			collision = ByteBufStreamCodecs.VARINT.decode(input);
		}
		@Override void writeRules(ByteBuf output) {
			ByteBufStreamCodecs.VARINT.encode(output, visibility);
			ByteBufStreamCodecs.VARINT.encode(output, collision);
		}
		@Override public boolean appliesTo(int protocol) { return protocol >= 770 && protocol < 776; }
	}

	static final class Protocol776 extends Protocol770 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol776> STREAM_CODEC = codec(Protocol776::new);

		Protocol776() { color = -1; }

		@Override void readParameters(ByteBuf input) {
			display = components().decode(input);
			prefix = components().decode(input);
			suffix = components().decode(input);
			readRules(input);
			color = input.readBoolean() ? ByteBufStreamCodecs.VARINT.decode(input) : -1;
			flags = input.readByte();
		}
		@Override void writeParameters(ByteBuf output) {
			components().encode(output, display);
			components().encode(output, prefix);
			components().encode(output, suffix);
			writeRules(output);
			output.writeBoolean(color != -1);
			if (color != -1) ByteBufStreamCodecs.VARINT.encode(output, color);
			output.writeByte(flags);
		}
		@Override int resetColor() { return -1; }
		@Override public boolean appliesTo(int protocol) { return protocol >= 776; }
	}
}
