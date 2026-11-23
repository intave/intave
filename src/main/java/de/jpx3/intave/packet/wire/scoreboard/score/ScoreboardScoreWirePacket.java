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

package de.jpx3.intave.packet.wire.scoreboard.score;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardScorePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChatComponent;
import de.jpx3.intave.share.ScoreboardNumberFormat;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ScoreboardScoreWirePacket {
	private ScoreboardScoreWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(Protocol47.class, Protocol765.class));
	}

	static abstract class Base extends AbstractMutable implements MutableScoreboardScorePacket {
		protected String owner = "", objective = "";
		protected int value;

		@Override public String owner() { return owner; }
		@Override public String objectiveName() { return objective; }
		@Override public Integer value() { return removed() ? null : value; }
		@Override public void setOwner(String value) { markMutated(); owner = Objects.requireNonNull(value); }
		@Override public void setObjectiveName(String value) { markMutated(); objective = value; }
		@Override public void setValue(int value) { markMutated(); this.value = value; }
		@Override public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Server.SCOREBOARD_SCORE;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = StreamCodec.of(
			(output, packet) -> {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.owner);
				ByteBufStreamCodecs.VARINT.encode(output, packet.removed ? 1 : 0);
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objective == null ? "" : packet.objective);
				if (!packet.removed) ByteBufStreamCodecs.VARINT.encode(output, packet.value);
			}, input -> {
				Protocol47 packet = new Protocol47();
				packet.owner = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				int action = ByteBufStreamCodecs.VARINT.decode(input);
				if (action != 0 && action != 1) throw new IllegalArgumentException("Unknown score action " + action);
				packet.removed = action == 1;
				packet.objective = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				if (packet.removed && packet.objective.isEmpty()) packet.objective = null;
				if (!packet.removed) packet.value = ByteBufStreamCodecs.VARINT.decode(input);
				return packet;
			});

		private boolean removed;

		Protocol47() {
		}

		@Override public boolean removed() { return removed; }
		@Override public void setRemoved(boolean value) { markMutated(); removed = value; }
		@Override public String displayNameJson() { return null; }
		@Override public void setDisplayNameJson(String value) {
			if (value != null) throw new UnsupportedOperationException("Legacy scores have no display name");
		}
		@Override public boolean appliesTo(int protocol) { return protocol >= 47 && protocol < 765; }
	}

	static final class Protocol765 extends Base {
		private static final StreamCodec<ByteBuf, ByteBuf, ChatComponent> DISPLAY =
			ByteBufStreamCodecs.optionalCodecOf(ChatComponent.NBT_CODEC);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = StreamCodec.of(
			(output, packet) -> {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.owner);
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objective);
				ByteBufStreamCodecs.VARINT.encode(output, packet.value);
				DISPLAY.encode(output, packet.display);
				ScoreboardNumberFormat.OPTIONAL_CODEC.encode(output, packet.numberFormat);
			}, input -> {
				Protocol765 packet = new Protocol765();
				packet.owner = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				packet.objective = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				packet.value = ByteBufStreamCodecs.VARINT.decode(input);
				packet.display = DISPLAY.decode(input);
				packet.numberFormat = ScoreboardNumberFormat.OPTIONAL_CODEC.decode(input);
				return packet;
			});

		private ChatComponent display;
		private ScoreboardNumberFormat numberFormat;

		Protocol765() {
		}

		@Override public boolean removed() { return false; }
		@Override public void setRemoved(boolean value) {
			if (value) throw new UnsupportedOperationException("Use RESET_SCORE to remove modern scores");
		}
		@Override public void setObjectiveName(String value) { super.setObjectiveName(Objects.requireNonNull(value)); }
		@Override public String displayNameJson() { return display == null ? null : display.json(); }
		@Override public void setDisplayNameJson(String value) {
			markMutated();
			display = value == null ? null : ChatComponent.json(value);
		}
		@Override public boolean appliesTo(int protocol) { return protocol >= 765; }
	}
}
