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

package de.jpx3.intave.packet.wire.scoreboard.objective;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardObjectivePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChatComponent;
import de.jpx3.intave.share.ScoreboardNumberFormat;
import de.jpx3.intave.share.ScoreboardObjectiveAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ScoreboardObjectiveWirePacket {
	private ScoreboardObjectiveWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol393.class,
			Protocol765.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableScoreboardObjectivePacket {
		protected String objectiveName;
		protected ScoreboardObjectiveAction action;
		protected String display;
		protected int renderType;

		protected Base() {
			this("", ScoreboardObjectiveAction.CREATE, "\"\"", 0);
		}

		protected Base(
			String objectiveName,
			ScoreboardObjectiveAction action,
			String display,
			int renderType) {
			this.objectiveName = objectiveName;
			this.action = action;
			this.display = display;
			this.renderType = renderType;
		}

		@Override
		public String objectiveName() {
			return this.objectiveName;
		}

		@Override
		public void setObjectiveName(String value) {
			markMutated();
			this.objectiveName = value;
		}

		@Override
		public ScoreboardObjectiveAction action() {
			return this.action;
		}

		@Override
		public void setAction(ScoreboardObjectiveAction value) {
			markMutated();
			this.action = value;
		}

		@Override
		public abstract String displayNameJson();

		@Override
		public abstract void setDisplayNameJson(String value);
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName);
					output.writeByte(packet.action.id());
					if (packet.action != ScoreboardObjectiveAction.REMOVE) {
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.legacyName);
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.legacyRenderType);
					}
				},
				input -> {
					String legacyName = "";
					String legacyRenderType = "integer";

					String objectiveName = ByteBufStreamCodecs.VARINT_STRING.decode(input);
					ScoreboardObjectiveAction action =
						ScoreboardObjectiveAction.byId(input.readUnsignedByte());
					if (action != ScoreboardObjectiveAction.REMOVE) {
						legacyName = ByteBufStreamCodecs.VARINT_STRING.decode(input);
						legacyRenderType = ByteBufStreamCodecs.VARINT_STRING.decode(input);
					}
					return new Protocol47(
						objectiveName, action, legacyName, legacyRenderType);
				});

		private String legacyName = "", legacyRenderType = "integer";

		public Protocol47() {
			super();
		}

		public Protocol47(
			String objectiveName,
			ScoreboardObjectiveAction action,
			String legacyName,
			String legacyRenderType) {
			super(objectiveName, action, "\"\"", 0);
			this.legacyName = legacyName;
			this.legacyRenderType = legacyRenderType;
		}

		@Override
		public String displayNameJson() {
			return action == ScoreboardObjectiveAction.REMOVE
				? null
				: new com.google.gson.JsonPrimitive(legacyName).toString();
		}

		@Override
		public String displayNameText() {
			return action == ScoreboardObjectiveAction.REMOVE ? null : legacyName;
		}

		@Override
		public void setDisplayNameJson(String value) {
			markMutated();
			legacyName = ChatComponent.json(value).legacyText();
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
					ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName);
					output.writeByte(packet.action.id());
					if (packet.action != ScoreboardObjectiveAction.REMOVE) {
						ByteBufStreamCodecs.stringCodec(262144).encode(output, packet.display);
						ByteBufStreamCodecs.VARINT.encode(output, packet.renderType);
					}
				},
				input -> {
					String display = "\"\"";
					int renderType = 0;

					String objectiveName = ByteBufStreamCodecs.VARINT_STRING.decode(input);
					ScoreboardObjectiveAction action =
						ScoreboardObjectiveAction.byId(input.readUnsignedByte());
					if (action != ScoreboardObjectiveAction.REMOVE) {
						display = ByteBufStreamCodecs.stringCodec(262144).decode(input);
						renderType = ByteBufStreamCodecs.VARINT.decode(input);
					}
					return new Protocol393(
						objectiveName, action, display, renderType);
				});

		public Protocol393() {
			super();
		}

		public Protocol393(
			String objectiveName,
			ScoreboardObjectiveAction action,
			String display,
			int renderType) {
			super(objectiveName, action, display, renderType);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393 && protocolVersion < 765;
		}

		@Override
		public String displayNameJson() {
			return action == ScoreboardObjectiveAction.REMOVE ? null : display;
		}

		@Override
		public void setDisplayNameJson(String value) {
			markMutated();
			display = Objects.requireNonNull(value);
		}
	}

	static final class Protocol765 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = StreamCodec.of(
			(output, packet) -> {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName);
				output.writeByte(packet.action.id());
				if (packet.action != ScoreboardObjectiveAction.REMOVE) {
					ChatComponent.NBT_CODEC.encode(output, packet.title);
					ByteBufStreamCodecs.VARINT.encode(output, packet.renderType);
					ScoreboardNumberFormat.OPTIONAL_CODEC.encode(output, packet.numberFormat);
				}
			}, input -> {
				Protocol765 packet = new Protocol765();
				packet.objectiveName = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				packet.action = ScoreboardObjectiveAction.byId(input.readUnsignedByte());
				if (packet.action != ScoreboardObjectiveAction.REMOVE) {
					packet.title = ChatComponent.NBT_CODEC.decode(input);
					packet.renderType = ByteBufStreamCodecs.VARINT.decode(input);
					packet.numberFormat = ScoreboardNumberFormat.OPTIONAL_CODEC.decode(input);
				}
				return packet;
			});

		private ChatComponent title = ChatComponent.legacy("");
		private ScoreboardNumberFormat numberFormat;

		Protocol765() {
		}

		@Override
		public String displayNameJson() {
			return action == ScoreboardObjectiveAction.REMOVE ? null : title.json();
		}

		@Override
		public void setDisplayNameJson(String value) {
			markMutated();
			title = ChatComponent.json(value);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 765;
		}
	}
}
