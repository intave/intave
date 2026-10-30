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
import de.jpx3.intave.share.ScoreboardObjectiveAction;
import io.netty.buffer.ByteBuf;

public final class ScoreboardObjectivePlainTextWirePacket extends ScoreboardObjectiveWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ScoreboardObjectivePlainTextWirePacket>
			STREAM_CODEC =
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
								return new ScoreboardObjectivePlainTextWirePacket(
										objectiveName, action, legacyName, legacyRenderType);
							});

	private String legacyName = "", legacyRenderType = "integer";

	public ScoreboardObjectivePlainTextWirePacket() {
		super();
	}

	public ScoreboardObjectivePlainTextWirePacket(
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
	public void setDisplayNameJson(String value) {
		legacyName = new com.google.gson.JsonParser().parse(value).getAsString();
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 393;
	}
}
